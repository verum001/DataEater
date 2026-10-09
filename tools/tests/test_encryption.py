"""Tests for payload encryption.

Run with:  tools/.venv/bin/python tools/tests/test_encryption.py

These tests cover only what has been implemented so far: encrypting and
decrypting the database text with AES-256-GCM and a random content key.

Deliberately not tested yet, because it does not exist yet:
  - the CLI `encrypt` command
  - wrapping the content key for a device (ECDH + HKDF)
  - the signed Unlock Code and expiry

A test that cannot fail proves nothing, so the negative cases are here too.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from dataeater_builder.packaging import (
    CONTENT_KEY_BYTES,
    ENCRYPTION_NAME,
    GCM_NONCE_BYTES,
    decrypt_payload,
    encrypt_payload,
    generate_content_key,
)

try:
    from cryptography.exceptions import InvalidTag
except ImportError:  # pragma: no cover
    InvalidTag = Exception

failures = []

CHUNKS = (
    '{"id":"c001","source_id":"s1","section":"1.1 Overview","page":3,'
    '"lang":"en","text":"Idle rail pressure must be 380 bar at 850 rpm."}\n'
    '{"id":"c002","source_id":"s1","section":"1.2 Filters","page":4,'
    '"lang":"en","text":"Replace the primary filter every 30,000 km."}\n'
)

SOURCES = (
    '[\n'
    '  {\n'
    '    "id": "s1",\n'
    '    "title": "Demo Manual",\n'
    '    "publisher": "DataEater",\n'
    '    "language": "en",\n'
    '    "license": "CC0-1.0",\n'
    '    "year": 2026\n'
    '  }\n'
    ']\n'
)


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def test_key_generation():
    print("content key")
    key = generate_content_key()
    check("a key is 32 bytes", len(key) == CONTENT_KEY_BYTES, str(len(key)))
    check("two keys are never the same",
          generate_content_key() != generate_content_key())
    check("the key is not all zeroes", key != b"\x00" * CONTENT_KEY_BYTES)


def test_round_trip():
    print("round trip")
    key = generate_content_key()
    payload = encrypt_payload(CHUNKS, SOURCES, key)

    check("a nonce was produced", len(payload.nonce) == GCM_NONCE_BYTES,
          str(len(payload.nonce)))
    check("ciphertext is longer than zero", len(payload.ciphertext) > 0)

    recovered = decrypt_payload(payload, key)
    check("chunks came back unchanged", recovered["chunks"] == CHUNKS)
    check("sources came back unchanged", recovered["sources"] == SOURCES)


def test_not_plain_text():
    print("the ciphertext must not leak the text")
    key = generate_content_key()
    payload = encrypt_payload(CHUNKS, SOURCES, key)
    blob = payload.ciphertext

    check("'380 bar' is not visible", b"380 bar" not in blob)
    check("'Demo Manual' is not visible", b"Demo Manual" not in blob)
    check("'rail pressure' is not visible", b"rail pressure" not in blob)
    check("the envelope marker is not visible", b"dataeater-payload" not in blob)


def test_each_encryption_differs():
    print("a fresh nonce every time")
    key = generate_content_key()
    first = encrypt_payload(CHUNKS, SOURCES, key)
    second = encrypt_payload(CHUNKS, SOURCES, key)

    check("two runs use different nonces", first.nonce != second.nonce)
    check("two runs give different ciphertext",
          first.ciphertext != second.ciphertext)
    check("both still decrypt correctly",
          decrypt_payload(first, key)["chunks"]
          == decrypt_payload(second, key)["chunks"] == CHUNKS)


def test_wrong_key_fails():
    print("a wrong key must not work")
    key = generate_content_key()
    payload = encrypt_payload(CHUNKS, SOURCES, key)

    try:
        decrypt_payload(payload, generate_content_key())
        check("a different key is refused", False, "it decrypted")
    except InvalidTag:
        check("a different key is refused", True)
    except Exception as problem:
        check("a different key is refused", False,
              "wrong exception: %s" % type(problem).__name__)


def test_tampered_ciphertext_fails():
    print("modified data must not work")
    key = generate_content_key()

    # flip one bit in the middle
    payload = encrypt_payload(CHUNKS, SOURCES, key)
    broken = bytearray(payload.ciphertext)
    broken[len(broken) // 2] ^= 0x01
    from dataeater_builder.packaging import EncryptedPayload
    tampered = EncryptedPayload(nonce=payload.nonce, ciphertext=bytes(broken))
    try:
        decrypt_payload(tampered, key)
        check("one flipped bit is refused", False, "it decrypted")
    except InvalidTag:
        check("one flipped bit is refused", True)

    # a different nonce
    tampered = EncryptedPayload(nonce=os.urandom(GCM_NONCE_BYTES),
                                ciphertext=payload.ciphertext)
    try:
        decrypt_payload(tampered, key)
        check("a different nonce is refused", False, "it decrypted")
    except InvalidTag:
        check("a different nonce is refused", True)

    # truncated data
    tampered = EncryptedPayload(nonce=payload.nonce,
                                ciphertext=payload.ciphertext[:10])
    try:
        decrypt_payload(tampered, key)
        check("truncated data is refused", False, "it decrypted")
    except Exception:
        check("truncated data is refused", True)


def test_bad_key_length():
    print("key length is checked")
    for length in (0, 16, 31, 33, 64):
        try:
            encrypt_payload(CHUNKS, SOURCES, b"\x01" * length)
            check("a %d byte key is rejected" % length, False, "it was accepted")
        except ValueError:
            check("a %d byte key is rejected" % length, True)


def test_empty_input():
    print("empty text still works")
    key = generate_content_key()
    payload = encrypt_payload("", "", key)
    recovered = decrypt_payload(payload, key)
    check("empty chunks come back empty", recovered["chunks"] == "")
    check("empty sources come back empty", recovered["sources"] == "")


def test_non_ascii_survives():
    print("text in any language survives")
    key = generate_content_key()
    text = '{"id":"c1","source_id":"s1","text":"Давление в рампе 380 бар"}\n'
    payload = encrypt_payload(text, SOURCES, key)
    check("Cyrillic comes back unchanged",
          decrypt_payload(payload, key)["chunks"] == text)


def test_encryption_name():
    print("the manifest marker")
    check("the scheme name is the one the app expects",
          ENCRYPTION_NAME == "aes-256-gcm", ENCRYPTION_NAME)


print("=" * 62)
print("DataEater payload encryption tests")
print("=" * 62)
test_key_generation()
test_round_trip()
test_not_plain_text()
test_each_encryption_differs()
test_wrong_key_fails()
test_tampered_ciphertext_fails()
test_bad_key_length()
test_empty_input()
test_non_ascii_survives()
test_encryption_name()

print("=" * 62)
if failures:
    print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
    sys.exit(1)
print("ALL TESTS PASSED")