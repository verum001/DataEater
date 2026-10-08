# Publish a DataEater release

1. Update the repository with the prepared public documentation: `README.md`,
   `CHANGELOG.md`, `PROVENANCE.md`, benchmark summary and release documentation.
   Keep Android implementation and private benchmark records outside this repository.
2. Open **Releases → Draft a new release**. Create the matching version tag (for example, **v1.4.2**) on the updated documentation commit. Use **DataEater 1.4.2** as the corresponding title.
3. Paste that version's `RELEASE_NOTES.md` from `releases/v1.4.2/` into the release description.
4. Attach that version's APK, notices ZIP and `SHA256SUMS` from the prepared release assets. Verify their hashes and signing certificate before publication. Publish when attachments finish uploading.
5. Open the published release and test the README’s Android app download link.

The APK belongs in **release attachments**, not the repository file upload form.
GitHub’s browser repository uploads are limited to 25 MiB per file; release
attachments use a separate upload interface. The FAA database link still refers
to the optimized database asset in the existing `v1.2` release. Preserve existing releases and use their actual tags; the historical 1.2.0 APK was published under `v1.2`.

Write user-visible changes at the top of `CHANGELOG.md`. Copy that version’s notes
into the release description. Update `README.md` for current features, recommended
models and download links. Use the commit summary for a short description of each
repository update. Keep earlier release notes and changelog entries.

See [GitHub release instructions](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
and [repository upload instructions](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository).
