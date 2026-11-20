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

# Point the agents at the model inside this container. Nothing leaves the server.
export AI_BASE_URL="http://127.0.0.1:11434/v1"
export AI_API_KEY="${AI_API_KEY:-local}"
export AI_TIMEOUT="${AI_TIMEOUT:-900}"

python3 -m agents "${1:-run}"
STATUS=$?

kill "$OLLAMA_PID" 2>/dev/null
exit $STATUS
