# Third-party components and model terms

Source publication does not redistribute model weights, user documents, PDF manuals, signing keys or Python/Android installed packages. The Gradle wrapper is the standard Gradle bootstrap tool, licensed under Apache-2.0: https://github.com/gradle/gradle/blob/master/LICENSE.

## Direct app dependencies

- AndroidX (Core, Activity, Lifecycle, Compose and Material): Apache-2.0; https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt
- Kotlin and coroutines: Apache-2.0; https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt and https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt
- Bouncy Castle 1.72, used by PDFBox: MIT-style upstream licence; https://www.bouncycastle.org/about/license/. The upstream licence is included at app/src/main/assets/builder/notices/BouncyCastle-LICENSE.txt. Its TLS classes are not used for DataEater’s network connections.
- PDFBox-Android 2.0.27.0 (Apache PDFBox/FontBox Android port): Apache-2.0, with included resource notices; https://github.com/TomRoush/PdfBox-Android. Upstream LICENSE.txt and NOTICE.txt are included in APK assets at app/src/main/assets/builder/notices/. Android PDF extraction uses this library; no PyMuPDF/MuPDF is bundled.
- Google LiteRT-LM: Apache-2.0; https://github.com/google-ai-edge/LiteRT-LM/blob/main/LICENSE
- JUnit, AndroidX Test, Espresso and org.json are test dependencies; they are not shipped in the release app. JUnit uses EPL-1.0; org.json has its own upstream licence.

These are direct-dependency references, not a substitute for the licences and notices of their transitive dependencies. When distributing a compiled APK, preserve applicable upstream notices for the actual resolved binaries. Gradle resolves packages separately; installed libraries are not checked into this source repository.

## Python builder

- PyMuPDF / MuPDF: GNU AGPL v3 or a commercial Artifex licence. See https://pymupdf.io/licensing and https://github.com/pymupdf/PyMuPDF/blob/main/COPYING. The repository's source-code licence does not remove these obligations. Do not assume a permissive app licence makes a proprietary distribution of the PDF builder permissible. No PyMuPDF binaries or code are vendored here.
- cryptography: upstream Apache-2.0 or BSD terms; https://github.com/pyca/cryptography/blob/main/LICENSE. Its installed native dependencies have separate notices.
- Optional OCRmyPDF and Tesseract are external tools, not app components.
- Pillow is only needed for the optional launcher-icon generation utility.

## Model downloads

Every model keeps its publisher's own licence, independently of DataEater. Consult the pinned Hugging Face repository before redistributing weights or using a model commercially. The curated list records the advertised licence in ModelCatalog.kt; source, hashes and runtime limitations are in docs/MODEL_CATALOG.md. No model weights are uploaded with this project.

## Synthetic database

The invented HF-4500 sample in dataeater/databases/demo.dataeater is CC0-1.0, as declared in its manifest. No real service manual is included. User-created databases have the licences and copyrights of their own source material; the app licence does not grant rights to those documents.

The separately maintained Linux builder is at https://github.com/verum001/DataEater-builder. Its dependencies are not part of the Android app. Public test fixtures contain deliberately disposable keys and synthetic documents; never use those keys for real data.
