# The `.dataeater` database format — version 1

## This format is meant for other people

The point of `.dataeater` is to be a **common container** — one file that anyone can fill
with knowledge and anyone else can read, on any phone, with no network and no account.

So this document is written to be usable by somebody who has never heard of DataEater.
Build one with the open-source `dataeater-builder`, or write your own; the spec below is
everything you need, and the file is an ordinary ZIP you can read in a text editor.

What a database can contain is not limited by this app. Workshop manuals, board pinouts,
man pages, recipes, commentaries, out-of-copyright books, a firm's internal service
history — if it is text, it goes in. See
[D-013](DECISIONS.md#d-013--dataeater-is-a-published-container-not-an-internal-format).

---

## What a `.dataeater` file actually is

A `.dataeater` file **is a ZIP archive** with a fixed layout inside.

This is a deliberate choice. It means:

* any database can be opened with `unzip` and read in a text editor
* no proprietary tool is required, now or in ten years
* adding encryption later does not change the outer container

We do **not** rely on any ZIP feature that is weak or non-standard. In particular we do
not use ZIP encryption, and we do not trust ZIP metadata for integrity. Those are handled
by our own fields (see *Integrity* below).

---

## Layout

An **open** database:

```
demo.dataeater
│
├── manifest.json      ← ALWAYS FIRST. Plain text. Never encrypted.
├── sources.json       ← which documents the text came from
└── chunks.jsonl       ← the text itself, one JSON object per line
```

A **locked** database — same container, different contents:

```
paid-manual.dataeater
│
├── manifest.json      ← the SAME, plus "encryption", "payload_nonce",
│                        and the creator's public contact details
└── payload.enc        ← sources.json + chunks.jsonl, encrypted together
                         as ONE AES-256-GCM blob
```

Future versions may add `index.sqlite`, `embeddings.bin` or `signature.bin`. Older apps
ignore files they do not know.

### Why the whole payload is one encrypted file

`sources.json` and `chunks.jsonl` are encrypted **together**, not separately.

Encrypting them separately would mean two nonces, two authentication tags and two chances
for a mismatch. One blob has one nonce and one tag, so either both files arrived intact or
neither did. It also means the reader only needs one decryption step.

After decryption the bytes are exactly the two plain files above, so the app reuses its
ordinary parsing code. There is only ever one set of parsing rules to keep working.

### Ordering rule

`manifest.json` must be the **first** entry in the archive. The app reads it first and,
if it decides the database is not supported, stops immediately without reading the rest.
This is why the reader can be a cheap streaming pass rather than a full load.

---

## `manifest.json`

The contract. It says what the database is, and it is the first thing read.

```json
{
  "format": "dataeater",
  "format_version": 1,
  "database_id": "demo",
  "name": "Demo Technical Knowledge Base",
  "description": "Small synthetic knowledge base about a fictional diesel fuel injection system.",
  "language": "en",
  "created_utc": "2026-10-04T18:53:19Z",
  "builder_version": "0.1.0",
  "license": "CC0-1.0",
  "encryption": "none",
  "counts": {
    "documents": 2,
    "chunks": 21
  },
  "files": {
    "chunks.jsonl": { "sha256": "sha256:1d7d13fd...", "records": 21 },
    "sources.json":  { "sha256": "sha256:e674ece8..." }
  }
}
```

| Field | Meaning |
|---|---|
| `format` | Must be `"dataeater"`. A different value means this is not our file. |
| `format_version` | **The version switch.** See *Versioning* below. |
| `database_id` | Short stable identifier, for example `demo`. |
| `name` | Shown to the user. |
| `description` | One or two sentences. |
| `language` | Main language of the text, for example `en`. |
| `created_utc` | ISO-8601 timestamp. |
| `builder_version` | Which builder produced it. |
| `license` | Content licence, for example `CC0-1.0`. |
| `encryption` | `"none"` for an open database, `"aes-256-gcm"` for a locked one. The field has existed since day one, so adding encryption did not break the format. |
| `payload_nonce` | Locked databases only. The AES-GCM nonce, base64. Never encrypted — the app needs it before it has any key. |
| `creator` | Locked databases only, optional. Who to ask for an Unlock Code. |
| `contact` | Locked databases only, optional. Usually an email address. |
| `creator_public_key` | Locked databases only, optional. The creator's ECDSA P-256 **public** key, so the app can check that an Unlock Code really came from this creator. Public keys are safe to publish — that is what they are for. |
| `counts` | Lets the app show "21 chunks" without decrypting anything. |
| `files` | Fingerprint of every file inside. See *Integrity*. |

### A locked manifest, in full

```json
{
  "format": "dataeater",
  "format_version": 1,
  "database_id": "hf4500-v2",
  "name": "HF-4500 Service Manual",
  "description": "Fuel injection system, 2026 edition",
  "encryption": "aes-256-gcm",
  "payload_nonce": "bBS/eSPg645lMzEk",
  "creator": "Northgate Technical Publishing",
  "contact": "licences@northgate.example",
  "creator_public_key": "MCowBQYDK2VwAyEABT10elTfT5weCVJPjP4gFWXuDq...",
  "counts": { "documents": 2, "chunks": 944 },
  "files": {
    "payload.enc": { "sha256": "sha256:b1b7478d...", "bytes": 7504 }
  }
}
```

Note what is **not** here: no content key, no licence, no expiry. All three belong to the
Unlock Code, which the customer receives separately. That is deliberate — the database file
can be copied around freely without granting access.

### Versioning — the important one

`DatabaseReader` refuses to open a database whose `format_version` is **newer** than the
app supports:

```kotlin
if (version > SUPPORTED_FORMAT_VERSION) {
    throw DataEaterException("This database uses format version $version. …")
}
```

It deliberately does *not* try to read an unknown future version. Guessing would risk
showing wrong facts with a confident-looking citation — the worst possible failure for a
diagnostic tool. Refusing is always the safer behaviour.

---

## `chunks.jsonl` — the text

One JSON object per line (JSON Lines). Chosen so the app can read line by line and never
needs to hold a whole large database in memory.

```json
{"id":"c003","source_id":"s1","section":"2.1 Routine Service","page":7,"lang":"en","text":"Replace the primary fuel filter every 30,000 km or every 12 months…"}
```

| Field | Purpose |
|---|---|
| `id` | Unique inside the database, e.g. `c003`. |
| `source_id` | Which document this came from. Matches an `id` in `sources.json`. |
| `section` | Chapter or heading. |
| `page` | Page in the original document. `0` if the source has no pages. |
| `lang` | Language of **this** text, so a future mixed-language database still works. |
| `text` | The content itself. |

> **`section` and `page` are not decoration.** They are what makes DataEater honest.
> When the app prints "page 11", it is reading a number that the *builder* recorded when
> it processed the original document. The app never invents a page number.

---

## `sources.json`

The documents themselves.

```json
[
  {
    "id": "s1",
    "title": "HF-4500 Fuel System - Service Manual (DEMO)",
    "publisher": "DataEater Project",
    "language": "en",
    "license": "CC0-1.0",
    "year": 2026
  }
]
```

---

## Integrity — how tampering is detected

Every file listed in `manifest.files` carries a SHA-256 fingerprint. The builder computes
them when it writes the file, and has a `verify` step.

* **Mismatch** → the file was modified after it was built.
* **This is detection, not protection.** A determined attacker can edit both the file and
  the manifest. Preventing that requires a signature over the manifest, which is designed
  in [SECURITY.md](SECURITY.md) and not implemented yet.
* **The app checks required knowledge files before opening them.** `sources.json`
  and `chunks.jsonl` are checked against their exact bytes before parsing; `payload.enc`
  is checked before decryption. A mismatch or missing/malformed fingerprint refuses
  opening with a visible error. A remembered licence does not hide an integrity error.
* The fingerprints must use `sha256:` followed by 64 lowercase hexadecimal digits,
  as emitted by the builder. Databases missing required fingerprints must be rebuilt.
* This check covers the files used for answers and citations. Unknown optional ZIP
  entries are ignored; their hashes are not checked by the app. Android still counts
  their decompressed bytes when scanning past them: 32 MiB per entry, 64 MiB per scan,
  and at most 10,000 entries. These limits bound reading work, not JSON object memory.

Verify it yourself in ten seconds:

```bash
mkdir /tmp/check && cd /tmp/check && unzip -o demo.dataeater > /dev/null
sed -i 's/380 bar/999 bar/' chunks.jsonl
sha256sum chunks.jsonl          # this no longer matches manifest.json
```

For a locked database, the fingerprint covers `payload.enc`, so the same trick works: edit
one byte of the ciphertext and the hash no longer matches.

---

## How the app tells an open database from a locked one

It reads **only** `manifest.json`, which is never encrypted, and looks at the
`encryption` field. It never tries to open the payload to find out.

| `encryption` | What the app does |
|---|---|
| absent, empty, or `none` | open it normally |
| anything else | treat it as locked, and show who to contact |

This distinction matters. A locked database has no `chunks.jsonl` at all, so a reader that
just went looking would report *"the file is missing, the database is damaged"*. That is
false — the database is encrypted on purpose and is perfectly intact. Telling a customer
their purchase is broken is worse than useless.

Deciding this is one small pure function, `DatabaseLock.statusOf`, so it can be tested on
a computer with no phone involved.

---

## Design rules we commit to

1. A `.dataeater` file is a ZIP, but only as a container. No ZIP crypto, no ZIP signatures.
2. `manifest.json` is always first and always plain text.
3. Every file is listed in the manifest with a SHA-256.
4. `format_version` is checked before anything else, and newer versions are refused.
5. One record per line, so reading can stream.
6. IDs are strings, so databases can later be merged without renumbering.
7. **No key material ever goes in the database file.** The creator's private key, the
   content key and the expiry all live in the Unlock Code, not in the `.dataeater`.

---

## Status

| Part | State |
|---|---|
| Reading, searching, provenance | done |
| Integrity hashes | done |
| Locked database layout | done |
| AES-256-GCM payload encryption | done |
| ECDSA P-256 signature over licences | done |
| Device-bound key wrapping | done |
| App opens a locked database once a licence is entered | done, checked on a phone |
| Manifest signature (proving who built the file) | designed, not built |
| Reading the format needs DataEater | **never** — see rule 9 |
| Indexing for very large databases | planned — the demo has 21 chunks and does not need one |
