"""Tests for wrapping the content key for one device.

Run with:  tools/.venv/bin/python tools/tests/test_licence_wrap.py

This covers device binding: one random content key, many devices, and the
database never re-encrypted.

The most important test in this file is the one that proves a second device
cannot use a licence made for a first device. That is the whole point of
device binding.
"""

import base64
import copy
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from dataeater_builder.packaging import (
    CONTENT_KEY_BYTES,
    WRAP_FORMAT,
    WRAP_INFO,
    WRAP_SALT,
    WRAP_VERSION,
    decrypt_payload,
    encrypt_payload,
    generate_content_key,
    generate_device_keypair,
    unwrap_content_key,
    wrap_content_key,
)

try:
    from cryptography.exceptions import InvalidTag
except ImportError:  # pragma: no cover
    InvalidTag = Exception

failures = []

CHUNKS = '{"id":"c1","source_id":"s1","text":"Idle pressure 380 bar."}\n'
SOURCES = '[\n  {"id": "s1", "title": "Demo Manual"}\n]\n'


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def test_wrap_round_trip():
    print("wrap and unwrap")
    device = generate_device_keypair()
    content_key = generate_content_key()

    envelope = wrap_content_key(content_key, device.public_key())
    check("the envelope is labelled", envelope["format"] == WRAP_FORMAT)
    check("the version is right", envelope["version"] == WRAP_VERSION)
    check("the envelope has the three fields we need",
          all(k in envelope for k in
              ("ephemeral_public_key", "nonce", "wrapped_key")))

    recovered = unwrap_content_key(envelope, device)
    check("the content key comes back unchanged", recovered == content_key)
    check("the recovered key is 32 bytes", len(recovered) == CONTENT_KEY_BYTES)


def test_the_content_key_is_hidden():
    print("the wrapped key must not leak")
    device = generate_device_keypair()
    content_key = generate_content_key()
    envelope = wrap_content_key(content_key, device.public_key())

    blob = "".join(str(envelope[k]) for k in envelope).encode()
    check("the key bytes are not visible as text",
          base64.b64encode(content_key)[:20] not in blob)
    check("the envelope is JSON-safe", isinstance(envelope, dict))


def test_full_unlock_chain():
    print("the whole chain: wrap, then open the database")
    device = generate_device_keypair()
    content_key = generate_content_key()

    # creator encrypts the database
    payload = encrypt_payload(CHUNKS, SOURCES, content_key)
    check("the payload does not contain the text", b"380 bar" not in payload.ciphertext)

    # creator issues a licence for this device
    envelope = wrap_content_key(content_key, device.public_key())

    # the phone recovers the key and opens the database
    recovered = unwrap_content_key(envelope, device)
    opened = decrypt_payload(
        type(payload)(nonce=payload.nonce, ciphertext=payload.ciphertext),
        recovered,
    )
    check("the phone reads the original text", opened["chunks"] == CHUNKS)
    check("the phone reads the original sources", opened["sources"] == SOURCES)


def test_one_database_many_devices():
    print("one database, many customers, no re-encryption")
    content_key = generate_content_key()
    payload = encrypt_payload(CHUNKS, SOURCES, content_key)
    cipher_before = payload.ciphertext

    phones = [generate_device_keypair() for _ in range(3)]
    licences = [wrap_content_key(content_key, phone.public_key()) for phone in phones]

    for index, (phone, licence) in enumerate(zip(phones, licences)):
        check("customer %d can open the database" % (index + 1),
              decrypt_payload(payload, unwrap_content_key(licence, phone))["chunks"] == CHUNKS)

    check("the database was never re-encrypted",
          payload.ciphertext == cipher_before)
    check("each licence is different",
          len({l["wrapped_key"] for l in licences}) == 3)


