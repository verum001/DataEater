"""Generate known-answer test vectors for the Android crypto tests.

Run with:  tools/.venv/bin/python tools/tests/make_crypto_vectors.py

WHY THIS EXISTS
---------------
The Android app and this Python builder each implement the same key maths
independently:

    shared secret --HKDF-SHA256--> wrapping key --AES-256-GCM--> content key

If the two implementations ever disagree by even one byte, every licence the
creator issues becomes useless on the customer's phone. That is a silent,
shipping-breaking bug, and unit tests on each side would not catch it: both
sides would still be self-consistent.

So this script produces fixed, hard-coded answers from the PYTHON side.
`app/src/test/java/com/dataeater/app/security/CryptoCompatibilityTest.kt`
checks the Kotlin side against those exact values. Change one side without the
other and the Android test fails immediately.

The vectors are deterministic: nothing here uses randomness or the clock.
Re-running the script always prints the same output.

It only ever prints PUBLIC test data and the CREATOR's throwaway signing key,
which exists solely for this test. No real customer key is involved.
"""

import base64
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from dataeater_builder import packaging  # noqa: E402
from dataeater_builder.packaging import (  # noqa: E402
    GCM_NONCE_BYTES,
    WRAP_INFO,
    WRAP_SALT,
    _derive_wrapping_key,
    _licence_signing_bytes,
    _import_crypto,
)

hashes, serialization, ec, AESGCM, HKDF = _import_crypto()


def vector_hkdf():
    """A fixed shared secret through the agreed HKDF step."""
    shared = bytes(range(32))  # 0x00..0x1f, easy to recognise
    key = _derive_wrapping_key(shared)
    return {
        "input_hex": shared.hex(),
        "salt_hex": WRAP_SALT.hex(),
        "info": WRAP_INFO.decode("ascii"),
        "output_hex": key.hex(),
    }


def vector_signing_bytes():
    """The exact text a licence signature covers.

    Keys deliberately arrive out of alphabetical order, because the whole
    point of sorting is that order must not matter. A nested object is
    included too, since the Kotlin side has to recurse into it.
    """
    licence = {
        "version": 1,
        "database_id": "demo-db",
        "expiry": "2027-01-31",
        "format": "dataeater-licence",
        "wrap": {"wrapped_key": "QUJD", "nonce": "REVG", "version": 1},
        "signed_by": "c2lnbmVy",
    }
    raw = _licence_signing_bytes(licence)
    return {
        "licence": licence,
        "signing_bytes_utf8": raw.decode("utf-8"),
    }


def vector_full_unwrap():
    """A whole wrapped key, built exactly as wrap_content_key builds one.

    The device key and the creator's ephemeral key are both fixed, so the
    envelope is reproducible. In production both are random.
    """
    device_private = ec.derive_private_key(int("11" * 32, 16), ec.SECP256R1())
    ephemeral_private = ec.derive_private_key(int("22" * 32, 16), ec.SECP256R1())
    content_key = bytes(range(32, 64))  # 0x20..0x3f

    shared = ephemeral_private.exchange(ec.ECDH(), device_private.public_key())
    wrapping_key = _derive_wrapping_key(shared)

    nonce = bytes(range(GCM_NONCE_BYTES))  # fixed instead of os.urandom
    ciphertext = AESGCM(wrapping_key).encrypt(nonce, content_key, None)

    ephemeral_der = ephemeral_private.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    device_public_der = device_private.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    device_private_der = device_private.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )

    return {
        "envelope": {
            "format": "dataeater-wrap",
            "version": 1,
            "ephemeral_public_key": base64.b64encode(ephemeral_der).decode("ascii"),
            "nonce": base64.b64encode(nonce).decode("ascii"),
            "wrapped_key": base64.b64encode(ciphertext).decode("ascii"),
        },
        # What the Kotlin test must end up with.
        "expected_content_key_hex": content_key.hex(),
        # Fixed keys the Kotlin test builds its ECDH step from. On a real
        # phone the private key comes out of AndroidKeyStore and cannot be
        # exported at all.
        "device_public_key_b64": base64.b64encode(device_public_der).decode("ascii"),
        "device_private_key_pkcs8_b64": base64.b64encode(
            device_private_der).decode("ascii"),
    }


def vector_other_keys():
    """Two extra valid keys, for the negative tests.

    `other_device_private_key` stands in for a different phone: the envelope
    above was not wrapped for it, so unwrapping must fail. `attacker_public_key`
    is a valid EC P-256 key that did NOT sign the licence, so verification must
    fail for the right reason rather than on a malformed key.
    """
    other_device = ec.derive_private_key(int("44" * 32, 16), ec.SECP256R1())
    other_device_der = other_device.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )

    attacker = ec.derive_private_key(int("66" * 32, 16), ec.SECP256R1())
    attacker_der = attacker.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    return {
        "other_device_private_key_pkcs8_b64": base64.b64encode(
            other_device_der).decode("ascii"),
        "attacker_public_key_b64": base64.b64encode(attacker_der).decode("ascii"),
    }


