# Security

## The honest limitation, stated first

**A local app that can decrypt a database cannot make that data impossible to
extract.**

If the app can read it and display it, a determined person can dump it from memory or
patch the app. We will never claim "unhackable" or "impossible to extract". Any vendor who
tells you otherwise about an app that runs on the customer's own phone is misleading you.

What we *can* realistically achieve:

| Goal | Realistic? |
|---|---|
| Stop casual copying of a database file | yes |
| Stop an unauthorised user opening the database | yes |
| Verify an access code against the database's declared creator key | yes |
| Establish publisher identity independently of the file | not yet; manifests are unsigned |
| Detect changed data relative to the manifest hashes | yes; this does not authenticate the manifest |
| Make unauthorised redistribution harder | partly |
| Make extraction from a rooted phone impossible | **no, and we will not pretend otherwise** |

---

## What is implemented today

| Control | State |
|---|---|
| Network permission | explicit model downloads and optional OpenRouter chat; local inference, retrieval, building and licensing have no network calls |
| No telemetry, no analytics, no crash reporting | done |
| Database format version check, refusing anything newer | done, tested |
| SHA-256 file fingerprints (change *detection*) | required knowledge files checked before opening; mismatches visibly refused |
| Grounded prompting and visible answer modes | Strict/Balanced/Flexible instructions and database-off labels; model compliance is not guaranteed |
| AES-256-GCM encryption of the database payload | done, builder and app |
| ECDSA P-256 signature over every licence field | done, tested |
| Licences bound to one device's hardware key | done, tested |
| Expiry enforced | done, fails closed |
| Locked databases recognised and reported honestly | done, tested |
| Private keys, passwords or API secrets | must stay outside version control; runtime secrets are private app data or explicit private exports |
| App accepts and verifies an Unlock Code | implemented, tested |

---

## Cryptography actually used

The licensing protocol uses established primitives through Android's platform
cryptography and Python's `cryptography` package. PDF extraction brings additional
transitive dependencies into the APK; it does not replace the platform licensing code.

| Purpose | Algorithm | Standard | Where |
|---|---|---|---|
| Authenticate a licence against its declared creator key | ECDSA P-256, SHA-256 | FIPS 186-4 | creator signs, app verifies |
| Bind a licence to one device | ECDH on curve P-256 | NIST SP 800-56A | creator wraps, phone unwraps |
| Derive the per-device wrapping key | HKDF-SHA256 | RFC 5869 | both sides |
| Encrypt the knowledge payload | AES-256-GCM | NIST SP 800-38D | both sides |
| Fingerprint files | SHA-256 | FIPS 180-4 | builder and app verify |

> **Correction from an earlier version of this document.** It previously said X25519 for the
> key agreement and "Tink, or BouncyCastle" for the library. Neither is what was built.
> The agreed design is **ECDH P-256**, chosen after a spike verified it works inside
> `AndroidKeyStore` on the target phone with a hardware-backed key. The library is
> **`javax.crypto`**, already in Android, so the app adds no dependency. See
> [ENCRYPTION_DESIGN.md](ENCRYPTION_DESIGN.md).

### The agreed limits

Two things are true and are stated rather than hidden:

* **The Unlock Code is shareable.** A licence works on the phone it was made for. A user
  who is allowed to read the database can also copy the code to someone else who can access that same device’s private key. Another phone of the same model has a different key and cannot use the licence. Device binding is not protection against extraction by an authorized reader.
* **Expiry can be bypassed by patching the app.** The date is inside the signed payload, so
  it cannot be edited without breaking the signature — but the *check* happens in code on
  the customer's device, and code on their device can be changed. Only the signature
  itself is unforgeable.

---

## How a licence works

```
Creator's computer                        Customer's phone
──────────────────                        ─────────────────
create-key  → ECDSA P-256 key pair
                │
                │  public key goes into the database manifest (public, safe to publish)
                │
build   → encrypt → a locked .dataeater file
                │
                │  file is emailed or sold
                ▼
                              app opens it, reads the manifest
                              shows: what it is, who to ask
                              "This database is locked"
                              │
       ┌──────────────────────┘
       │  customer sends the Request Code
       ▼  (the phone's P-256 public key - it cannot open anything by itself)
licence  ────────────────────────→  paste the Unlock Code
  · wraps the content key for          │
    that one public key                │
  · signs every field, incl. expiry     │
  · sets an expiry date                │
                              app verifies the signature
                              checks it is not expired
                              recovers the content key (ECDH + HKDF)
                              decrypts the payload
                              opens the database
```

**The creator's private key must never exist in:** the APK, the git repository, a database
file, the customer's device, or any public documentation.

### What we will never do

* a shared secret such as `LICENSE=123456`
* an invented algorithm
* a licence server requirement — the app must work fully offline
* shipping any private key in the repository

---

## Threat model

