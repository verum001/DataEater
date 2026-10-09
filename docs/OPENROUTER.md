# OpenRouter online AI

Version 1.3.0 adds optional online answers. Local AI remains the default;
OpenRouter is activated explicitly in Settings and is labelled Online in chat.

## Setup

1. Open Settings → AI connection → Use OpenRouter.
2. Paste your OpenRouter API key. Get an API key opens the account page.
3. Choose a model. Free models are listed first; paid models use account credits.
4. Decide whether to allow selected database excerpts. This is off by default.
5. Review the disclosure and tap Use online AI.

The saved key is encrypted with AES-GCM using a non-exportable Android Keystore
key and is excluded from Android backup with other private preferences. Leave the
key field blank to keep it; Remove saved key clears the configuration and stored
secret. Keys are never written to app logs or request bodies.

## Data and memory

Online requests send the current question, instructions and bounded recent online
messages. With database sharing enabled, locally retrieved passages and their
references are included. Local conversations and unscoped legacy messages are
excluded from online memory. No whole database or original PDF is uploaded.

The online provider receives plaintext excerpts when that option is enabled,
including excerpts from an unlocked encrypted database. Database encryption does
not protect text after sending it to a provider. Use on-device AI for private
content that cannot be shared. OpenRouter and the selected provider have their
own terms and privacy policies.

Requests use OpenRouter's `data_collection: deny` provider filter. This narrows
routing; it does not make online inference local or guarantee every service's
retention behavior. A route may be unavailable with this filter.

Answers stream into chat. Stop disconnects the request. Whether provider billing
stops depends on that provider; no automatic retries or paid fallback requests are
made. Connectivity, rejected keys, missing credits, request limits and incomplete
streams produce recovery messages. Local matching still runs before a database
request; no matching passages means no online completion request.

Reply length sets the online output budget: Short 2,048 tokens, Normal 3,072,
and Detailed 4,096. These are limits, not promised word counts. The budget leaves room for internal reasoning; a low cap can otherwise
produce no visible reply even when Short was requested. A truncated
or failed response remains an incomplete turn and is excluded from future memory.
Online mode is off after restarting; settings remain saved. Use On device to return
to local AI, then choose a local model.

Sources: [chat API](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request),
[streaming and cancellation](https://openrouter.ai/docs/api_reference/streaming),
[model list](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties),
[provider data policies](https://openrouter.ai/docs/guides/routing/provider-selection).

## Reply controls and model switching

Settings → Replies contains two independent controls. Strict is the default and
asks for only facts stated in the retrieved passages. Balanced allows explanations
and labelled deductions supported by those passages. Flexible may add general
background under “General knowledge (not in the database)”; a notice remains with
the saved answer. None of the levels permits inventing absent technical values,
procedures or citations. Models can still ignore instructions: verify important
answers against the source. If search finds no passages, no database request is
sent, even in Flexible mode. Switch Database off for general questions.

Short requests one or two sentences or a short list. Normal gives a clear answer
with several sentences where useful. Detailed asks for fuller supported explanation.
Local models receive the same style instructions; actual length depends on the model.
Essential warnings must be kept at every length. Preferences survive app restart.
Changing strictness separates model memory between levels so relaxed answers are not
replayed as strict evidence. Changing length keeps the conversation context.

While online AI is active, tap the model name below the message box to choose an
online model. The picker opens with free models only; search or turn that filter off
to see paid models. A paid selection shows a credit-use confirmation before switching.
On device restores the local-model picker.
