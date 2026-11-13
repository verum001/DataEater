# Synthetic PDF naming fixtures

These are generated test documents, not copies of manuals.

- `34545we3.pdf`: four pages. Pages 1–3 contain a synthetic Isuzu Trooper 1994
  service-manual title and generic test text. Page 4 contains an unrelated marker
  and year that must never enter the excerpt.
- `887799.pdf`: one blank page, to exercise the no-selectable-text result.

The fixtures are used only by Android device tests and are not included in the
application APK. They are original test data under the repository's Apache-2.0
licence. The assertions run without a model or access to shared files.

- `scanned-title.pdf`: generated image-only four-page document. The first page
  contains an English title, pages 2–3 are blank, and page 4 has an unrelated
  marker that must not enter OCR evidence.
- `russian-title.png`: generated upright Cyrillic text for Russian recognition.

These additional fixtures test the on-device OCR engine without a chat model.
