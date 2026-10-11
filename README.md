<p align="center"><img src="assets/dataeater2.jpg" alt="DataEater — Feed your eater" width="100%"></p>

# DataEater

**Ask your documents. Get answers on your phone.**

DataEater is an open-source Android app for local AI chat and asking AI specific questions using knowledge databases. Answers can include document,
section and page references. Local inference and document processing run on the
phone. Optional OpenRouter chat sends messages to its service; sharing retrieved
document excerpts requires a separate opt-in.

[Download Android app](https://github.com/verum001/DataEater/releases) ·
[Linux database builder](https://github.com/verum001/DataEater-builder) ·
[Changelog](docs/CHANGELOG.md) · [Build from source](docs/DEVELOPMENT.md)

## Get started

1. Install the APK from GitHub Releases. Android may ask to allow installation.
2. Open the app, grant storage access and choose **Download AI**, or configure
   **Settings → AI connection → OpenRouter** with your own API key.
3. Create a database through **Settings → Create database**, or put an existing
   `.dataeater` file in the DataEater folder in internal storage.
4. Select the database and model in Settings. Turn **Database** on to ask about
   the documents, or off for general chat.
5. Check important answers against their cited source. Small models can make mistakes.

A compatible 64-bit ARM64 or x86-64 Android 8.0+ device is required. Encrypted
access-code unlocking requires Android 13+. Storage and memory requirements
vary by model. The interface is currently English. No model or real manual is
bundled. See [supported devices](docs/supported-devices.md).

## Features

- Local AI and optional OpenRouter online AI, selected explicitly.
- Document questions with page references and Strict/Balanced/Flexible grounding.
- Short/Normal/Detailed reply preferences.
- Model downloads with checksums, visible progress and cancellation.
- Saved conversations, separate per-session choices, text copy and confirmed deletion.
- Database creation, editable extraction review and archive inspection on Android.
- Encrypted databases and signed, device-bound creator-issued access codes.
- Light/dark themes, text sizes and adaptable layouts.

The separate [DataEater Builder](https://github.com/verum001/DataEater-builder)
provides the same database workflow through a Linux desktop and Python CLI.
Scanned PDFs need external OCR; diagrams are not reconstructed as knowledge.

## Privacy

Local chat, retrieval and database building do not upload documents. Model
downloads contact Hugging Face without sending questions. Online mode requires
your own OpenRouter key and sends online messages to OpenRouter and its selected
provider. Local chat history is not replayed online; document excerpts are off
unless sharing is enabled. Online mode must be selected again after restarting.
There is no telemetry, analytics or crash-reporting service. Private chats and
keys are excluded from Android cloud backups and migration.

See [online setup and privacy](docs/OPENROUTER.md) and [security](docs/SECURITY.md).

## Build and inspect

The complete original Android implementation is in `app/src/main`, including
retrieval, model prompts, crypto, online requests and the native Android builder.
Unit tests are in `app/src/test`; synthetic hardware checks are in `app/src/androidTest`.

Install the Android SDK with API 37, build-tools 36.0.0 and a JDK 25 environment.
Set `ANDROID_HOME` to your SDK or configure an ignored `local.properties` file.
Then run:

```sh
./gradlew testResearchUnitTest assembleResearch assembleRelease lintRelease
```

Research builds use `com.dataeater.app.research` and install separately from the
normal app. Release assembly is unsigned; signing keys are supplied outside Git.
Exact dependency versions and the checked Gradle wrapper are included.
See [development guide](docs/DEVELOPMENT.md) for setup and validation limits.

## Documentation

- [Architecture and source map](docs/ARCHITECTURE.md)
- [Database format](docs/DATABASE_FORMAT.md)
- [Android database builder](docs/ANDROID_BUILDER.md)
- [Linux builder commands](docs/BUILDER.md)
- [Encryption design](docs/ENCRYPTION_DESIGN.md)
- [Model catalogue](docs/MODEL_CATALOG.md)
- [Implementation decisions](docs/DECISIONS.md)
- [Roadmap](docs/ROADMAP.md)
- [Contributing](CONTRIBUTING.md)

Source on the default branch includes conversation-save/checkpoint and archive
limit improvements after the published 1.4.2 APK. Those older release binaries
have not been replaced. [Provenance](PROVENANCE.md) distinguishes them from source.

## License

Copyright 2026 Jack Littlejack. Original code, documentation and artwork are licensed under
[Apache License 2.0](LICENSE), with attribution in [NOTICE](NOTICE). You can inspect,
modify and redistribute the source under that license. Third-party code keeps
its own terms; see [third-party notices](THIRD_PARTY_NOTICES.md). Model weights and
input documents have separate licenses. 
