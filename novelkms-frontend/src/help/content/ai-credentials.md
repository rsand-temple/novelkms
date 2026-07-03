---
id: ai.credentials
title: AI Credentials (Bring Your Own Key)
section: ai
order: 20
---
# AI Credentials (Bring Your Own Key)

NovelKMS uses a **bring-your-own-key** model. You supply a provider API key, and reviews run against your own account — not a shared pool.

Two providers are currently supported:

- **OpenAI** — GPT models. Default: `gpt-5.4`.
- **Anthropic** — Claude models. Default: `claude-sonnet-4-6`.

You can add credentials for either or both providers. When you run a review or generate any AI artifact, NovelKMS uses whichever credential you have set as default for that provider.

- Keys are **write-only** from the app's perspective: once saved, only a masked hint is ever shown.
- Keys are encrypted at rest.
- Manage credentials under **Settings → AI**. You can set a default model per credential.
- The provider of a saved credential cannot be changed — replace it by adding a new credential if needed.

Without a credential, review and generation actions cannot run.
