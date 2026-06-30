---
id: ai.credentials
title: AI Credentials (Bring Your Own Key)
section: ai
order: 20
---
# AI Credentials

NovelKMS uses a **bring-your-own-key** model. You supply a provider API key (OpenAI first), and reviews run against your own account.

- Keys are **write-only** from the app's perspective: once saved, only a masked hint is ever shown.
- Keys are encrypted at rest.
- Set a default model and manage credentials under **Settings → AI**.

Without a credential, review and generation actions cannot run.
