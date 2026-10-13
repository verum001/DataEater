# Database and licence security audit — 13 October 2026

Scope: Android database opening, encrypted creation, device keys, licences and
licence storage; the independent Python builder and CLI; related tests and
security documentation. Fixes are in Android 0.1.7.3/code16 and Builder 0.4.1.
This is a code review with synthetic regression and interoperability checks,
not a penetration-test certification or a claim that every Android build is secure.

## Findings and priorities

| Priority | Confirmed finding | Preconditions and impact | Resolution |
|---|---|---|---|
| Medium | ID check skipped missing/empty IDs and ignored case | A genuinely signed unbound code, or an ambiguous/modified manifest, could pass the logical database check. Opening different encrypted data also requires the same content key; unrelated random keys still fail GCM. | Require exact, nonempty, typed IDs. Reject ambiguous IDs and formats. New codes additionally bind to encrypted package identity. |
| Medium | Python inspect reread files without limits after verification errors; licence issuance read manifests without the guard | Opening hostile input in these CLI commands could exhaust memory despite the existing verifier detecting oversize data. | Consumers use one bounded, verified package scan and stop on invalid input. |
| Medium | Deep JSON and oversized external licence previews could exhaust resources | Untrusted database/licence files could reach recursive JSON parsing or an uncapped read without a valid signature. | Bound JSON depth/structure, reject duplicate fields, and cap licence files/code parsing before decoding. |
| Medium | Encryption accepted input with stale hashes; Python issuance did not prove supplied keys matched the database | Damaged/changed plaintext could be repackaged as valid encrypted data; wrong signing/content keys produced unusable licences. | Required hashes checked before encryption. Issuance proves the content key decrypts the payload and the signing key matches the manifest. |
| Low / conditional | UTF-8 replacement could preserve a signature after replacing `?` with an unpaired surrogate | Requires an affected signed string and crafted invalid Unicode. This is a canonicalization ambiguity, not a break of ECDSA. | Strict UTF-8 output/ID validation. Preserve parsed numbers and sort Unicode keys by code point for interoperability. |
| Low | ZIP duplicates were not consistently rejected; Android scans stopped at the requested entry | Different ZIP consumers could select different duplicate entries. Trailing unknown entries were ignored; no direct Android memory attack from an ignored suffix alone was demonstrated. | Reject duplicate entries and enforce bounds across the whole scan, including skipped directories/files. |
| Low | Creator curve validation cast the key itself to parameter specification, so its P-256 test never ran | Unsupported EC creator keys could be accepted by the signature verifier despite the specified protocol. No arbitrary signature forgery was demonstrated. | Compare actual curve, generator, order/cofactor and point validity; validate ephemeral keys too. |
| Low | Sanitized licence IDs collided; same IDs across publishers overwrote remembered codes | Primarily availability/renewal problems. Signatures and GCM still prevented a wrong key from decrypting unrelated content. | Atomic private writes, bounded reads and hashed ID/publisher storage names; legacy names remain read-only fallbacks and are fully reverified. |
| Low | Python secret/key files were chmodded only after writing | A permissive umask allowed a brief readable-file window; following an existing symlink could overwrite another file. | Create mode0600 staging files, flush and atomically replace destinations without following their symlinks. |
| Compatibility | Python declared cryptography >=42 while using an API introduced in43 | Licence signing could fail on an allowed dependency version. | Minimum requirement is43; no algorithm change or new dependency. |

Two important limitations remain: publisher identity is not authenticated by a
manifest alone, and offline expiry is not protected against a user controlling
the clock/app. Neither is described as a cryptographic break or silently solved
with an internet requirement.

## End-to-end implementation

1. Python `write_database` and Android `BuilderCore.writeDatabase` serialize
   source/passages with IDs and page references. The public manifest contains
   required SHA-256 fingerprints. These detect consistency relative to that
   manifest, not an independent publisher signature.
2. Encryption validates the plain input, chooses a random 32-byte content key
   unless deliberate reuse is selected, and creates a fresh random 12-byte nonce.
   AES-256-GCM encrypts a version1 JSON envelope containing sources/passages with
   a128-bit tag. The readable manifest records the nonce, creator public key and
   ciphertext fingerprint; the content key remains a private creator secret.
3. The reader's device ECDH P-256 key is generated/stored in Android Keystore.
   The Request Code is its public X.509 DER key in base64. Hardware backing is
   used when available; the app does not export the device private key.
