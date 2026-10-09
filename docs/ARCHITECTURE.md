# Architecture

Current implementation: DataEater 1.4.2, with conversation-storage and archive-reading improvements in the working tree.

DataEater opens document databases, retrieves matching passages, and generates answers with source references. On-device AI is the default; OpenRouter is an explicit online option. A separate Android builder creates compatible databases on the phone, while the independent Linux builder provides CLI and desktop workflows.

## Components

| Layer | Main components | Responsibility |
|---|---|---|
| UI and coordination | `MainActivity`, `ChatScreen`, `SettingsScreen`, session drawer, search/help/unlock screens | Visible modes, model/database choices, generation, storage and recovery |
| Conversations | `SessionController`, `SessionCodec`, `SessionStore`, `ConversationMemory`, `StreamingAnswer` | Session settings, scoped complete-turn memory, partial/final answer state and persistence |
| Answer decisions | `ChatResponder`, `ReplyBehavior`, `BoundedGeneration` | Decide whether to generate, apply reply preferences, fit model input and recover from local overflow |
| Retrieval | `SearchEngine`, `RagEngine`, `AnswerCleaner` | Lexical indexing, passage selection, prompts, citations and removal of exposed reasoning blocks |
| AI | `LocalAiEngine`, `LiteRtAiEngine`, `OpenRouterAiEngine`, `OpenRouterClient` | Local LiteRT-LM inference or optional streamed online generation |
| Model files | `ModelCatalog`, `ModelDownloads`, `ModelInstaller`, `ModelDeletion` | Curated downloads, memory-fit estimates, staged verification and file management |
| Database data | `DatabaseReader`, `DatabaseIntegrity`, `DatabaseLimits`, `DatabaseLock`, `DatabaseFiles` | Discover, classify, verify, decrypt and parse database containers |
| Access codes | `DeviceKeys`, `UnlockCode`, `LicenceStore`, `LicenceFiles` | Device keys, signed licence checks and saved unlock information |
| Android builder | `BuilderCore`, `BuilderReview`, `BuilderCrypto`, `PublisherVault`, `BuilderFiles`, builder UI/ViewModel | Extraction, review, packaging, protection, key management and file-picker output |

`MainActivity` coordinates much of the application state. Pure rules are separated for JVM testing, but UI orchestration remains substantial; further extraction should preserve existing session, cancellation and sharing boundaries.

## Retrieval and answer flow

1. `ChatResponder` checks the question, model availability and selected answer mode.
2. `ConversationMemory` selects bounded complete turns from the matching context. Local and online histories are scoped separately, as are database identity and reply strictness.
3. `SearchEngine` ranks lexical matches using a cached index, term rarity, length normalization, heading/context bonuses and query coverage. It filters contents-style passages from answer evidence. It does not implement embeddings, semantic synonym matching or cross-language translation.
4. `RagEngine` normally retrieves three passages, or up to six for certain list questions, then fits complete passages within the input budget. The best passage must fit; text is never silently cut.
5. `RagEngine.buildPrompt()` places numbered reference passages before the current question. Follow-ups can carry the previous user question as subject context. Grounding instructions are supplied through the engine's system role and reply preferences; the old question-sandwich prompt is no longer used.
6. Local generation uses LiteRT-LM 0.17.1 with CPU/GPU selection. If the runtime rejects input before any output is streamed, `BoundedGeneration` replans at a smaller budget, at most three times. Citations follow the final selected passages. OpenRouter requests use their own request/stream protocol and do not use the local overflow retry.
7. The answer is streamed, cleaned and saved with source labels, context and completion state. A stopped, timed-out or failed answer remains incomplete and is excluded from replay memory.

Retrieval finds matching text, not proof that the text answers the question. A source label identifies a supplied passage; it does not verify every generated claim. Model responses can still add unsupported information.

## Visible answer and network modes

With Database on, no retrieved passages means the model is not called. Strict asks for source-only answers; Balanced permits supported explanations; Flexible may add labelled general background. With Database off, the answer is marked as unchecked general knowledge.

