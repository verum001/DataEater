# Implementation decisions

## Local processing and explicit online mode

On-device inference, retrieval and database creation run locally. Optional
OpenRouter is a separate, explicitly selected provider. It does not replay local
chat history; document excerpts require a separate sharing opt-in. Online mode
resets to local after restart, while its encrypted key and model choice persist.

## Portable knowledge files

Plain format-v1 databases are ZIP files containing a manifest, source records and
passages. Original page references are retained. Required knowledge-file hashes
are verified before parsing. These hashes detect changes relative to the manifest;
they do not authenticate an unsigned publisher manifest.

## Bounded context and scoped conversation memory

Lexical retrieval selects whole passages. UTF-8-weighted input budgeting accounts
for the question, system instructions, replay and references. Native input-overflow
errors before visible text trigger bounded replanning. Citations are replaced by
the final plan. Relaxed or database-off answers are not replayed as strict evidence.

## Cryptographic interoperability

Protected databases use AES-256-GCM. Access codes use ECDSA P-256 signatures and
ECDH/HKDF wrapping for the selected phone key. Android Keystore holds device keys;
Linux uses the documented compatible format. Fixed cross-language test vectors
catch encoding disagreements. Device binding cannot prevent an authorized reader
from extracting plaintext or patching expiry checks.

## Independent builders

Android uses PDFBox-Android; Linux uses PyMuPDF in a separate project. Both support
extraction review and retain original page markers. Fingerprints and marker checks
validate structure, not factual correctness. Review prompts are product resources
used on selected document batches; there is no automatic document upload.

## Source availability

Original source and documentation are public under Apache-2.0. Third-party code,
model weights and input documents keep their own terms. Production keys, personal
data and unpublished research artifacts are outside the source distribution.

## D-024 — Search across open databases (11 October 2026)

An explicit All unlocked databases selection forms one in-memory search collection for text search and RAG. Source and passage IDs are namespaced by collection position, preserving original files and preventing citation collisions. Stored unlock codes are verified while opening; excluded locked files are named. Unreadable files fail opening with their filename rather than disappearing silently. Collection payload is capped at 64 MiB of passage/navigation UTF-8 and 100,000 passages; this is an input cap, not a guarantee of peak RAM. Refresh replaces the collection.

## D-025 — Database catalogue and simplified creation (11 October 2026)

Database downloads use the existing public database repository's release API rather than a fixed list embedded in the APK. Android DownloadManager persists transfers; verification and atomic publication precede database discovery. The catalogue rejects missing checksums and unexpected origins rather than silently falling back to an older asset.

The simple builder composes existing format-v1 extraction, review and encryption operations. Original documents accompany manual LLM review exports; source files and parsed originals are fingerprinted, and page identities are retained. A local dataSync foreground service supports background preparation without adding a dependency. Publisher secrets remain in the private encrypted vault, separate from review ZIPs. Database-specific content keys link to their encrypted artifact and matching signing key for licence issuance. Key removal affects future issuance, not existing device-bound codes.

## D-026 — Selected manual collections and live model discovery (12 October 2026)

Search in several databases requires an explicit checkbox selection. The list is
saved globally for reopening and with each chat; files outside that selection
are not opened. Missing or locked files are reported. Collection identifiers
include selected file identities so chat memory does not cross different groups.

The model catalogue queries public Hugging Face metadata, pins current commits
and LFS checksums, and distinguishes exact tested files from untested versions.
A bounded recent search and memory estimate aid selection without claiming that
all .litertlm bundles are compatible. Unique versioned filenames preserve old
files; persisted download metadata retains verification across process restarts.
No repository code is fetched or executed. Research target APKs and records stay
frozen separately from these main-project changes.

## D-027 — Curated model mode and publisher descriptions (12 October 2026)

Recommended offers exact reviewed Gemma E2B, Qwen2.5 1.5B and Qwen3 4B files;
All adds recent public verified-file metadata. Recommendation requires repository,
remote filename, byte count and SHA-256 agreement with the reviewed file. Updated
files remain untested until separately checked. Publisher descriptions are lazy,
revision-pinned plain-text excerpts with bounded responses; model cards are data,
not executable content or proof of answer quality. Download models lives within
the expanded Model Selection area. Routine updates increment the final version
component: 0.1.7.1, 0.1.7.2 and onward, alongside Android's version code.


## D-028 — Local model comparisons (12 October 2026)

Start benchmark uses installed local engines sequentially, a captured set of
questions and databases, the existing ChatResponder and bounded-generation path,
and fresh histories. Its transcript shares the main chat components but is stored
separately from ordinary chats. The main chat engine is released before comparison
engines allocate memory. No OpenRouter requests or model downloads are initiated.

Monotonic load, retrieval, first-text and answer clocks distinguish missing
passages, failures and cancellation from successful model output. Atomic JSON
checkpoints and readable text reports persist completed answers and optional
ratings in DataEater/benchmark. Exports use Downloads and a narrowly scoped cache
FileProvider; multiple selections share through Android. Process interruption
is reported on reopening rather than showing a partial run as finished.

No accuracy claim follows from timing or subjective ratings. Cache state, backend
fallback, model-specific limits and selected/fitted evidence are described in
reports. Five-minute generation limits bound an individual answer; native loading
may finish before cancellation releases its resources.
