# Create databases on Android

Available in DataEater 1.4.0. Open **Settings → Create database** using the wide button at the bottom.

## Getting started

Open Settings and tap Create database at the bottom. The task chooser includes all seven builder tasks. Everything happens on your phone: no account, model or network connection is needed to build a database.

Choose Build database, add PDF, text or Markdown documents, enter a readable name and press Build database. Choose folder reads supported files in that folder and its subfolders. Check Details for warnings. Save file uses Android's file picker. Use in DataEater adds the result to the app's database list when folder access is available.

The moving progress bar means work is running; it does not estimate a percentage. Cancel stops the operation at a safe checkpoint. Existing databases, keys and review workspaces are kept. A complex PDF page can take time before cancellation is acknowledged.

Advanced options contain metadata and passage sizes. Help explains each workflow. Forms are retained across ordinary screen rotation, but unsaved form data can be lost if Android terminates the app. Review workspaces and publisher keys stay in app storage; uninstalling the app removes them. Export secure backups before uninstalling.

## Building databases

Select PDF, TXT, MD or Markdown files, or a document folder. The builder extracts text and makes searchable passages. It does not train a model or automatically correct technical information.

Name identifies the database in DataEater. Advanced options include description, language code, document publisher, content licence and passage size. Language records the source language; it does not translate text. Content licence records rights in the source material, not the app's software licence.

Passage size normally targets 700 characters. Smaller passages may lose context; larger passages may be harder for a small model to use. Complete sentences and table rows may exceed the target. Try important questions against the original document before publishing.

Scanned PDFs need OCR elsewhere. No OCR is included. Pictures, diagrams and relationships shown only by layout or arrows are not reconstructed. Even PDFs with selectable text can have broken reading order or tables. Use text review and compare extracted values, units, warnings and identifiers against the PDF.

Sparse pages can be omitted, but retained passages keep original PDF page positions. Printed page labels may differ from those positions. Details reports omissions and sparse-text warnings. The phone uses PDFBox rather than the Linux builder's PyMuPDF, so reading order and passage boundaries can differ even though database files are compatible.

Phone building is limited to 12 MB of extracted source text per collection and 256 MB per input file. For larger or very complex documents, split the collection or use the Linux builder. Existing files are never overwritten by Use in DataEater; another filename is chosen.

## Reviewing extracted text

Prepare text review creates untouched originals, editable batches, source fingerprints, page metadata and an LLM prompt. No text is sent online. Whole pages stay together; a long page can exceed the selected batch size.

Save review ZIP exports the workspace. Save review prompt exports LLM_PROMPT.txt. Give that prompt and one original batch to a large LLM only when you are allowed to share the document. If needed, supply the matching PDF pages. Ask for extraction corrections only, never invented facts or procedures.

Choose a batch, then Original to read and copy its untouched text. Edit changes the reviewed copy inside DataEater. Import edit replaces that reviewed copy with a complete checked plain-text file. Keep every === PAGE n === marker exactly as supplied and in order. Empty, missing or renumbered pages are refused. Notes stores uncertainties separately from the database text.

Check numbers, decimal points, units, warnings and part identifiers against the PDF. A valid marker does not prove a correction is accurate. Build reviewed text reports how many pages have numeric changes.

Build reviewed text accepts an existing app workspace, a review ZIP or a review folder made by the Linux builder. A ZIP can contain the workspace contents or one enclosing folder. Do not import a folder containing several reviews. Keep original/ and review.json unchanged.

To use a phone review on Linux, unzip it and run build-reviewed on the folder containing review.json. Never run a direct build on the whole review folder: it would mix originals and edited copies. Very large individual batches may need editing on Linux; the phone editor/import accepts up to 2 MB per batch.

## Checking databases

Check database verifies the format, manifest fingerprints, document count, passage count, IDs and source references. Details shows document names, a sample passage and counts of oversized or exact repeated passages for an unlocked database.

A long passage can be legitimate when a paragraph or table row must stay together. Repetition can reflect repeated headers or real repeated instructions. Do not delete content solely because it appears in this report.

Locked databases can be checked without exposing their text. Their readable manifest identifies the creator, public signing key and encryption. The text remains encrypted.