| Threat | Defence |
|---|---|
| Customer copies the `.dataeater` file to another phone | the payload is encrypted; without a licence for *that* phone it cannot be read |
| Attacker reverse-engineers the APK | it contains only **public** keys and no secret |
| Attacker patches the APK to skip the licence check | signatures cover every field including expiry, so a licence cannot be edited and re-issued. A patched app can *ignore* a check, but cannot forge one |
| Attacker replays a stolen Unlock Code on another phone | the content key is wrapped to a device public key, so the shared secret differs and the AES check fails |
| Attacker extends an expiry date | the expiry is covered by the signature, so editing it breaks verification |
| Attacker issues their own licence | it will not verify against the creator's public key |
| Attacker edits the database text | required knowledge files are checked against manifest hashes before use; an attacker replacing both text and hashes can still produce a matching plain database because manifests are unsigned |
| Creator's private key leaks | outside our control. Documented as a creator responsibility; keep an offline backup |
| Malicious `.dataeater` package | no code is ever executed; it is parsed as data only |
| Zip bomb | Android reads and discards entries with a 32 MiB per-entry limit, 64 MiB decompressed limit per scan and 10,000-entry limit; whole-file parsing still needs memory headroom |

---

## The one bug the cross-language tests caught

Worth recording, because it is exactly the kind of failure that is invisible until a
customer tries to use a licence they paid for.

The Kotlin app and the Python builder each compute the bytes that a licence signature
covers. Both deliberately sort the fields so the order they were written in does not
matter.

The app sorted the keys — and then wrote them into a fresh JSON object, assuming it would
keep that order. **It does not.** `JSONObject` stores fields in a hash map, so they came
back out in a different order. Measured: `{"zzz":1,"aaa":2,"mmm":3}` came out as
`{"aaa":2,"zzz":1,"mmm":3}`.

So for the *same* licence, the app and the builder produced **different bytes**, and every
signature would have failed. Nothing crashed. No error message. A customer simply could
never open what they bought.

Neither side's own unit tests would have caught this: each side was perfectly consistent
with itself. It was caught by pinning known answers produced by the Python side and
checking the Kotlin side against them.

The rule taken from this: **when two languages implement the same maths, test them against
each other, not only against themselves.** The tooling lives in
`tools/tests/make_crypto_vectors.py` and `CryptoCompatibilityTest.kt`.

---

## The "all files access" permission — a deliberate trade-off

`MANAGE_EXTERNAL_STORAGE` lets the app read `/sdcard/DataEater/`, where model files live.

**Why:** Android's private folder (`/sdcard/Android/data/...`) is hidden in most file
managers, so a user cannot conveniently copy a model in or see which models they have. A
visible folder is far more usable.

**The cost:** it is a broad permission. Android classifies it as special, and Google Play
restricts it to file managers, backup and antivirus apps.

**Honest assessment:** it is reasonable for what DataEater does — a knowledge tool must be
able to read whole files — and it is declared openly rather than worked around. But it
does weaken the "no permissions" story, and it would block a Play Store release.

| Option | Permission | Notes |
|---|---|---|
| **A. `/sdcard/DataEater` (current)** | all files access | usable and visible; **would block Google Play** |
| B. SAF file picker | none | user picks the folder once; the permission-free choice |
| C. app-private folder | none | invisible and awkward |

If DataEater is ever published on Google Play, option B becomes mandatory.

---

## Rules for the repository

| Never commit | |
|---|---|
| Private keys | any file, any format, ever |
| Licence secrets | |
| Cloud credentials or API tokens | |
| Copyrighted manuals | without permission |
| Real production diagnostic databases | |
| AI model files | `.litertlm` — they are large and separately downloaded |

`.gitignore` covers model files, keys and Python caches.

The demo database is **synthetic**. The HF-4500 engine, its part numbers and its fault
codes were invented for this project and released under CC0-1.0, so it can be shared.

---

## Privacy guarantees

* **No telemetry.** Nothing is measured about the user.
* **On-device use needs no account or payment.** Optional OpenRouter requires your API key and may use paid credits.
* **Explicit model downloads.** Android DownloadManager requests pinned public model URLs;
  no question, document, licence, device identifier or account token is attached.
  Downloads use HTTPS and files are checked against pinned sizes and SHA-256 before
  installation. On-device inference, retrieval, database building and licensing have no network calls.
  This is a code-level boundary, not Android's previous permission-level prohibition.
  `DataEater_offline` retains the original APK without INTERNET permission.
* **Optional online AI sends selected content.** Choosing OpenRouter sends the current question and scoped online chat memory. Database sharing is off by default; enabling it allows retrieved excerpts, including decrypted text, to leave the phone. Local chat history is not replayed to OpenRouter. See [online privacy](OPENROUTER.md).
* **Documents remain local during on-device use and database building.** Exported files and explicitly shared online excerpts are under the user's control.
* Network functionality must remain explicit, optional and visible.

## System backup and logging

Android cloud backup is disabled, with explicit exclusions for legacy backups and Android 12+ cloud/device transfer. Chats, device-bound licences and persisted download IDs must not be copied through system backups. Uninstalling removes private chat data and the keystore key; existing licences may require reissue on reinstall. Model responses are not written to application logs, and native verbose logging is not enabled in normal runs. See https://developer.android.com/identity/data/autobackup for the platform rules.

Encrypted database licensing requires Android 12+ for AndroidKeyStore ECDH key agreement. Older supported Android versions can use unencrypted databases and general chat, subject to local model support. No insecure exported-key fallback is used.

## OpenRouter online option (1.3.0)

Local inference remains default. Explicit online selection sends scoped online messages and, only with a separate sharing opt-in, retrieved database excerpts. Unlocked encrypted database text is plaintext when sent; its access licence does not authorize cloud disclosure. The API secret uses Android Keystore AES-GCM and excluded private preferences. HTTPS origin is fixed, redirects are rejected, response errors are sanitized, and request text/headers are never logged. No automatic retries or local/online fallback. See OPENROUTER.md.
