# Project and release record

**DataEater · Created by Jack · Public preview prepared 7 October 2026**

The working Android implementation was developed during October 2026. This public package presents a local AI knowledge engine for technical document questions, with a functioning Android APK and screenshots from an invented demonstration database.

## What is public

High-level product documentation, screenshots, a dated changelog, the signed preview APK and its third-party notices. No private source, database construction process, prompts, ranking/preprocessing code, real manuals or customer conversations are included.

The original development Git history and source remain privately preserved. The public repository begins with a separate showcase history. It does not publish the original development commits or claim they were publicly available at their private dates.

## Artifact identity

- Application: `com.dataeater.app`
- App version: **1.1.0**, version code **2**
- Release APK: `DataEater-1.1.0.apk`
- Architectures: ARM64 and x86-64; installation minimum Android 8.0
- Encrypted database access: Android 12+ required
- APK SHA-256: `ea5b9d9f9974aa2262c4a9d99d8870d512ce619edc3dbf669ede06588c367650`
- Signing certificate SHA-256: `85cd101a233a16732e630429eab9105293540894a6a78f4e4b92f0ca9b587040`

The full file checksums are in [releases/SHA256SUMS](releases/SHA256SUMS). Keep the original certificate for future updates. Private signing keys are not published.

## Verification scope

237 JVM tests passed. Eight Python builder suites and the Android permission/backup guard passed. Debug and optimized release builds succeeded. Android release lint reported zero errors and 36 warnings, mainly dependency-update suggestions, style/performance advice and launcher icon recommendations.

The optimized release variant was exercised on a real Android 16 phone using an isolated application ID and storage folder to protect private user data. It loaded LFM 2.5 1.2B on the GPU and answered the invented fuel-pressure question with source references. The public APK uses the normal application ID; its signature, version, 64-bit native libraries, backup settings and absence of bundled model/database/key files were checked separately. It was not installed over a differently signed private development installation.

Not all catalog models have been downloaded or inference-tested. Layout checks on one phone do not constitute hardware validation on all Android devices. Generated answers may still be wrong; verify sources.

## Publication dates

This is a local preparation record. The package has not been uploaded by the development assistant. GitHub's repository/release timestamps will record the actual publication when the creator manually uploads it. This record does not confer exclusivity over general local-AI/document-retrieval concepts.

## Licensing decision

On 7 October 2026, the creator selected Apache License 2.0 for DataEater original software and project documentation, including the published APK. This replaces the previously prepared restrictive terms. Source availability remains private; no publication has been performed by the assistant. The APK bytes and signature are unchanged by this licensing update. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