Fingerprint checks detect damage relative to the manifest. They do not authenticate a publisher, prove ownership, verify technical accuracy or guarantee model answers. Check important facts against the original manual. A file that fails verification should be rebuilt from known-good documents before distribution.

## Protecting databases

Protect database creates a locked copy of an unlocked database. Choose or import your signing key, and enter the creator name and contact. If the private signing key is kept elsewhere, paste only its public key in Advanced options. The public key is safe to publish and lets DataEater verify access codes.

The encrypted database can be saved and shared. Its content key stays in private app storage, encrypted using Android's keystore. Back up content key explicitly exports a standard .secret file for use on Linux. Keep that backup secure: anyone holding the content key can decrypt the database.

Advanced options can reuse an existing content key. New key for this copy generates a different key; old access codes do not unlock the new copy. Earlier app keys and databases are preserved. Give each version a distinct database name where possible, and keep the matching key and signing key with your private release records.

Never publish .secret or .key files. Export secure backups before uninstalling DataEater or resetting the phone. An app-only key cannot be recovered after uninstalling. Android backups exclude private builder data.

Encryption protects stored content and enables device-bound access codes. It does not prevent an authorized reader from copying displayed information.

## Signing keys

Create signing key generates an ECDSA P-256 key pair. The private key is stored behind an Android-keystore wrapping key; the public key appears in Details and is safe to share. New signing keys keep old keys intact.

Back up signing key asks for confirmation before exporting a .key file. The exported file contains private material and is compatible with the Linux builder. Keep it in a secure backup, never in a GitHub release or customer message. Import signing key reads a Linux-generated .key file and stores it privately in the app.

A locked database carries the public key of its signing key. Issue access code refuses a different signing key. Keep older keys to continue issuing codes for older databases. Losing a private signing key means you cannot issue valid new codes for databases tied to its public key.

Android system backups exclude publisher keys and review content. Uninstalling removes the app's stored keys; export backups first. A public key alone cannot sign access codes or reconstruct the private key.

## Device access codes

A customer opens a locked database and sends the phone's Request Code. Issue access code needs that request code, the locked database, its matching content key and the signing key corresponding to the database's public key.

The builder checks that both keys belong to the database. Expiry defaults to never. Advanced options accepts a duration such as 30d, 90d or 1y, or a date in YYYY-MM-DD form. The resulting code is signed and works only on the requesting device.

Save file exports a .lic file. Send that file only to the customer. They save it in DataEater/Lic in internal phone storage and select it in the Android app. View access code displays text that can be selected and copied if a file cannot be used.

Access codes contain wrapped content-key material. Keep them out of public screenshots, repositories and releases. The app does not send codes or documents automatically and does not manage payments.

If unlocking fails, check the database version, matching content key, signing key, request code and expiry. Imported Linux keys and access codes use the same portable formats as those created on the phone.

## Troubleshooting and privacy

No text found: the PDF may be scanned, blank or use unsupported extraction. Run OCR elsewhere and check the text. No OCR or diagram reconstruction is included in this builder.

Review rejected: restore untouched originals and review.json. Keep every original page marker in the reviewed copies, without commentary or Markdown fences. Do not bypass validation by building the whole export folder.

File access failed: choose it again in Android's file picker. Building uses the selected documents and needs no broad storage permission. Use in DataEater needs access to the DataEater folder; Save file lets you choose the destination yourself.

Operation slow or too large: split the document collection or use the Linux builder. Progress is not a completion percentage. Cancelling can wait for a PDF operation to reach a checkpoint. Processing is kept off the interface thread.

Incomplete save: Android document providers control external files. A cancelled or failed Save file attempts to remove the new incomplete file, but a provider may refuse deletion. Check the chosen destination before sharing a result. Earlier app workspaces, keys and databases remain intact.

No model API, OCR service, telemetry or automatic upload is used by the builder. Review text and keys are stored privately and excluded from Android backups. Only deliberate Save file or key-backup actions export data. Clipboard tools may retain copied text.

Only distribute documents and database content you have permission to share. Check technical corrections against the original source. A verified file is not proof that an AI answer or maintenance instruction is accurate.

