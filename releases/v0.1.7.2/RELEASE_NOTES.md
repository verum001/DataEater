# DataEater 0.1.7.2 — Local model benchmarks

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
