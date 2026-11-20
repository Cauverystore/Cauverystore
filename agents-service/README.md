# Cauvery Store agents

A small app that runs **separately from the store**. Its AI can come from any provider, and it
never uses Claude. Its agents only **prepare drafts**. A person approves everything in Cauvery
Admin.

## Listing Agent

1. In Admin, go to **Agent Drafts**. Paste DeoDap or Baapstore product links and click **Queue for the agent**.
2. The agent runs on a schedule: 6:00, 12:00 and 18:00 India time, set in `railway.json`. Each run, it:
   - reads each product's live data from the supplier and skips anything out of stock,
   - writes a fresh title, description, specs and SEO text, and suggests a category and HSN code,
   - sends the draft back to the store.
3. The store checks each draft. The HSN code must be a real code, and the category must exist.
   The store also sets the price with its own rule: cost × 2, rounded up to end in 9.
4. In **Agent Drafts → Ready to review**, click **Open in Add Product**. Check the form, add the MRP and photos, and press **Create**.

The agent can only use `/api/agent/**` on the store, protected by `AGENTS_TOKEN`. That lets it
take queued links, read the category list and send back drafts. It cannot create, change or
publish products.

## Settings

See `.env.example`. You need the store address, the shared `AGENTS_TOKEN`, and the AI provider's
`AI_BASE_URL`, `AI_API_KEY` and `AI_MODEL`. To change the AI provider, change these three
settings. No code changes are needed.

## Commands

```
python -m agents check    # are the store and the AI reachable?
python -m agents run      # one run now
python -m unittest discover -s tests -t .   # tests (no AI key needed)
```

## Deploying on Railway

Add a new service from this GitHub repo with **Root Directory** `agents-service`. Railway reads
`railway.json`, which sets up a Dockerfile build and the cron schedule. Then set the variables.
On the store's backend service, set `AGENTS_TOKEN` to the same secret.
