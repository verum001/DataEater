# Create databases on Android

Open **Settings → Create database**. Building and encryption run locally; no account or online model is needed.

## Create a database

1. Enter a name and tap **Choose files** to select PDF, text or Markdown documents.
2. Turn **Encrypt** on for a private database, or leave it off for an open database.
3. Turn **Optimise with LLM** on to prepare a review ZIP, or leave it off to build directly.
4. Tap **Create**. The full-screen status explains that the result is saved in `/sdcard/DataEater`. Tap **Continue in background** to leave the progress screen.

The progress bar indicates work rather than a percentage. Cancel stops at a safe checkpoint; complex PDF pages may take time before cancellation is acknowledged. Existing files are preserved. Preparation continues while the activity is closed, but does not automatically restart after Android terminates the process.

The builder extracts text into searchable passages. It does not train a model or reconstruct diagrams. Scanned PDFs need external OCR. Reading order and tables can be imperfect even in selectable-text PDFs. Verify values, units, warnings and page references against the originals before sharing a database.

Source text is limited to 12 MB per collection and 256 MB per input file. Split larger collections or use the [Linux builder](BUILDER.md). Retained passages preserve PDF page positions, which can differ from printed page labels. Android extraction uses PDFBox and may produce different passage boundaries from the Linux builder.

## Optimise with LLM

Create with **Optimise with LLM** enabled saves a ZIP containing unchanged source files in `sources/`, parsed source text in `original/`, editable return text in `reviewed/`, review metadata, notes and `LLM_PROMPT.txt`.

The ZIP is unencrypted, including when Encrypt is selected for the eventual database. No automatic cloud upload occurs. Send it manually only to a service you trust. Do not include private key backups. Check the returned text against the originals, choose **Choose optimised ZIP**, and tap **Continue after optimisation**.

Original-file fingerprints, text fingerprints and page markers are verified before building. These checks do not establish factual accuracy. Finish all batches before importing the returned ZIP.

## Private databases and secret key vault

The first use of Encrypt asks to create the secret key vault. Database content keys and signing keys are encrypted in app storage using Android's key store. Private database filenames end in `-priv.dataeater`.

The vault list below Create shows each private database name. Tap a name to open the licence generator. Enter the receiving phone's request code and an expiry such as `never`, `30d` or `YYYY-MM-DD`, then tap **Generate** and save the licence key. The receiving phone applies the code from its database unlock screen. Encrypted access-code unlocking requires Android 13 or later.

**Backup** saves a private JSON bundle containing both the content key and matching signing key. Keep it in secure storage, separate from shared databases and optimisation ZIPs. **Restore key backup** verifies that the keys match an available private database. Uninstalling DataEater removes keys stored only inside the app. **Remove key** removes the stored content key and leaves the database file in place. Without a backup, new licences cannot be issued for that database.

See [encryption design](ENCRYPTION_DESIGN.md) for compatibility and verification details. Formats remain compatible with existing format-v1 files and portable Linux builder keys.


Preparation quality report (0.1.7.7 Android / 0.5.0 Linux)
------------------------------------------------------
Every new database embeds a source-linked preparation report. It lists original
page numbers, included/omitted text and available character/byte counts, sparse
pages, oversized passages and PDF pages whose reading order needs manual review.
PDF columns, table associations and component ownership are NOT automatically
verified. Older review exports may have unknown omitted-page sizes.

Reviewed builds also list changed page IDs and heuristic flags for restrictions,
numbers, units, part identifiers and numbered steps. These hints do not certify
technical accuracy, and legitimate formatting edits remain allowed. Restriction
hints cover English/Russian; all other changed text still needs source review.
No AI or internet is needed. Optional manual LLM review remains unchanged.

In Android, use Save quality report after creation. On desktop, use Inspect
database to read the complete report in the results pane; CLI users can use
inspect FILE.dataeater. Optimisation/review packages include quality_report.json;
that advisory copy is regenerated from validated originals and edits at build,
not trusted as proof supplied by the reviewer. Preserve every original page marker.

The embedded report is part of fingerprinted sources.json and is encrypted with
private database text. An explicitly exported report is unencrypted: keep it
private if source fingerprints or page counts are sensitive. Reports do not
contain document text or secret keys. Private-database inspection without a key
cannot display its encrypted report; retain the creator's saved report if needed.
