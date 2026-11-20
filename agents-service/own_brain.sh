#!/bin/sh
# Starts the built-in model, runs the agents once, then stops. Used by Dockerfile.ownbrain.
set -u

# First ask the store whether anything is queued. Starting the model costs a minute or two of
# server time, so on a quiet day the run ends here and costs almost nothing.
python3 -m agents pending
case $? in
  0) ;;                                                      # work waiting: carry on
  3) echo "Nothing queued, so the AI model was not started."; exit 0 ;;
  *) echo "Could not reach the store, so the AI model was not started."; exit 1 ;;
esac

ollama serve > /tmp/ollama.log 2>&1 &
OLLAMA_PID=$!

i=0
until curl -sf http://127.0.0.1:11434/api/tags > /dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 120 ]; then
    echo "The built-in AI model did not start. Last lines of its log:"
    tail -20 /tmp/ollama.log
    kill "$OLLAMA_PID" 2>/dev/null
    exit 1
  fi
  sleep 1
done
echo "Built-in AI model is ready ($AI_MODEL)."

# Speed: inside a container the model sees every processor of Railway's big host machine and starts
# that many workers, although only a small share is ours; the workers then choke each other and a
# one-minute answer takes over 15 minutes. So count the processors we are really allowed (the
# container's CPU quota) and tell the model to use exactly that many. AI_THREADS overrides it.
quota_threads() {
  if [ -r /sys/fs/cgroup/cpu.max ]; then                        # cgroup v2: "<quota> <period>" or "max ..."
    read -r q p < /sys/fs/cgroup/cpu.max
    [ "$q" != "max" ] && [ "$p" -gt 0 ] && echo $(( (q + p - 1) / p )) && return
  fi
  if [ -r /sys/fs/cgroup/cpu/cpu.cfs_quota_us ]; then           # cgroup v1
    q=$(cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us); p=$(cat /sys/fs/cgroup/cpu/cpu.cfs_period_us)
    [ "$q" -gt 0 ] && [ "$p" -gt 0 ] && echo $(( (q + p - 1) / p )) && return
  fi
  nproc
}
THREADS="${AI_THREADS:-$(quota_threads)}"
[ "$THREADS" -gt 0 ] 2>/dev/null || THREADS=2
[ "$THREADS" -gt 16 ] && THREADS=16
echo "Processors: host shows $(nproc), using $THREADS for the AI model."
printf 'FROM %s\nPARAMETER num_thread %s\n' "$AI_MODEL" "$THREADS" > /tmp/Modelfile
if ollama create store-brain -f /tmp/Modelfile > /tmp/create.log 2>&1; then
  export AI_MODEL=store-brain
else
  echo "Could not set the processor count, carrying on with defaults:"; tail -5 /tmp/create.log
fi

# Point the agents at the model inside this container. Nothing leaves the server.
export AI_BASE_URL="http://127.0.0.1:11434/v1"
export AI_API_KEY="${AI_API_KEY:-local}"
export AI_TIMEOUT="${AI_TIMEOUT:-900}"

python3 -m agents "${1:-run}"
STATUS=$?

kill "$OLLAMA_PID" 2>/dev/null
exit $STATUS
