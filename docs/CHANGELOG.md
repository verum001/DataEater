# Changelog

## 0.1.7.7 — 21 October 2026

Database creation includes offline coverage and review-change reports in Android, with a Save quality report action. Reports identify omitted pages, PDF layout-review needs and changes to restrictions, units, identifiers and numbered steps. Existing page markers, fingerprints and format-v1 reader compatibility are retained. Private reports are encrypted with database source metadata. AI is optional; automatic flags are not technical approval.

DataEater 0.1.7.1 — 12 October 2026

• Download models is under the expanded Model Selection area.
• Recommended offers three reviewed model files; All adds recent public Hugging Face models.
• Show description expands publisher text fetched on demand.
• Database strictness and Reply length selections match the other control colours.
• Help follows the new download route. Routine updates increment the final version component.

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
