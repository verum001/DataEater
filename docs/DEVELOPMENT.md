# Build and test DataEater

## Requirements

Use the versions declared in `gradle/libs.versions.toml` and the Gradle wrapper.
The current build uses Gradle 9.6.0, Android Gradle Plugin 9.4.1, Kotlin 2.4.20,
LiteRT-LM 0.17.1 and Android API 37. The daemon toolchain is JDK 25. Install
Android SDK platform 37 and build-tools 36.0.0; configure `ANDROID_HOME`, or an
ignored `local.properties` file containing `sdk.dir=/path/to/Android/Sdk`.
Dependencies resolve from Google Maven, Maven Central and the Gradle plugin portal.
Initial setup needs internet access; `--offline` works only after dependencies
and toolchains are cached. `JAVA_HOME` can select an installed JDK.

## Commands

```sh
./gradlew testResearchUnitTest
./gradlew assembleResearch
./gradlew assembleRelease lintRelease
```

JVM results are written to `app/build/reports/tests/testResearchUnitTest/`.
Lint reports are under `app/build/reports/`. Unsigned release output is
`app/build/outputs/apk/release/app-release-unsigned.apk`. Supply your own signing
configuration outside source control. An APK signed with a different certificate
cannot update an existing installation while preserving its private app data.

The isolated research APK has application ID `com.dataeater.app.research`:

```sh
adb install -r app/build/outputs/apk/research/app-research.apk
```

Hardware tests can require explicit instrumentation arguments, a selected model
and selected research-owned files. Inspect the test source before running it;
never point destructive test fixtures at production app data. Background native
inference may be suspended by some vendor firmware, so timing checks require a
foreground app. Restore temporary display/power settings after testing.

## What the tests cover

JVM tests exercise retrieval, prompt budgets, scoped conversation memory, save
failures, deletion, streaming checkpoints, archive limits, model-file safety,
OpenRouter protocol handling, cryptography and Android builder logic. Synthetic
fixtures and disposable public test keys are not suitable for real documents.
Native inference, Android Keystore, storage permissions, PDF providers and screen
layouts need physical-device validation. Passing JVM tests does not establish
model accuracy or support for every Android version or device.

## Separate Linux builder and interoperability

Clone the builder beside the Android project when using the compatibility tools:

```sh
git clone https://github.com/verum001/DataEater-builder.git ../DataEater_builder
python3 -m venv ../DataEater_builder/tools/.venv
../DataEater_builder/tools/.venv/bin/pip install -r ../DataEater_builder/tools/requirements.txt
python3 tools/tests/test_no_internet_permission.py
```

The permission check reads an already merged Android manifest. INTERNET is
allowed for explicit downloads and OpenRouter; tracking permissions and automatic
backups are rejected. Run builder regression scripts from its own repository.
Fixed Python/Kotlin crypto vectors check compatibility. Android uses PDFBox;
Linux uses PyMuPDF, so extraction reading order can differ.

## Concurrency and data handling

Keep UI updates on the main thread. Save session snapshots atomically and surface
storage errors before switching or deleting sessions. Do not append callbacks
once a stream is terminal. Native input-overflow recovery replans whole passages
and replaces the displayed source list; it does not truncate an individual passage.

Do not upload personal chats, real documents, model weights or signing material
with a bug report. Use an invented minimal fixture and include toolchain versions,
Android/device details, exact commands and the observed error.
