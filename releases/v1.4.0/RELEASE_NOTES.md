# DataEater 1.4.0

Create knowledge databases directly on Android through **Settings → Create database**.

- Build from selectable-text PDFs, text and Markdown, including folders.
- Review extracted text and rebuild with original page references.
- Check databases, create protected copies, manage signing keys and issue access codes.
- Save through Android’s file picker or add the result to DataEater.
- Offline help, visible progress and cancellation.

Publisher keys are encrypted in private app storage. Export secure key backups before uninstalling or resetting the phone. Linux builder databases, keys and review packages are compatible.

No OCR is included. Diagrams are not reconstructed, and complex reading order or tables may need review. Limits are 12 MiB of extracted source text per collection and 256 MiB per input file.

Android 8.0+ and a supported 64-bit device are required. Encrypted database unlocking requires Android 13+. Building databases needs no AI model or network connection. Optional OpenRouter AI and reply settings from 1.3 remain available.

## Verification and downloads

Archived checks on vivo V2453A / Android 16 report 277 unit tests passing, three core phone tests, one UI test and no release lint errors. Tests used synthetic documents. This does not establish support for every Android version or document provider.

Install `DataEater-1.4.0.apk`. The existing release signing certificate is retained. `SHA256SUMS` verifies the APK and accompanying `DataEater-1.4.0-notices.zip`. The notices archive includes PDFBox, Bouncy Castle and existing runtime notices. No models, private source or user documents are bundled in the release attachments.