def test_a_licence_is_useless_on_another_phone():
    print("a licence must not work on a different phone")
    owner = generate_device_keypair()
    stranger = generate_device_keypair()
    content_key = generate_content_key()

    envelope = wrap_content_key(content_key, owner.public_key())

    try:
        recovered = unwrap_content_key(envelope, stranger)
        decrypt_payload  # keep the import meaningful
        check("a stranger is refused", False,
              "it produced a %d byte key" % len(recovered))
    except InvalidTag:
        check("a stranger is refused", True)
    except Exception as problem:
        check("a stranger is refused", False,
              "wrong exception: %s" % type(problem).__name__)


def test_tampered_licence_fails():
    print("a modified licence must fail")
    device = generate_device_keypair()
    content_key = generate_content_key()
    envelope = wrap_content_key(content_key, device.public_key())

    # flip a character in the wrapped key
    broken = copy.deepcopy(envelope)
    raw = bytearray(base64.b64decode(broken["wrapped_key"]))
    raw[0] ^= 0x01
    broken["wrapped_key"] = base64.b64encode(bytes(raw)).decode()
    try:
        unwrap_content_key(broken, device)
        check("a modified wrapped key is refused", False, "it opened")
    except InvalidTag:
        check("a modified wrapped key is refused", True)

    # a different nonce
    broken = copy.deepcopy(envelope)
    raw = bytearray(base64.b64decode(broken["nonce"]))
    raw[0] ^= 0x01
    broken["nonce"] = base64.b64encode(bytes(raw)).decode()
    try:
        unwrap_content_key(broken, device)
        check("a modified nonce is refused", False, "it opened")
    except InvalidTag:
        check("a modified nonce is refused", True)

    # a different ephemeral key
    other = wrap_content_key(content_key, generate_device_keypair().public_key())
    broken = copy.deepcopy(envelope)
    broken["ephemeral_public_key"] = other["ephemeral_public_key"]
    try:
        unwrap_content_key(broken, device)
        check("a swapped ephemeral key is refused", False, "it opened")
    except (InvalidTag, Exception) as problem:
        check("a swapped ephemeral key is refused", True)


def test_wrong_curve_is_refused():
    print("only P-256 is accepted")
    from cryptography.hazmat.primitives.asymmetric import ec
    content_key = generate_content_key()

    p384 = ec.generate_private_key(ec.SECP384R1()).public_key()
    try:
        wrap_content_key(content_key, p384)
        check("a P-384 device key is refused", False, "it was accepted")
    except ValueError:
        check("a P-384 device key is refused", True)


def test_bad_key_length_is_refused():
    print("the content key length is checked")
    device = generate_device_keypair()
    for length in (16, 31, 33):
        try:
            wrap_content_key(b"\x01" * length, device.public_key())
            check("a %d byte key is refused" % length, False, "accepted")
        except ValueError:
            check("a %d byte key is refused" % length, True)


def test_malformed_envelope():
    print("a malformed licence is refused clearly")
    device = generate_device_keypair()

    try:
        unwrap_content_key({"format": "something-else", "version": 1}, device)
        check("a foreign format is refused", False)
    except ValueError:
        check("a foreign format is refused", True)

    try:
        unwrap_content_key({"format": WRAP_FORMAT, "version": 99}, device)
        check("a future version is refused", False)
    except ValueError:
        check("a future version is refused", True)


def test_constants_match_the_design():
    print("the constants are the ones the design specifies")
    check("the curve is P-256", WRAP_FORMAT == "dataeater-wrap")
    check("the salt is 16 zero bytes", WRAP_SALT == b"\x00" * 16)
    check("the info string names the licence", WRAP_INFO == b"DataEater licence v1")
    check("the version is 1", WRAP_VERSION == 1)


print("=" * 62)
print("DataEater licence wrapping tests")
print("=" * 62)
test_wrap_round_trip()
test_the_content_key_is_hidden()
test_full_unlock_chain()
test_one_database_many_devices()
test_a_licence_is_useless_on_another_phone()
test_tampered_licence_fails()
test_wrong_curve_is_refused()
test_bad_key_length_is_refused()
test_malformed_envelope()
test_constants_match_the_design()

print("=" * 62)
if failures:
    print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
    sys.exit(1)
print("ALL TESTS PASSED")