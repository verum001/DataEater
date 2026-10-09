# Project and release record

DataEater — Jack — latest prepared preview 1.4.2, built 8 October 2026. GitHub Releases records actual publication dates; preparation dates are not publication dates.

This repository contains product documentation, demonstration screenshots and
app releases. Android implementation and development history remain private.
The separate DataEater Builder repository contains the original builder source.

## Release identity

- Application: `com.dataeater.app`
- Version: **1.4.2**, version code **8**
- APK: `DataEater-1.4.2.apk`
- Architectures: ARM64 and x86-64; installation minimum Android 8.0
- Encrypted database unlocking: Android 13 or newer
- APK SHA-256: `2731cc764e0431ff6f3bd84b78a24d5d30f46a060f1f9b3157528a43784a1e22`
- Signing certificate SHA-256: `85cd101a233a16732e630429eab9105293540894a6a78f4e4b92f0ca9b587040`

The existing signing identity is retained for app updates.

## Verification

The archived 1.4.2 record reports 285 unit tests passing, two phone UI tests on vivo V2453A / Android 16, and release lint with no errors and 50 warnings. Phone checks used invented conversations and covered confirmed deletion, current and last-session behavior, persistence and storage failure handling.

For 1.4.0, archived checks report 277 unit tests and builder/device checks. For 1.4.1, 283 unit tests and real Qwen 4B overflow recovery were recorded. These are historical checks for the exact archived APKs. Later local development is not included in those APKs. No new phone test is implied by documentation updates. See [benchmarks](docs/benchmarks.md) for model comparisons and limits.

Original software and documentation use Apache 2.0. Models, dependencies and
source documents retain their own terms. GitHub records the actual publication
date when each release is published.
