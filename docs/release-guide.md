# Publish DataEater v1.2.0

1. Update the repository with the prepared public documentation: `README.md`,
   `CHANGELOG.md`, `PROVENANCE.md`, benchmark summary and release documentation.
   Keep Android implementation and private benchmark records outside this repository.
2. Open **Releases → Draft a new release**. Create tag **v1.2.0** on the updated
   documentation commit, with title **DataEater v1.2.0**.
3. Paste `releases/v1.2/RELEASE_NOTES.md` into the release description.
4. Attach `DataEater-1.2.0.apk`, `DataEater-1.2.0-notices.zip` and `SHA256SUMS`
   from the prepared `releases/v1.2` folder. Publish when the attachments finish uploading.
5. Open the published release and test the README’s Android app download link.

The APK belongs in **release attachments**, not the repository file upload form.
GitHub’s browser repository uploads are limited to 25 MiB per file; release
attachments use a separate upload interface. The FAA database link still refers
to the earlier v1.1.0 asset, which remains available.

Write user-visible changes at the top of `CHANGELOG.md`. Copy that version’s notes
into the release description. Update `README.md` for current features, recommended
models and download links. Use the commit summary for a short description of each
repository update. Keep earlier release notes and changelog entries.

See [GitHub release instructions](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
and [repository upload instructions](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository).
