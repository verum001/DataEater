# DataEater 1.4.1

Local models handle long conversations and database references more reliably.

- Qwen 4B uses a smaller regular input budget.
- When a local model reports that its input is too long, retry with less chat history and fewer complete passages.
- Keep references for the passages actually supplied to the model.
- Explain when the highest-ranked passage cannot fit, so the question or source can be shortened or a larger model used.

Saved chats remain intact. The Android database builder, optional online AI and other earlier features remain available. No OCR is included.

## Verification and downloads

Archived checks report 283 unit tests passing, no release lint errors, and actual Qwen 4B overflow recovery on vivo V2453A / Android 16 using invented facts. Other devices and PDF layouts were not covered by that run.

Install `DataEater-1.4.1.apk`. The existing release signing certificate is retained. `SHA256SUMS` verifies the APK and accompanying `DataEater-1.4.1-notices.zip`.
