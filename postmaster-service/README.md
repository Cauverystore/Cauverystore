# Postmaster

The store's stand-alone AI mail agent. It runs as its own small service every 5 minutes, reads new mail sent to support@cauverystore.in, and suggests a reply.

## How it stays safe

- The AI can only **suggest**. The store checks every suggestion and does the sending.
- Before mail leaves the store, emails, phone numbers, long numbers, PIN codes, and common name and address patterns are hidden. This is best effort, so unusual wording can slip through.
- The AI sees only that sender's own orders, the public FAQ and products on sale. It never sees delivery addresses.
- Mail from an unverified sender, mail with attachments and automatic mail never reach the AI.
- Refunds, complaints, cancellations, payment, damaged or missing items and legal mail always wait for a person, with a draft.
- A reply is stopped if it has an outside link or email, a number the store cannot confirm, or a promise of money or a replacement.
- If the AI or this service fails, the mail comes to a person.

## Settings

On this service:

| Variable | What |
|---|---|
| `STORE_API_URL` | the store backend address |
| `POSTMASTER_TOKEN` | a long random secret, same value as on the backend |
| `AI_BASE_URL` | `https://api.anthropic.com` for Claude, or any OpenAI-compatible AI service |
| `AI_API_KEY` | the key from that AI service |
| `AI_MODEL` | the model name, e.g. `claude-haiku-5-5` |

On the store backend:

| Variable | What |
|---|---|
| `POSTMASTER_TOKEN` | the same secret |
| `POSTMASTER_ENABLED` | `true` makes Postmaster handle new mail instead of the old rule-based replier |
| `POSTMASTER_LIVE` | `true` lets safe replies go out. Leave unset to keep everything as a preview |

## Tests

```
python -m unittest discover -s tests -t .
```
