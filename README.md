<p align="center">
  <img src="assets/dataeater-banner.svg" alt="DataEater — Offline AI knowledge engine" width="100%">
</p>

<p align="center">
  <strong>Ask your documents. Get answers on your phone.</strong><br>
  Your data. Your device. Your AI.
</p>

<p align="center">
  <a href="https://github.com/verum001/DataEater/releases/download/v1.2/DataEater-1.2.0.apk">Download Android app</a> ·
  <a href="https://github.com/verum001/DataEater/releases/download/v1.2/FAA_Aviation_Maintenance_General_and_Powerplant_2023_optimized.dataeater">Download test database</a> ·
  <a href="docs/supported-devices.md">Supported devices</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

**DataEater is an Android app that answers questions using your document databases and AI running on your phone.** Once a model and database are downloaded, you can ask questions without an internet connection. Your questions and document content stay on your device.

**One app, many knowledge databases.** Use it with technical manuals, electronics references, company documentation, books or other document collections prepared for DataEater.

## Get started

1. **Install the app.** Download the APK above, open it and allow installation when Android asks.
2. **Download AI.** Open DataEater, allow storage access, then tap **Download AI**. Choose a model, wait for the download and tap **Use model**.
3. **Add a database.** Download the `.dataeater` file above. Using your phone’s file manager, move it into the **DataEater** folder in internal storage (`/sdcard/DataEater/`).
4. **Select it.** Open **Settings → database selection**, tap **Scan again** and choose the database.
5. **Ask a question.** Turn **Database** on to use the selected documents. Turn it off for general AI chat.

Database files are downloaded through your browser and added manually in this preview.

**Requirements:** a supported 64-bit Android 8.0+ device, enough storage and memory for your chosen model. Encrypted databases require Android 12+. See [supported devices](docs/supported-devices.md).

## Recommended models

Choose a model in **Settings → Model Selection → Get models**. Download once;
then use it offline.

| Model | Download size | Why choose it |
|---|---:|---|
| **Gemma 4 E2B** | 2.0 GB | Recommended starting point: fast answers and strong document results in the phone tests. |
| **Qwen2.5 1.5B Instruct** | 1.6 GB | Smaller alternative that answered supplied facts and follow-up questions well. |
| **Qwen3 1.7B** | 977 MB | Smallest download; best for simpler questions. Its final tests passed, but earlier answers were less consistent. |
| **Qwen3 4B Instruct 2507** | 2.7 GB | Larger alternative with good document results; slower on the tested phone. |

These exact bundles were compared on a **vivo V2453A running Android 16**.
Other phones may perform differently. Small models can still invent answers;
check important information against the document. See [phone benchmarks](docs/benchmarks.md).

## Try the aviation database

The test database combines the FAA’s **2023 General and Powerplant Aviation Maintenance Technician Handbooks**. It contains text and PDF page references; diagram images are not included. It is an unofficial conversion, not an FAA-endorsed product.

Example questions:

- What can cause an aircraft engine to run rough at idle?
- What are the possible causes of low oil pressure?
- What should I check if an engine is overheating?
- What can cause excessive oil consumption?
- What could cause low compression in a cylinder?

These are questions to try, not guaranteed answers. **Check the original handbook and the applicable manufacturer’s instructions before performing maintenance.**

## See it in action

| Ask | Read the answer | Choose a database |
|---|---|---|
| <img src="screenshots/search.png" alt="Question entered in DataEater" width="240"> | <img src="screenshots/answer.png" alt="Answer from the selected database" width="240"> | <img src="screenshots/database.png" alt="Database selected in Settings" width="240"> |

[View source references](screenshots/sources.png) · [See the demo](demo/example-workflow.md)

These screenshots use a separate demo with invented equipment information. They do not show the FAA database or private user data.

## How it works

```text
Your question → Relevant database text → Local AI → Answer + source references
```

DataEater finds relevant text in the selected database and gives it to the local model to answer your question. References can identify the document, section and page when that information is available. If no matching information is found, the app tells you.

## Features

- Offline AI chat and questions about documents.
- Source references for checking answers.
- AI model downloads with progress and cancellation.
- Model and database selection and deletion.
- Saved conversations, chat memory and Android text selection and copy.
- Encrypted databases with creator-issued access codes.
- A sliding conversation panel, Help and adaptable layouts.

## Privacy and accuracy

AI and document searches run locally. Optional model downloads connect to Hugging Face; those requests do not include your questions or database text. No account, analytics or telemetry service is required. Private app data is excluded from Android system backup and device migration.

**AI can make mistakes.** Always check important answers against their sources. General chat uses the model’s own knowledge and is not checked against a database.

## Project and license

**Current preview: v1.2.0.** DataEater is under active development. This repository contains documentation, screenshots and app releases. The implementation source remains private. See [PROVENANCE.md](PROVENANCE.md) for release details and verification.

Copyright © 2026 **Jack**. DataEater original software and public documentation are licensed under [Apache 2.0](LICENSE). See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Models, source documents and databases retain their own rights and terms; the app’s Apache license does not cover their content. Only share material you have permission to distribute.
