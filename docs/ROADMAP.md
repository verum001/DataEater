# Roadmap

The source implements local document chat, optional OpenRouter, model downloads,
conversation management, reply preferences and a native Android database builder.
The independent Linux Builder 0.4.0 provides a desktop and CLI.

The 0.1.7.2 release includes explicit multi-database selection, model discovery
and local benchmark reports alongside conversation management and the builder.

## Remaining work

- Broader Android version, small-screen, large-font and document-provider testing.
- Streaming database parsing to reduce peak heap use on large archives.
- Interface localization; the current interface is English.
- Measured semantic/multilingual retrieval alongside lexical search.
- Authenticated publisher metadata beyond public repository catalogue checks.
- Repeatable source-backed model evaluation with exact contexts and failure logs.

The app does not reconstruct diagram relationships, provide OCR, certify generated
technical advice or guarantee that a small model obeys grounding instructions.
Changes to retrieval or prompts need matched evaluation before accuracy claims.
See [architecture](ARCHITECTURE.md) and [historical phone checks](benchmarks.md).

### 11 October 2026 — Multi-database search

1.5.0 source adds All unlocked databases for text search and AI retrieval, distinct cross-file citations, visible scope/exclusions and bounded collection size. Survival 0.1.0 is a separate versioned collection of three historical manuals. Automated tests and APK build are checked; phone validation depends on a connected device.

### 13 October 2026 — Local benchmark release

Question lists, model/database checkboxes, automatic transcripts, optional ratings
and exportable phone/timing reports are included in0.1.7.2. Controlled source-backed
accuracy evaluation remains separate work. See [benchmark guide](BENCHMARKS.md).
