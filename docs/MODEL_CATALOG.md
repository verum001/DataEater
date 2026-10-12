# Download models

Open Settings, expand Model Selection with Change Model, then tap Download models.
Recommended shows three curated files from the limited phone document comparison:
Gemma 4 E2B, Qwen2.5 1.5B Instruct and Qwen3 4B Instruct 2507. Recommendations
are tied to the exact reviewed file and byte count. A new conversion or changed
file does not inherit the recommendation. Known reviewed versions remain listed
when updates cannot be checked; refreshing metadata never silently replaces them
with an untested file.

Switch to All to browse current public Hugging Face .litertlm files alongside the
reviewed options. Search by model or publisher, or tap Refresh. Discovery checks
a bounded set of recent repositories with published SHA-256 and size; narrow
the name for more specific results. Gated files requiring an account or agreement
are excluded. Partial catalogue checks remain visible.

Show description expands each model's details. Publisher text is fetched on
demand from README.md at the listed repository revision, displayed as a bounded
plain-text excerpt and labelled From Hugging Face. Code, images and hidden
metadata are omitted. A missing description has a retry action; the model can
still be downloaded. Publisher descriptions are not DataEater test results.

The default memory filter estimates fit from the phone's RAM. It does not certify
runtime compatibility or technical answer accuracy. Turn it off to see larger
matches. File size, update date, licence and exact-file test status remain visible.
Downloads check available storage before starting. An existing transfer stays
accessible even when the catalogue mode changes.

Each download pins the repository commit, file path, byte count and SHA-256.
Verification and atomic installation precede discovery in /sdcard/DataEater.
Transfers and their exact metadata survive reopening. Updated or different
files use distinct local filenames and never overwrite an existing model.
Only catalogue metadata and requested model bytes are sent over the network;
chats, documents and keys remain local. Downloading does not execute repository
code. Failed model loads remain visible and do not silently switch to another
model. Known bundles keep their tested runtime profiles; new files use bounded
conservative settings and remain untested until evaluated.

API reference: [Hugging Face Hub endpoints](https://huggingface.co/docs/hub/api).

## Historical tests — 7 October 2026



DataEater 1.2.0 uses LiteRT-LM 0.17.1. Downloads pin a repository revision,
exact byte count and SHA-256, and verify the complete file before installation.
The earlier fixed catalogue offered these four exact bundles, tested on one phone:

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
of compatibility or technical accuracy. See [research results](SMALL_MODEL_RESEARCH.md).

Nemotron 4B produced empty answers in repeated conversations. LFM 1.2B and tested
sub-billion conversions failed missing-value, negation or follow-up checks. Those exact bundles were excluded from the earlier fixed catalogue; existing owner files remain available for manual use.
Their file-specific profiles remain, including CPU-only Nemotron and Qwen3.5 0.8B.
LFM 2.6B, Phi-4 Mini, Qwen3.5 4B and Gemma E4B were untested in that comparison. GGUF-only LFM 8B-A1B and Granite H Tiny are removed from the picker because
the current engine cannot load them. Gated downloads are excluded from the simple
account-free flow.

Database downloads now have a separate release catalogue; see DATABASE_DOWNLOADS.md.
