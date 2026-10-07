# DataEater 1.1.0 — Android preview

A signed APK and SHA-256 checksum are prepared for this preview. The APK is an attachment in this repository’s **Releases** section, rather than a file in the source tree. Download the APK and its accompanying third-party notices from the same release. It is a preview, not a Google Play release or a promise of support on every Android device.

## Install

1. Open this repository’s **Releases** section and download `DataEater-1.1.0.apk`.
2. Allow your browser/file manager to install the file when Android asks.
3. Open DataEater, allow storage access, and tap Download AI.
4. Choose an AI model suitable for your device. Document questions also need a compatible database.

No model weights are bundled in the APK. The invented demo database is separate from the application.

## Existing development installations

The public APK has a dedicated release signing certificate. An earlier development/debug installation signed by another key cannot be updated directly with this APK. Do not uninstall an installation containing chats or paid database licences casually: private chats and the device key are removed on uninstall, and licences may need reissue. Prefer a separate device for evaluating the first public release if you need to preserve a development installation.

Keep the official release signing key private; future APK updates need the same key. The private key is never included in this public repository.

DataEater original software and presentation materials are licensed under Apache 2.0. The accompanying notices archive includes LICENSE, NOTICE and third-party terms. The implementation source remains unpublished.
