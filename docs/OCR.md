# Offline scanned-PDF recognition

OCR recognises printed text in PDF page images during database creation. It runs offline in English, Russian or both. The standalone desktop builder still needs external OCR.

## Create a database

Open Settings → Database settings → Create database. Enter a name and select
files. Enable **Read scanned PDFs (OCR)** and choose English, Russian, or both.
English is the default when enabling OCR. Press Create normally. No AI model,
account or internet connection is needed. TXT/Markdown files use normal extraction.

Every PDF page is checked. Pages with fewer than 100 characters of selectable text
are candidates for OCR, matching the existing sparse-page cutoff. Usable native
text is preferred; a recognised reading replaces thin native text only when it
contains at least 24 letters/digits and more letters/digits than the native reading.
Short accepted OCR title/label pages retain their original page numbers too.
Low-confidence/empty OCR never erases an existing native reading. Blank or unusable
pages remain listed as omitted in the quality report. Invisible or faulty text
layers longer than the cutoff are not automatically detected or corrected.

Preparation runs in the existing background service with file/page progress. A
single page image is held at a time, limited to 3 million pixels and a 2,200-pixel
long edge. Existing 256 MiB input, 10,000-page and collection text/package limits
remain; OCR output exceeding 50,000 characters on a page stops preparation instead
of silently truncating database content. Large scanned manuals can take a long
time and use battery. Split large documents if needed.

The quality report records each OCR attempt's language, mean confidence and
outcome (used, not improved, or no usable text), alongside omitted pages and source
fingerprints. Confidence is a recognition hint, not proof of technical accuracy.
The report contains no source text; the database does contain recognised text.
Embedded reports follow database encryption. Explicitly exported reports are
unencrypted, as before. Technical values, dates, identifiers, warnings, table
relationships and reading order require comparison with the original PDF.

The same extraction is used when preparing an optimisation ZIP. Original source
copies, fingerprints, page markers and OCR provenance stay in the review package.
Continue after optimisation uses the reviewed text; it does not repeat recognition.
OCR is independent of optional LLM optimisation. Nothing is uploaded automatically.

## Accuracy and privacy

Language data is bundled. OCR makes no network requests and needs no account.
Text with mean confidence below 40 is discarded. Verified language data remains
in private storage; temporary source copies and bitmaps are cleaned up. OCR text
is not written to application logs. Optimisation ZIPs explicitly contain unencrypted
originals and recognised text, so share them only when intended.

Rotated, blurred, handwritten or complicated pages may need preprocessing or a
different workflow. OCR does not reconstruct missing facts or certify a manual.
Recognition runs on a worker thread. Cancellation is checked between page tasks;
the blocking native call is not a hard-timeout sandbox and must finish first.

## Open-source build

[Tesseract](https://tesseract-ocr.github.io/tessdoc/) and the Java/JNI wrapper use
Apache-2.0; Leptonica has a BSD-style licence. Core libraries are built from pinned
vendored source. Optional native JPEG/PNG decoders are absent; Android decodes the
PDF pages into bitmaps. The selected core versions are Tesseract 5.5.3 and
Leptonica 1.87.0. Language-file commits, sizes and hashes are recorded alongside
the assets. Existing cached language files are verified and repaired atomically
from the bundled assets before use.

OCR runtime code and language assets are available for source review. F-Droid inclusion and reproducible signing remain subject to the normal release checks.
