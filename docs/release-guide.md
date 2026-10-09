# Release checklist

1. Update versionCode/versionName, changelog and user documentation. Build from
   the exact public commit and run unit, lint and relevant device checks.
2. Tag the source commit for that version. Keep tags aligned with actual source;
   older documentation-only release tags are historical and cannot build the app.
3. Sign outside Git using the existing release identity when updating the installed
   app. Verify APK checksums, certificate, permissions and dependency notices.
4. Attach the APK, applicable license/notices archive and checksums to the matching
   GitHub Release. Record measured validation and any untested behavior accurately.
5. Verify links and download integrity. Keep private data and keys out of assets.

Existing APKs and releases are preserved. The default branch includes later source
changes; those are not automatically part of an earlier binary. See [provenance](../PROVENANCE.md).
