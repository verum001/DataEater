# Changelog

## Source publication — 9 October 2026

- Publish original Android source, tests, build configuration and technical documentation under Apache-2.0.
- Update Linux Builder source to include the 0.4.0 desktop and packaging.
- Add build/contribution guidance and distinguish current source from unchanged older APKs.

## 1.4.2 — 8 October 2026

- Delete conversations with the trash button in the side panel and a confirmation.
- Deleting the current conversation selects another; deleting the last opens a blank conversation.
- Deletion waits until the app is idle. Storage failures keep the conversation visible for retry.

## 1.4.1 — 8 October 2026

- Reduced the regular input budget for Qwen 4B.
- Retry local-model input overflow with less history and fewer complete database passages.
- Retain references for passages actually supplied and explain when a passage cannot fit.

## 1.4.0 — 8 October 2026

- Create databases on Android from selectable-text PDFs, text, Markdown and folders.
- Review extraction and rebuild with original page references.
- Save or import databases, create protected copies, manage signing keys and issue access codes.
- Offline builder help, progress and cancellation; compatible Linux builder workflows.
- No OCR or diagram reconstruction; extracted text and input file limits apply.

## 1.3.1 — 8 October 2026

- Database strictness: Strict, Balanced and Flexible.
- Reply length: Short, Normal and Detailed.
- Online model selection in the composer.

## 1.3.0 — 8 October 2026

- Optional online AI through OpenRouter using a personal API key.
- Local AI remains available offline; online mode shares selected messages and optional document excerpts with external providers.

## 1.2.0 — 7 October 2026

- Chat memory for recent exchanges and short facts within each conversation.
- Better follow-up questions about the selected database.
- Faster document search and improved answer streaming and cancellation.
- Four phone-tested downloads: Gemma E2B, Qwen 1.5B, Qwen 1.7B and Qwen 4B.
- Removed unreliable and untested choices from the simple download list.
- Updated model recommendations and device-specific benchmark results.

Existing chats and databases remain compatible. Model behavior and speed depend
on the device and question; check important answers against their sources.

## 1.1.0 — first public preview prepared, 7 October 2026

- Local Android AI chat and document-backed questions with source references.
- Optional AI downloads, progress, cancellation and verified installation.
- First-use Download AI entry point and expanded model catalog.
- Model and database deletion with confirmation.
- Android message selection and copy.
- Sliding conversation panel, Help, version and changelog.
- Responsive layouts and Android text-size support.
- Encrypted databases with device-bound creator access codes.
- Disabled private app-data cloud backup and migration; removed response-text logging.
- Apache 2.0 licensing selected for original software and public documentation; source was not published with this earlier release.

This earlier release published documentation and the APK before Android source publication. Model downloads and online database downloads are separate features: the latter is not yet implemented.

## Earlier private development — October 2026

Local inference, database questions, conversation management and offline licensing were developed and tested before preparing this public showcase. The original development Git history is preserved privately, not rewritten into a claimed public publication history.
