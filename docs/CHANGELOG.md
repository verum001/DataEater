# Changelog

## DataEater 0.1.7.6 — F-Droid build preparation

- Adds the app description, artwork, five current-interface screenshots and
  versioned release notes used by F-Droid.
- Explains that optional Hugging Face model downloads are separate from the app
  and are not distributed or reviewed by F-Droid.
- Removes the automatic JDK downloader; builds use an installed full JDK.

Android version code 20 follows the earlier GitHub APK (0.1.7.5, code 18) and
the reserved preparation code 19. GitHub remains the upstream repository.
The existing 0.1.7.5 release and tag are unchanged. This update uses the existing
release certificate. Official F-Droid build-server verification and inclusion
remain pending.

## DataEater 0.1.7.5 — Security and database creation fixes

- Fix the delayed crash after selecting PDFs in Create database.
- Retain Android document read permissions for optimisation ZIP preparation. If a previously selected file has lost access, remove it and choose it again.
- Replace internal file-permission errors with a recovery message and clean incomplete extraction copies.
- Tighten database/licence identifiers, bind new unlock codes to encrypted database identity, reject duplicate ZIP/JSON entries, and enforce input limits.
- Validate cryptographic inputs and strengthen private licence storage. Existing valid legacy licences remain supported.

Database file integrity does not independently authenticate a publisher. Offline licence expiry still depends on the device clock; see docs/SECURITY_AUDIT_2026-10-13.md for the audit and remaining limitations. Matching security fixes are included in the DataEater-builder repository (0.4.1).

Validation: 337 Android unit tests passed, signed release build and lint passed with zero errors. Three vivo phone tests passed: repeated PDF selection/cancellation, denied document access recovery, and optimisation ZIP creation/reimport with encryption, licensing and key backup. Python builder regression suites and staged public source checks also passed.

Install DataEater-0.1.7.5.apk as an update. Version code 18 and the existing release certificate preserve update compatibility. Chats and keys remain in place. SHA256SUMS covers the APK and notices archive.


## 0.1.7.2 — 13 October 2026

Open Settings → Start benchmark, below Create database. Add questions, select
installed models and databases, and optionally enable human ratings. Watch the
automatic questions and answers in chat. Each model receives a fresh conversation
for every question; selected databases are searched together.

Reports include phone details, date/time, models, databases, questions, answers,
load/retrieval/first-text/answer times and a comparison table. Add stars and feedback,
reopen saved results, and select files to delete, copy to Downloads or share through
Android. Reports stay in DataEater/benchmark until you export them. Keep the app open
while measuring; completed answers are checkpointed. Timings and ratings do not
establish answer accuracy.

This update also includes the changes since 0.1.6.1:

- Settings at the chat's top right, with database selection and the DB switch below the message box.
- Search in several databases uses explicit checkboxes remembered per conversation.
- Download models is inside expanded Model Selection, with Recommended/All modes,
  live Hugging Face discovery and expandable publisher descriptions.
- Reply preference selections use consistent accent colours.

Validation: 322 automated tests passed, release build succeeded and Android lint
reported no errors. The connected vivo phone completed the benchmark flow with
two installed models and two invented questions against a synthetic database;
rating persistence, two-file downloads, Android sharing and selected deletion
passed. These checks do not cover every phone, Android version or document.

Install DataEater-0.1.7.2.apk as an update to retain existing chats and keys.
Android version code is 15; the established production certificate is retained.
SHA256SUMS verifies the APK and accompanying notices archive. No models, real
manuals, private instructions, research records or keys are included.

APK SHA-256: 89d8977e34cfa96383a0f394f8c38f8fa9c6ab98c9ae5ed8ded9e8866db18d9a


Unreleased — 8 October 2026

• Conversation files retain the previous save if replacement fails.
• Streamed answers save progress in the background about once a second, with a final save on completion or stop. Late callbacks cannot replace the finished answer.
• A failed streaming save displays a storage message while the conversation remains in memory.
• Archive reading limits now apply to skipped entries and total decompression per scan.
• Privacy, architecture and encryption documentation describe optional online AI, unsigned manifests and the current ECDSA licensing protocol.

DataEater 1.4.2 — 8 October 2026

• Delete saved sessions using the trash button beside each session in the side panel.
• Confirm before removing a conversation and its messages. Cancel keeps the session.
• Deleting the current session opens another saved conversation; deleting the last starts a blank session.
• Deletion is unavailable while the app is busy. A storage failure keeps the session and offers a retry.

DataEater 1.4.1 — 8 October 2026

• Qwen 4B and other local models fit more reliably within their input limits.
• If a local model rejects a long question, DataEater retries with less chat history and fewer complete database passages. Citations follow the passages actually used.
• Questions that cannot fit produce a clear message instead of a runtime error.

DataEater 1.4.0 — 8 October 2026

• Create database button at the bottom of Settings.
• Build databases from PDF, text and Markdown files or document folders, entirely on the phone.
• Export text review packages, edit or import reviewed batches and rebuild with original page references.
• Check databases, protect them, create signing keys and issue device-bound access codes.
• Save files through Android’s file picker, or add finished databases directly to DataEater.
• Offline help for every builder task, progress and cancellation.
• Publisher keys are encrypted in private app storage, with explicit backup actions.
• Compatible with Linux builder databases, keys and review packages. No OCR is included.

DataEater 1.3.1 — 8 October 2026

• Database strictness: Strict, Balanced or Flexible. Strict stays within the passages; Balanced explains supported deductions; Flexible may add separately labelled general background.
• Reply length: Short, Normal or Detailed, for local and online AI.
• In online mode, the model picker below the message box shows online models. Free models are shown by default; paid selections require confirmation.
• Flexible answers carry an outside-knowledge notice, saved with the message. Changing strictness separates conversation memory between levels.

DataEater 1.3.0 — 8 October 2026

Optional OpenRouter online AI with model selection and streamed answers.
Encrypted API-key storage and a clear online indicator.
Database sharing requires a separate opt-in; online and local memory stay separate.
Local AI remains available without an internet connection.

DataEater 1.2.0 — 7 October 2026

Chat memory for recent complete turns, including follow-up document questions.
Faster indexed database search and bounded reference text for small models.
Improved streaming and stopping answers.
Reviewed small-model download choices.

DataEater 1.1.0 — 7 October 2026

• Download AI directly from the main screen when no model is installed.
• More models in Get models, with download progress and a Use model button.
• Swipe from the left edge to open conversations.
• Help at the bottom of the side panel, with changelog and version information.
• Layouts adapt to screen size and text size.
• Private app data is excluded from Android system backups.
• Model response text is no longer written to application logs.

DataEater 1.0 — initial version

• Local AI chat and questions about document databases.
• Select and copy message text.
• Manage models and databases in Settings.

## 1.6.0 — Database downloads and creation

Adds a live database download catalogue, simplified creation controls, source-inclusive manual optimisation ZIPs, background preparation, per-database private-key management, key backups/restoration and device licence generation. Original format-v1 files remain compatible.

## 0.1.6.0 — Download repair and version naming

Changes the current version name to 0.1.6.0 while keeping Android update ordering. Database downloads continue after storage permission is granted, and catalogue refreshes cannot invalidate a download tap. Removes More tools from database creation.

## 0.1.6.1 — Database catalogue and Help

Database descriptions start collapsed and can be expanded individually. Download databases is inside the expanded Database Selection section. Help now explains downloads, database creation, manual optimisation and private key backups.

The catalogue is loaded directly from the public database repository and retained when refresh fails. This avoids exhausted unauthenticated GitHub API quotas.