def vector_signature():
    """A real ECDSA P-256 signature, so verification is covered too.

    Deterministic, so the printed vector never changes between runs: ECDSA
    itself is randomised, but the key is fixed, so the same signature comes
    out every time. This is a throwaway key used only by this script.
    """
    signing_private = ec.derive_private_key(int("33" * 32, 16), ec.SECP256R1())

    licence = {
        "format": "dataeater-licence",
        "version": 1,
        "database_id": "demo-db",
        "expiry": "2027-01-31",
        "wrap": {
            "format": "dataeater-wrap",
            "version": 1,
            "ephemeral_public_key": "QUJD",
            "nonce": "REVG",
            "wrapped_key": "R0hJ",
        },
    }
    signed = packaging.sign_licence(licence, signing_private)

    public_der = signing_private.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return {
        "creator_public_key_b64": base64.b64encode(public_der).decode("ascii"),
        "signed_licence": signed,
        "verifies": packaging.verify_licence(
            signed,
            serialization.load_der_public_key(public_der)),
    }


def vector_request_code():
    """The text a customer sends to the creator, for one fixed key pair.

    `make_request_code` is just base64 of the X.509 SubjectPublicKeyInfo DER,
    and `DeviceKeys.requestCode()` must produce the identical string from the
    key AndroidKeyStore gives us. If those two ever differ, no licence can be
    issued for a real phone at all.

    The public key here is `private = 0x55 * 32`, chosen once. No private key
    exists anywhere near this script.
    """
    private_key = ec.derive_private_key(int("55" * 32, 16), ec.SECP256R1())
    request_code = packaging.make_request_code(private_key.public_key())

    return {
        # Exactly what `DeviceKeys.requestCode()` must return.
        "request_code": request_code,
        # The same key with the prefix a human would paste in, to prove the
        # creator's tool tolerates whitespace when it is pasted in.
        "request_code_wrapped": "\n".join(
            request_code[i:i + 64] for i in range(0, len(request_code), 64)),
        "decoded_bytes": len(base64.b64decode(request_code)),
    }


def vector_unlock_code():
    """The exact string a customer pastes into the app, and its parts.

    This is what the Android side has to accept. `encode_licence` is just
    base64 of the JSON, so the whole thing is one long line a customer pastes
    into a text box - and it usually arrives with stray whitespace around it
    from whatever they copied it from.
    """
    licence = vector_signature()["signed_licence"]
    encoded = packaging.encode_licence(licence)

    return {
        "unlock_code": encoded,
        # The same code as it realistically arrives: wrapped and padded.
        "unlock_code_wrapped": "\n".join(
            encoded[i:i + 64] for i in range(0, len(encoded), 64)),
        "creator_public_key_b64":
            vector_signature()["creator_public_key_b64"],
    }


def vector_expired_unlock_code():
    """A correctly signed licence whose expiry is already in the past.

    Signed by the same key, so it is genuinely valid. The only thing wrong
    with it is the date - which is exactly the case that must be refused.
    """
    _, serialization, _, _, _ = _import_crypto()
    private_key = ec.derive_private_key(int("33" * 32, 16), ec.SECP256R1())
    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    licence = {
        "format": "dataeater-licence",
        "version": 1,
        "database_id": "demo",
        "expiry": "2020-01-01",
        "wrap": {
            "format": "dataeater-wrap",
            "version": 1,
            "ephemeral_public_key": "QUJD",
            "nonce": "REVG",
            "wrapped_key": "R0hJ",
        },
    }
    signed = packaging.sign_licence(licence, private_key)

    return {
        "unlock_code": packaging.encode_licence(signed),
        "creator_public_key_b64": base64.b64encode(public_der).decode("ascii"),
        "expiry": "2020-01-01",
    }


def vector_wrong_database_unlock_code():
    """A perfectly valid code that was issued for a DIFFERENT database.

    This exists because the app refuses a mismatched `database_id` **after**
    the signature check. That ordering means the mismatch can never be reached
    with a tampered licence - the signature fails first. To test the check
    honestly, the code has to be genuinely signed by the genuine creator and
    genuinely be for the wrong database.
    """
    private_key = ec.derive_private_key(int("33" * 32, 16), ec.SECP256R1())

    licence = {
        "format": "dataeater-licence",
        "version": 1,
        "database_id": "a-completely-different-manual",
        "expiry": "2027-01-31",
        "wrap": {
            "format": "dataeater-wrap",
            "version": 1,
            "ephemeral_public_key": "QUJD",
            "nonce": "REVG",
            "wrapped_key": "R0hJ",
        },
    }
    signed = packaging.sign_licence(licence, private_key)

    return {
        "unlock_code": packaging.encode_licence(signed),
        "database_id": "a-completely-different-manual",
    }


def main():
    vectors = {
        "hkdf": vector_hkdf(),
        "signing_bytes": vector_signing_bytes(),
        "full_unwrap": vector_full_unwrap(),
        "other_keys": vector_other_keys(),
        "request_code": vector_request_code(),
        "signature": vector_signature(),
        "unlock_code": vector_unlock_code(),
        "expired_unlock_code": vector_expired_unlock_code(),
        "wrong_database_unlock_code": vector_wrong_database_unlock_code(),
    }
    print(json.dumps(vectors, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())