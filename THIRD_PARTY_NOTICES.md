# Third-party notices

The DataEater app uses Google LiteRT-LM, AndroidX, Kotlin and Kotlin coroutines. Version 1.4.0 adds PDFBox-Android 2.0.27.0 (Apache 2.0) and Bouncy Castle 1.72 (upstream MIT-style terms) for the Android database builder. Their code is separate from DataEater's private implementation and keeps its original licensing. AI weights are downloaded separately and retain each publisher's licence. This repository does not grant rights to models or documents.

PDFBox license and resource notices and the Bouncy Castle license are included in APK assets under `builder/notices/` and accompany the prepared 1.4 release notices archives. No PyMuPDF/MuPDF binary is bundled in the Android builder.

The [resolved APK dependency inventory and upstream license texts](releases/third-party/DEPENDENCIES.md) accompany the APK. In particular, [LiteRT-LM’s complete native third-party notices](releases/third-party/LiteRT-LM-THIRD_PARTY_NOTICE.txt) are preserved unmodified. No Python builder or PyMuPDF binary is included in the public repository or Android APK.

The invented HF-4500 demo is CC0-1.0. Real technical manuals and databases derived from them have separate rights and are not included.