4. Issuance verifies that the supplied publisher key matches the database and
   that the supplied content key decrypts its payload. A fresh ephemeral P-256
   key performs ECDH with the request public key. HKDF-SHA256 derives the 32-byte
   wrapping key with the existing zero salt and `DataEater licence v1` context.
   A fresh 12-byte nonce and AES-256-GCM wrap the content key. Separate publisher
   signing keys and ephemeral/device agreement keys retain their distinct roles.
5. ECDSA P-256/SHA-256 signs the canonical UTF-8 licence fields, including expiry,
   database ID, wrap and the new optional `database_fingerprint`. Editing signed
   fields fails verification; invalid Unicode, ambiguous fields, oversized input
   and unsupported cryptographic lengths/curves are refused.
6. Pasted codes, imported licence files, remembered licences, multi-database
   collections and benchmarks converge at `DatabaseReader.openLicensed`.
   It reads the current manifest, verifies signature, expiry and binding, then
   touches the device private key. Opening compares the identity again, checks
   the exact ciphertext bytes against the manifest hash and verifies the GCM tag.
   The payload version/shape, source/passages IDs and counts are validated. The
   recovered byte-array key is cleared after use; this is best effort, not a
   guarantee that all JVM/native copies have been erased.
7. Remembered codes store the wrapped licence, not a bare content key, in private
   app storage. Publisher secrets use the existing Keystore-backed encrypted vault.
   Android system backup/device transfer remains disabled. Exported publisher key
   backups are intentionally plaintext secrets and must be protected by their owner.

