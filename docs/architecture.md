# Architecture overview

DataEater is an Android application with three visible responsibilities:

1. Manage local conversations, models and imported document databases.
2. Consult the selected database when document-backed answers are requested.
3. Run a compatible language model locally or use optional online AI, and present an answer with available source references.

```mermaid
flowchart LR
    U[Question on Android] --> A[DataEater]
    D[Local technical database] --> A
    M[Local AI model] --> A
    A <--> O[Optional OpenRouter provider]
    A --> R[Answer and source references]
```

Local chat inference, document lookup and on-device database creation use local files. Model downloads are an optional network operation. Online mode sends selected messages through OpenRouter; document excerpts are included only when sharing is enabled. Encrypted databases can require creator-issued access codes bound to a device.

This is a product-level overview. The implementation, file-format internals, database construction, retrieval/ranking, prompts and optimization details remain private. An APK necessarily contains executable implementation and can be inspected; unpublished source is not a guarantee against reverse engineering.
