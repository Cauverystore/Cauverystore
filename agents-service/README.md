# Cauvery Store agents

A small app that runs **separately from the store**, with **its own AI brain**. An open model
runs inside the app's own container, so no outside AI company or API key is involved, and
nothing is sent anywhere except the store. Its agents only **prepare drafts**. A person approves
everything in Cauvery Admin.

## The brain

- **Model:** Qwen3 4B Instruct, an open model under the Apache 2.0 licence, so commercial use is
  allowed. It runs through Ollama (pinned at version 0.40.13), and is baked into the image by
  `Dockerfile.ownbrain`.
- **How a run works:** each scheduled run starts the model, the agents do their work, and the
  container stops. The server is only busy, and only paid for, while it is working.
- **Speed:** it runs on an ordinary server processor (CPU). Expect roughly one to a few minutes
  per product. A run stops starting new products after 45 minutes (`AGENT_MAX_MINUTES`).
- **Memory:** about 4–5 GB while running.
- **Rented AI instead (optional):** you can switch to a rented AI service later.
  `Dockerfile.hosted` together with `AI_BASE_URL`, `AI_API_KEY` and `AI_MODEL` works with any
  OpenAI-compatible provider.

## Listing Agent

1. In Admin, go to **Agent Drafts**. Paste DeoDap or Baapstore product links and click
   **Queue for the agent**.
2. The agent runs on a schedule: 6:00, 12:00 and 18:00 India time, set in `railway.json`. Each
   run, it:
   - takes queued links one at a time,
   - reads each product's live data from the supplier and skips anything out of stock,
   - writes a fresh listing and suggests a category and HSN code,
   - sends the draft back to the store.
3. The store checks each draft. The HSN code must be a real code and the category must exist.
   The store also sets the price with its own rule: cost × 2, rounded up to end in 9.
4. In **Agent Drafts → Ready to review**, click **Open in Add Product**. Check the form, add the
   MRP and photos, and press **Create**.

The agent can only use `/api/agent/**` on the store, protected by `AGENTS_TOKEN`. That lets it
take queued links, read the category list and send back drafts. It cannot create, change or
publish products.

## Settings (Railway variables)

| Variable | Needed | What |
|---|---|---|
| `STORE_API_URL` | yes | the store backend address |
| `AGENTS_TOKEN` | yes | the same secret as `AGENTS_TOKEN` on the store backend |
| `AGENT_BATCH_SIZE` | no | most products per run (default 10) |
| `AGENT_MAX_MINUTES` | no | stop starting new products after this long (default 45) |
| `AI_TIMEOUT` | no | seconds to wait for one answer (default 900 with the own brain) |

## Commands

```
python -m unittest discover -s tests -t .   # tests (no model needed)
python -m agents check    # are the store and the AI reachable?
python -m agents run      # one run now
```

## Deploying on Railway

Add a new service from this GitHub repo with **Root Directory** `agents-service`. Railway reads
`railway.json`, which builds the own-brain image and sets the schedule. Then set the variables
above. Give the service at least 8 GB of memory. On the store's backend service, set
`AGENTS_TOKEN` to the same secret.
