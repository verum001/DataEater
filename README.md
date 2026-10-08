<p align="center">
  <img src="assets/dataeater2.jpg" alt="DataEater — Feed your eater" width="100%">
</p>

<p align="center">
  <strong>Ask your documents. Get answers on your phone.</strong><br>
</p>

<p align="center">
  <a href="https://github.com/verum001/DataEater/releases/latest">Download Android app</a> ·
  <a href="https://github.com/verum001/DataEater/releases/download/v1.2/FAA_Aviation_Maintenance_General_and_Powerplant_2023_optimized.dataeater">Download test database</a> ·
  <a href="docs/supported-devices.md">Supported devices</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

**DataEater is an Android app that answers questions using your document databases.** Use an AI model on your phone for offline answers, or choose optional online AI through OpenRouter. In local mode, your questions and document content stay on your device. Online mode sends selected messages through OpenRouter to the chosen provider; database excerpts are included only when document sharing is enabled.

**One app, many knowledge databases.** Use it with technical manuals, electronics references, company documentation, books or other document collections prepared for DataEater.

## Get started

1. **Install the app.** Download the APK above, open it and allow installation when Android asks.
2. **Download AI.** Open DataEater, allow storage access, then tap **Download AI**. Choose a model, wait for the download and tap **Use model**.
3. **Add a database.** Download the `.dataeater` file above. Using your phone’s file manager, move it into the **DataEater** folder in internal storage (`/sdcard/DataEater/`). Version 1.4.0 and newer can also create databases through **Settings → Create database**.
4. **Select it.** Open **Settings → database selection**, tap **Scan again** and choose the database.
5. **Ask a question.** Turn **Database** on to use the selected documents. Turn it off for general AI chat.

Database files are downloaded through your browser and added manually in this preview.

**Requirements:** a supported 64-bit Android 8.0+ device, enough storage and memory for your chosen model. Encrypted database unlocking requires Android 13+. See [supported devices](docs/supported-devices.md).

## Online AI and reply settings

Configure OpenRouter in Settings with your own API key, select online mode and choose an online model. Internet access is required. Model availability, usage limits and charges depend on OpenRouter and the provider.

Choose **Strict**, **Balanced** or **Flexible** for database answers and **Short**, **Normal** or **Detailed** for reply length. These controls guide answers; they do not guarantee accuracy or that every sentence is supported by a source.

## Create databases on Android

Version 1.4.0 adds **Settings → Create database**. Import selectable-text PDFs, text or Markdown files, including folders. Review extracted text, preserve original page references, and save a database through Android’s file picker or add it to DataEater. Protected database creation, signing keys and access codes are also available.

No AI model or network connection is needed for database creation. No OCR is included; diagrams are not reconstructed, and complex tables or reading order may need review. Limits are 12 MiB of extracted source text per collection and 256 MiB per input file. Back up publisher keys securely before uninstalling or resetting the phone.

For larger collections, use the separate [DataEater Builder](https://github.com/verum001/DataEater-builder) project.

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
Your question → Relevant database text → Selected AI → Answer + source references
```

DataEater finds relevant text in the selected database and gives it to the selected model to answer your question. Local models process this on the phone; online document answers require permission to share excerpts. References can identify the document, section and page when that information is available. If no matching information is found, the app tells you.

## Features

- Offline AI chat and questions about documents.
- Optional online AI through OpenRouter and configurable reply settings.
- On-device database creation from PDFs, text and Markdown (1.4.0+).
- Recovery from local-model input limits (1.4.1+) and confirmed conversation deletion (1.4.2).
- Source references for checking answers.
- AI model downloads with progress and cancellation.
- Model and database selection and deletion.
- Saved conversations, chat memory and Android text selection and copy.
- Encrypted databases with creator-issued access codes.
- A sliding conversation panel, Help and adaptable layouts.

## Privacy and accuracy

Document search and database creation run locally. Local AI keeps questions and database text on the phone. Optional model downloads connect to Hugging Face; those requests do not include questions or database text. Optional online AI sends selected messages through OpenRouter to the chosen provider, including document excerpts when sharing is enabled. An OpenRouter account and API key are needed only for online mode. Private app data is excluded from Android system backup and device migration.

**AI can make mistakes.** Always check important answers against their sources. General chat uses the model’s own knowledge and is not checked against a database.

## Project and license

**Latest prepared preview: v1.4.2.** See [GitHub Releases](https://github.com/verum001/DataEater/releases) for published downloads and [release notes](releases/README.md) for version details. DataEater is under active development. This repository contains documentation, screenshots and app releases. The implementation source remains private. See [PROVENANCE.md](PROVENANCE.md) for release details and verification.

Copyright © 2026 **Jack**. DataEater original software and public documentation are licensed under [Apache 2.0](LICENSE). See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Models, source documents and databases retain their own rights and terms; the app’s Apache license does not cover their content. Only share material you have permission to distribute.