OpenRouter selection requires an API key. Online requests send the current question and selected complete online turns. Retrieved database excerpts are included only when the separate sharing setting is enabled. This also applies to plaintext recovered from encrypted databases. Local chat history is not replayed online. Restarting returns to on-device mode; the saved key and model choice remain available.

Model downloads use pinned HTTPS URLs and Android DownloadManager. Installation stages the file beside its destination and verifies pinned size and SHA-256 before publishing it. Online AI uses a fixed HTTPS service origin and rejects redirects. Neither path is a telemetry service. See [OpenRouter](OPENROUTER.md) and [Security](SECURITY.md).

## Conversation storage and streaming

Each session is one JSON file in private app storage. It records messages, citations, completion state and model/database settings. API keys and licensing keys are stored separately.

`SessionStore` writes and syncs a temporary file, then atomically replaces the prior JSON file in the same folder. It does not delete the previous save first. If replacement fails, the old save remains. Incomplete staging files are ignored when loading sessions. This protects file replacement; it does not promise survival of every filesystem or power failure.

Streaming updates change the controller's in-memory copy without writing per callback. A sequential coroutine checkpoints immutable snapshots on an IO thread about once a second, then finishes any older checkpoint before saving the final answer. A sudden process kill can lose text received since the last successful checkpoint. Storage failures during checkpoint/final saving produce a status message while the conversation remains in memory.

`StreamingAnswer` serializes background text accumulation and closes the stream before final UI updates. Queued or late callbacks cannot overwrite the final answer as incomplete. Normal message and settings edits retain immediate saves. Deletion is confirmed, blocked while busy and retained in the list if storage deletion fails.

## Database container and limits

Format v1 is ZIP with a readable manifest. Plain databases contain `sources.json` and `chunks.jsonl`; protected databases contain `payload.enc`. Required knowledge-file fingerprints are verified against the exact bytes before parsing or decrypting. Missing, malformed or mismatching hashes refuse opening.

Android caps each decompressed entry at 32 MiB, including entries discarded while searching; it also caps a scan at 64 MiB and 10,000 entries. It walks the archive separately for required files. These caps bound decompression work, but parsing still retains byte arrays, decoded text, JSON objects, passages and a search index. Very large or unusually dense databases can still require more heap than a phone provides. Streaming parsing remains future work.

Manifest hashes detect changes relative to the manifest. The manifest is not signed, so they do not establish publisher identity or protect against replacing both data and hashes. Access codes are signed separately. See [Database format](DATABASE_FORMAT.md) and [Encryption design](ENCRYPTION_DESIGN.md).

## Builder boundaries

The Linux engine lives in `../DataEater_builder`; the main app's legacy launcher forwards there. Linux PDF extraction uses PyMuPDF; Android uses PDFBox. Reading order can differ. Both preserve original page references and validate reviewed-text structure, but fingerprints/page markers do not establish technical accuracy. Neither workflow includes OCR or reconstructs diagrams. Documents and review exports are not automatically uploaded to an LLM.

The Android builder writes new output through the document picker or adds a newly named database to DataEater. The reader/search/chat path reads its input database; it does not rewrite it. See [Android builder](ANDROID_BUILDER.md) and the independent builder guide.

## Validation and remaining gaps

JVM tests cover retrieval, answer policy, context budgets, memory isolation, session state, storage failures, archive limits, crypto interoperability, model file safety and builder logic. Python regression suites cover the independent builder. Fixed Python/Kotlin cryptographic vectors catch cross-language disagreements rather than merely testing each implementation against itself.

Use `./gradlew testResearchUnitTest` for current JVM results. Historical benchmark records describe their specific device, version and test data; they do not establish performance on every Android device. Physical-device checks remain necessary for keystore support, native inference, PDF extraction, permissions, layouts and document-provider behavior.

Remaining limitations include lexical retrieval, model grounding drift, whole-database memory use, broad storage access for the shared model folder, unsigned manifests and limited device coverage. See [Roadmap](ROADMAP.md).
