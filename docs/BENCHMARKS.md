# Compare installed models

Open **Settings → Start benchmark**, below Create database. Add each question,
expand Select models, and tick the installed models to compare. Use database is
on initially: choose databases to search together, or switch it off for general
knowledge answers. Private databases need a valid remembered Unlock Code.

Human rating adds five stars and optional feedback beneath each finished answer.
Run benchmark opens the chat view and automatically asks every question of each
model. Models run sequentially, load once, and receive a fresh conversation for
each question. The current reply preferences and processor preference are captured
at the start. Stop benchmark keeps completed answers. Keep the app open during
measurement; the screen stays awake while the run is visible.

Results live in `/sdcard/DataEater/benchmark`. Tap a filename to reopen answers and
edit ratings. Select files using checkboxes, then:

- **Download** copies text reports into Downloads/DataEater.
- **Share** opens the standard Android chooser for the selected reports.
- **Delete** removes selected reports and their companion checkpoints after confirmation.

A readable `.txt` report includes phone specifications reported by Android, date
and time with time zone, app version, selected models and databases, questions,
actual runtime backend, load/retrieval/answer/first-text times, complete or partial
answers, retrieved citations, optional feedback, and a text comparison table.
Its `.json` companion stores the run and ratings for reopening. Both stay local
until an export or share action. Reports can contain private questions and answers.
Do not share them accidentally. Licence and publisher keys are excluded.

## How to interpret a comparison

Times use a monotonic clock. Load time includes backend fallback. Answer time
includes generation and any input-budget retries; retrieval is timed separately.
Average answer and first-text times include completed answers only. Unrated
outputs are excluded from the human-rating average. No match means the database
search returned no passages and the model was not asked. Failed, cancelled and
unasked questions are visible in the table instead of receiving zero times.

This is a practical comparison, not a controlled accuracy evaluation. Runtime
caches may already be warm. Phone load, heat, battery state, model order and
model-specific context/output limits affect timings. Selected databases form one
collection, but fitted passages may differ with a model's input limit. Retrieved
citations do not prove every generated statement is supported. Faster or longer
answers do not imply more accurate answers, and a star rating is personal judgment.

Each generation has a five-minute limit. Loading is allowed to finish before
native resources are released. Completed answers are checkpointed. If Android
kills the app, reopening that result marks it interrupted; the current streamed
answer may be lost since its last checkpoint. Storage errors remain visible and
Save result retries saving the in-memory report. Uninstalling the app removes
private chat/vault data but shared benchmark reports may remain on storage.
