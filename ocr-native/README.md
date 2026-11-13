# Offline OCR native module

This module builds Tesseract and Leptonica from the pinned source files in
`src/main/cpp`. It contains no prebuilt native libraries and uses no OCR service.
Java/JNI bindings derive from Tesseract4Android 4.9.0; `UPSTREAM.json` identifies
the original wrapper and the separately pinned core releases.

The application supplies Android Bitmaps. Native JPEG/PNG codecs and their Java
library loads are omitted; Leptonica builds their unsupported-operation stubs.
PDF page decoding uses Android's PdfRenderer. Generic upstream file-codec helper
APIs are not supported by this variant. OCR itself uses the standard single-thread
variant, with a separately owned instance per recognition session.

## Source build

The project uses Android SDK 37, NDK 27.2.12479018, CMake 3.22.1 and C++20.
Both arm64-v8a and x86_64 are built with flexible 16 KB page-size support.
Install these tools in the normal Android SDK before building. No source fetch,
JitPack AAR, custom Java downloader or compiler download script runs in this module.
Normal Gradle dependencies still require their usual resolution/cache.

`dataeaterNativeBuildDir` is an optional local Gradle property for placing native
build intermediates on another disk. It does not change the source or APK identity.

## Licences and provenance

- Java/JNI wrapper: Apache-2.0, `LICENSE` and original file headers.
- Tesseract 5.5.3: Apache-2.0, `src/main/cpp/tesseract/src/LICENSE`.
- Leptonica 1.87.0: its BSD-style licence,
  `src/main/cpp/leptonica/src/leptonica-license.txt`.
- English/Russian language data is separate application data; see
  `../app/src/main/assets/ocr/UPSTREAM.json` and its Apache-2.0 `LICENSE`.

Samples, unit tests, training tools and documentation assets from upstream are
omitted. Runtime sources, required headers and legal notices are retained.
Local changes to build configuration and library loading are listed in
`UPSTREAM.json`. App runtime limits and behaviour are described in `../docs/OCR.md`.
