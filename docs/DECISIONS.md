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
