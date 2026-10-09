# Encryption and device access codes

The format-v1 protection and licensing workflow is implemented in the Android app and the independent Linux builder. Android encrypted-database unlocking requires Android 12 or newer for AndroidKeyStore ECDH. Older supported devices can use plain databases, subject to local model support.

The goal is to protect a distributed database from casual unauthorized opening and issue access codes for individual phones without an online licence server. An authorized reader can extract decrypted content; patched applications can bypass local policy checks.

## Workflow

1. Create a creator signing key pair once.
2. Build a plain database, then encrypt its knowledge payload with a fresh random content key. Keep the content-key secret file and signing key private and backed up.
3. Distribute the protected database. Its manifest remains readable so the app can show its title, creator/contact details and declared creator public key before unlocking.
4. The phone creates a P-256 key pair in AndroidKeyStore. Its Request Code carries the public key; the private key remains in the keystore. The same Request Code can be used for multiple databases.
5. The creator issues a signed Unlock Code containing the content key wrapped for that phone, the database identifier and the expiry.
6. The app parses the code, verifies its signature against the manifest's declared creator key, checks expiry and database identity, then uses the device key to unwrap the content key and decrypt the payload.
7. A remembered code is rechecked when reopening the protected database. Uninstalling removes private app data and device keys, so codes may need reissue.

## Algorithms and keys

| Purpose | Implemented choice |
|---|---|
| Database encryption | AES-256-GCM with a random 32-byte content key and fresh nonce |
| Device key agreement | ECDH P-256, with a new ephemeral creator key for each wrapping operation |
| Wrapping-key derivation | HKDF-SHA256, 16 zero salt bytes, UTF-8 info `DataEater licence v1` |
| Wrapped content key | AES-256-GCM under the derived wrapping key |
| Creator signature | ECDSA P-256 with SHA-256, DER-encoded signature |
| File fingerprints | SHA-256 over exact stored bytes |

Ed25519 was considered in an earlier design and replaced following device compatibility checks. It is not the shipped signing algorithm. No RSA wrapping fallback is implemented.

The content key protects one database's payload. The creator signing key authenticates access codes. The device key binds wrapping to a particular phone. They serve different purposes and must not be substituted for one another.

## Container and code formats

A plain `.dataeater` ZIP contains `manifest.json`, `sources.json` and `chunks.jsonl`. A protected ZIP contains the readable manifest and `payload.enc`; the original knowledge files are not present in plaintext. The encrypted payload is a JSON envelope containing the source and chunk text. The manifest supplies encryption metadata, the payload nonce and ciphertext fingerprint.

An Unlock Code is base64-encoded licence JSON with format `dataeater-licence`, version 1. Its `wrap` object contains the ephemeral public key, nonce and wrapped key. The licence also carries its database identifier and expiry. The signature covers every field except `signature`, using the same deterministic sorted JSON encoding on Python and Kotlin. Signing bytes must not be produced by relying on `JSONObject` field iteration order.

The expiry belongs to the signed licence, not the encrypted database payload. Default expiry is `never`; the CLI supports day/year durations and explicit dates. Different customers can receive different expiry dates for the same encrypted file without re-encrypting it.

See [Database format](DATABASE_FORMAT.md) for the container fields and [Builder](BUILDER.md) for commands. The protocol implementation is in `DeviceKeys.kt`, `UnlockCode.kt` and the independent builder's `packaging.py`.

## Secret storage

| Material | Storage and handling |
|---|---|
| Device private key | AndroidKeyStore; not exported by the app |
| Device public key / Request Code | Public; safe to send to the creator |
| Creator private signing key | Private creator file on Linux, or encrypted publisher storage in the Android builder; export backups explicitly and keep them private |
| Creator public key | Readable database manifest |
| Database content key | Private creator secret file; Android publisher material is handled by the builder's encrypted vault |
| Accepted Unlock Code | Private app storage; contains wrapped key material and should be treated as sensitive |
| Decrypted document text | App memory after unlocking; may be included in online requests only with explicit document-sharing consent |

Publisher keys can be created on a phone using the Android builder. They are creator secrets, separate from the customer's non-exportable device key. Exported publisher backups need protection. The application binary does not embed customer or publisher private keys.

## Trust and limits

- The licence signature verifies against the key supplied by the database. It does not independently establish the publisher's real-world identity. There is no signed manifest or trusted publisher catalogue.
- File hashes detect changes relative to a manifest. A matching plain package can be produced by replacing both content and hashes. AES-GCM additionally authenticates encrypted payload bytes under their key.
- A copied Request Code cannot decrypt content. An Unlock Code is bound to the corresponding device private key, but an authorized user on that device can still extract the plaintext.
- Expiry is evaluated against the device's local date. There is no trusted online clock or remote revocation. Changing the clock or patching the app can undermine expiry enforcement.
- Losing a content-key secret prevents issuing new codes for that encrypted database. Losing the signing key prevents issuing codes accepted against its declared public key. Keep private backups; never publish either secret.
- Encrypted document access does not itself grant permission to send that document to a cloud service. OpenRouter receives decrypted excerpts only when document sharing is enabled.

## Validation

Fixed Python/Kotlin vectors test wrapping, canonical signing bytes and signature verification. Fixture tests build real protected packages, check for plaintext leakage, unwrap the same content key and recover the original sources/chunks. Incorrect keys, modified ciphertext, changed licence fields, invalid formats and expiry are covered separately. AndroidKeyStore behavior and supported provider/device combinations still need device testing.

See [Security](SECURITY.md) for permission, privacy and threat boundaries.
