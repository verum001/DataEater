# Tested model catalog — 7 October 2026

DataEater 1.2.0 uses LiteRT-LM 0.17.1. Downloads pin a repository revision,
exact byte count and SHA-256, and verify the complete file before installation.
The simple picker now offers these four bundles, tested on the owner's phone:

| Model | Download size, decimal GB | Source |
|---|---:|---|
| Gemma 4 E2B | 2.008 | [LiteRT bundle](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Qwen3 1.7B | 0.977 | [LiteRT bundle](https://huggingface.co/litert-community/Qwen3-1.7B) |
| Qwen2.5 1.5B Instruct | 1.598 | [LiteRT bundle](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct) |
| Qwen3 4B Instruct 2507 | 2.659 | [LiteRT bundle](https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507) |

Gemma E2B and Qwen 1.5B gave the strongest results in this limited comparison.
Qwen 1.7B is the smallest download; the UI describes it as suitable for simpler
questions. All four answered both final FAA questions and recovered after
cancellation. This is evidence from one phone and a small sample, not a guarantee
of compatibility or technical accuracy. See [research results](benchmarks.md).

Nemotron 4B produced empty answers in repeated conversations. LFM 1.2B and tested
sub-billion conversions failed missing-value, negation or follow-up checks. They
are excluded from downloads; existing owner files remain available for manual use.
Their file-specific profiles remain, including CPU-only Nemotron and Qwen3.5 0.8B.
Untested LFM 2.6B, Phi-4 Mini, Qwen3.5 4B and Gemma E4B are deferred, not declared
broken. GGUF-only LFM 8B-A1B and Granite H Tiny are removed from the picker because
the current engine cannot load them. Gated downloads are excluded from the simple
account-free flow.

Database downloads remain planned until the owner publishes the database catalog.
The same simple selection, progress and retry interface should be reused then.
