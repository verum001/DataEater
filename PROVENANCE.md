# Project and release record

DataEater — Jack — version 1.2.0 prepared 7 October 2026.

This repository contains product documentation, demonstration screenshots and
app releases. Android implementation and development history remain private.
The separate DataEater Builder repository contains the original builder source.

## Release identity

- Application: `com.dataeater.app`
- Version: **1.2.0**, version code **3**
- APK: `DataEater-1.2.0.apk`
- Architectures: ARM64 and x86-64; installation minimum Android 8.0
- Encrypted databases: Android 12 or newer
- APK SHA-256: `a219eb69f2a1cb1c1355893303f3079dbcbb0b631e45c6ea00d60196a8f3e99d`
- Signing certificate SHA-256: `85cd101a233a16732e630429eab9105293540894a6a78f4e4b92f0ca9b587040`

The existing signing identity is retained for app updates.

## Verification

247 app unit tests and nine builder test scripts passed. Release lint reported
zero errors and 36 warnings. The signed app was installed as an update and its
startup and model picker checked on a vivo V2453A with Android 16. Partial release
optimization was disabled after a startup failure; the corrected release passed
the phone check. See [benchmarks](docs/benchmarks.md) for model comparisons and limits.

Original software and documentation use Apache 2.0. Models, dependencies and
source documents retain their own terms. GitHub records the actual publication
date when each release is published.
