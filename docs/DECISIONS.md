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
