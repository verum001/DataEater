# Roadmap

The source implements local document chat, optional OpenRouter, model downloads,
conversation management, reply preferences and a native Android database builder.
The independent Linux Builder 0.4.0 provides a desktop and CLI.

Current source also improves atomic conversation saves, background streaming
checkpoints, safe session switching and limits on discarded ZIP entries. The
published 1.4.2 APK predates those changes; source publication does not replace it.

## Remaining work

- Broader Android version, small-screen, large-font and document-provider testing.
- Streaming database parsing to reduce peak heap use on large archives.
- Interface localization; the current interface is English.
- Measured semantic/multilingual retrieval alongside lexical search.
- Database catalogues and authenticated publisher metadata.
- Repeatable source-backed model evaluation with exact contexts and failure logs.

The app does not reconstruct diagram relationships, provide OCR, certify generated
technical advice or guarantee that a small model obeys grounding instructions.
Changes to retrieval or prompts need matched evaluation before accuracy claims.
See [architecture](ARCHITECTURE.md) and [historical phone checks](benchmarks.md).