AES-GCM, ECDH P-256, HKDF-SHA256 and ECDSA are retained. No nonce reuse was found
in the reviewed creation paths: payload/wrap nonces are generated afresh, and the
vault uses the provider's generated IV. Repeated key reuse still needs sensible
usage limits and should not be the default for unrelated databases. The existing
HKDF extract/expand matches [RFC5869](https://www.rfc-editor.org/rfc/rfc5869.html);
output bounds are enforced. The96-bit nonce policy follows
[NIST SP800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final).

## Binding and compatibility

`database_id` is an exact, case-sensitive string: no missing/null/empty values,
outer whitespace, control characters, invalid Unicode, path separators or `.` /
`..`; maximum256 UTF-16 units. Builders generate ordinary ASCII slugs by default.
Malformed/unsupported versions and encryption identifiers are rejected; historical
plain `none` capitalization/empty aliases remain supported.

New signed `database_fingerprint` is SHA-256 of canonical fields: exact database
ID, format version, encryption scheme, canonical creator DER public-key base64,
nonce base64 and ciphertext SHA-256. It binds the licence to the encrypted
artifact, not the outer ZIP's compression/timestamps. Display name, description,
contact and other readable claims remain unsigned and are not publisher proof.
Changing a nonce, publisher key, ciphertext or logical ID changes the identity.
Consumers verify the same identity while opening rather than trusting an earlier
header inspection.

Existing valid version1 licences without this field remain accepted when their
ID, signature, expiry, device wrapping and GCM tag pass. Legacy licences bind to
an ID/key scope, not a unique encrypted revision: deliberately reusing both ID
and content key can permit an old licence to open a later revision. Assign distinct
IDs and random keys to unrelated databases; reissue licences to obtain strict
artifact binding. Signed empty-ID or mismatched-case codes need corrected reissue.
Older app versions can verify signatures on new version1 codes but do not enforce
the fingerprint; use0.1.7.3 or newer for the added binding check.

## Publisher identity and recommended trust model

Three separate checks must not be conflated:

- **Signature validity:** the code was signed by the private key matching the
  manifest's supplied public key.
- **Package integrity/confidentiality:** hashes agree with the manifest, and GCM
  authenticates decrypted bytes under the recovered content key.
- **Trusted publisher identity:** someone independently confirmed that the key
  or artifact belongs to the expected publisher. This is not currently provided.

An attacker who replaces the manifest/key and issues a code using their own key
can produce a valid-looking replacement package containing their own material.
They cannot decrypt unchanged original ciphertext without its content key: merely
replacing the public key and wrapping an unrelated key fails GCM. Replacing plain
content and recalculating unsigned hashes can also pass integrity checks. Therefore
publisher names/contact strings and a successful unlock are not authenticity badges.

Practical immediate model: obtain the database SHA-256 and publisher DER-key
SHA-256 fingerprint from an independently trusted channel, then compare them before
use. A known repository/website can be a distribution trust source, with its own
account-compromise risks. A future optional local pin/TOFU store can remember a
manually verified publisher key and warn on rotation; first-use TOFU alone does not
prove identity. No centralized CA, registry or mandatory network service is added.

## Offline expiration

The signed absolute expiry is checked against `LocalDate.now()` when the database
is opened, including remembered codes. Changing the signed date fails the
signature, but setting the device date backwards can make an expired code pass.
The check is not a continuous revocation service: an already opened database can
remain in memory, and previously copied/exported answers cannot be revoked.
Timezone changes near midnight can also affect a local-date decision.

A completely offline app on a customer-controlled device cannot obtain guaranteed
current civil time or enforce DRM against a patched/rooted client. Realistic
optional mitigations are a protected maximum-observed-date record, elapsed realtime
within a boot/session, rechecks on resume/long sessions, and an optional publisher
signed time anchor when a connection is voluntarily available. These catch some
ordinary rollback attempts, but need recovery for incorrect future clocks and
cannot defeat all reset/replay/patch attacks. No mandatory internet access or silent
clock lockout is introduced. Time-limited commercial licences must state this limit.

## ZIP, JSON and integrity boundaries

The existing counted-stream limits remain32 MiB per expanded entry,64 MiB per full
scan and10,000 entries. Compressed package size is capped at128 MiB; source/passage
counts are capped at10,000/100,000. Python permits only Android-compatible stored/deflated ZIP entries; ZIP-level
password encryption is unsupported. Required plain files and encrypted payloads need
valid SHA-256 records. Duplicate ZIP/JSON fields, malformed manifests, unsupported
schemes and invalid payload versions are refused. Unknown files are data only:
they are bounded/scanned, never executed or extracted into arbitrary paths.

Large inputs can still consume substantial memory inside these limits. Python's
standard ZIP parser loads central-directory metadata before the entry-count check;
the physical-size cap bounds but does not eliminate that cost. General streaming
parsing and broader fuzzing remain useful follow-up work. Hashes in an unsigned
manifest cannot detect wholesale malicious replacement with recalculated hashes.

## Privacy and device compatibility

No new logging of codes, private keys, plaintext passages or decrypted payloads
is introduced. DeviceKeys diagnostics name providers, not secrets. Android licence
storage/vault/cache are private and excluded from backup, while selected exports,
chat answers and benchmark reports may contain plaintext readable data. Optional
OpenRouter can receive decrypted excerpts only with its separate sharing opt-in;
this audit does not turn that feature on or redefine licensing as cloud consent.

Keystore key agreement requires Android12/API31, as documented by
[KeyProperties.PURPOSE_AGREE_KEY](https://developer.android.com/reference/android/security/keystore/KeyProperties#PURPOSE_AGREE_KEY).
The obsolete Android13 gate on the ECDSA-only helper is removed. Older devices can
still use plain databases; there is no exported-device-key fallback. Python's
minimum cryptography version is43 for the existing deterministic ECDSA API, per
[its documentation](https://cryptography.io/en/43.0.3/hazmat/primitives/asymmetric/ec/).

## Validation

337 Android JVM tests and all11 Python test suites passed. Release compilation
and the final Android lint check are recorded with the tested artifacts.


Regression coverage includes signed empty/wrong/case-mismatched IDs, modified
fingerprints, legacy compatibility, invalid Unicode, P-384 rejection, malformed
GCM/wrap lengths, HKDF output bounds, duplicate/deep JSON, duplicate ZIP entries,
limits before and after required entries, capped licence reads, collision-resistant
storage names, wrong issuer/content keys, atomic private files and symlink safety.
Synthetic Python-produced encrypted packages and new/legacy licences are verified,
unwrapped and opened by the actual Kotlin reader. The clock-rollback test explicitly
demonstrates the remaining limitation instead of claiming to prevent it.

The signed0.1.7.3/code16 update was installed and checksum-verified on the vivo
V2453A/API36. The focused real Keystore test passed: new and legacy licences opened,
a new code failed against a re-encrypted same-ID/same-key revision, empty IDs and
expired codes were refused, private licence storage round-tripped and the device
key remained unchanged. The device key reported hardware backing. Temporary
fixtures/test package were removed; existing chats, keys and research packages
were preserved. The APK uses the existing production certificate. Android lint
reported zero errors. Final automated and phone records accompany the artifacts. Broader
Android12/other-vendor tests, hostile-provider testing, full fuzzing, OS/root resistance
and source/answer accuracy are outside this audit's demonstrated coverage.
