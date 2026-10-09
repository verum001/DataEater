"""Tests for the licence: signing, expiry, and the whole customer journey.

Run with:  tools/.venv/bin/python tools/tests/test_licence_command.py

The question that matters most here is simple:

    can a customer make their own licence, or give themselves more time?

The answer must be no, and these tests are how we prove it.
"""

import copy
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from datetime import date, timedelta

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from dataeater_builder import packaging
from dataeater_builder.chunker import Chunk

BUILDER = os.path.join(os.path.dirname(__file__), "..", "dataeater-builder")

failures = []


def printed_code(output):
    """
    Pull the Unlock Code out of what the command printed.

    Found by shape rather than by counting blank-line-separated blocks. The
    command's instructions around it change as the flow is made easier — they
    now explain saving a file — and a test that depends on how many paragraphs
    the help text has is a test that breaks for no reason.

    The check is still strict: the line must look like the thing it is looking
    for, so a genuinely wrong code is not quietly accepted.
    """
    for line in reversed(output.splitlines()):
        candidate = line.strip()
        if len(candidate) > 40 and re.fullmatch(r"[A-Za-z0-9+/=]+", candidate):
            return candidate
    raise AssertionError("no Unlock Code was printed:\n" + output)


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def make_locked_database(folder):
    """A real plain database, then a real locked one, through the CLI."""
    from dataeater_builder.chunker import Chunk as _Chunk

    build = packaging.DatabaseBuild(
        database_id="hf4500", name="HF-4500 Service Manual",
        description="Demo", license="CC0-1.0")
    build.finalise()
    build.sources.append(packaging.SourceRecord(
        source_id="s1", title="HF-4500 Service Manual", year=2026))
    build.chunks.append(_Chunk(
        chunk_id="c001", source_id="s1", section="3.1 Pressure",
        page_start=11, page_end=11,
        text="Idle rail pressure must be 380 bar at 850 rpm."))

    sources_text = packaging.render_sources(build.sources)
    chunks_text = packaging.render_chunks(build.chunks)
    manifest_text = packaging.render_manifest(build, chunks_text, sources_text)

    plain = os.path.join(folder, "plain.dataeater")
    with zipfile.ZipFile(plain, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", manifest_text)
        z.writestr("sources.json", sources_text)
        z.writestr("chunks.jsonl", chunks_text)

    locked = os.path.join(folder, "locked.dataeater")
    subprocess.run([sys.executable, BUILDER, "encrypt", plain, "-o", locked,
                    "--secret", os.path.join(folder, "locked.secret")],
                   capture_output=True, check=True)
    return plain, locked, os.path.join(folder, "locked.secret")


def make_creator_key(folder):
    path = os.path.join(folder, "creator.key")
    subprocess.run([sys.executable, BUILDER, "create-key", "-o", path],
                   capture_output=True, check=True)
    return path


def test_create_key():
    print("create-key")
    work = tempfile.mkdtemp(prefix="de_lic_")
    key_path = make_creator_key(work)

    check("the key file exists", os.path.exists(key_path))
    check("it is private to this user",
          (os.stat(key_path).st_mode & 0o077) == 0, oct(os.stat(key_path).st_mode))

    with open(key_path) as handle:
        record = json.load(handle)
    check("it says what it is", record["format"] == "dataeater-creator-key")
    check("it names the algorithm",
          record["algorithm"] == "ecdsa-p256-sha256", record["algorithm"])
    check("it warns the user", "Never commit it" in record["warning"])

    private, public_text = packaging.read_creator_key_file(key_path)
    check("the key pair loads", private is not None and public_text)

    # It must refuse to overwrite by accident.
    result = subprocess.run([sys.executable, BUILDER, "create-key", "-o", key_path],
                            capture_output=True, text=True)
    check("it refuses to overwrite", result.returncode != 0)
    check("and says why", "already exists" in result.stderr, result.stderr[-150:])


def test_request_code_round_trip():
    print("the Request Code is only a public key")
    phone = packaging.generate_device_keypair()
    code = packaging.make_request_code(phone.public_key())
    # base64 uses A-Z a-z 0-9 + / and may end in '='
    check("it is base64 text",
          all(c.isalnum() or c in "+/=" for c in code), repr(code[:20]))
    check("it is about 120 characters", 90 < len(code) < 200, str(len(code)))

    restored = packaging.read_request_code(code)
    check("the same public key comes back",
          restored.public_numbers() == phone.public_key().public_numbers())

    # An RSA public key must be rejected with a clear message.
    from cryptography.hazmat.primitives.asymmetric import rsa
    rsa_key = rsa.generate_private_key(public_exponent=65537, key_size=2048).public_key()
    from cryptography.hazmat.primitives import serialization
    import base64 as _b64
    rsa_code = _b64.b64encode(rsa_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
    try:
        packaging.read_request_code(rsa_code)
        check("an RSA key is refused", False, "it was accepted")
    except ValueError as problem:
        # The message may say "not an EC public key" or name P-256; both
        # explain the problem clearly, so either is acceptable.
        message = str(problem)
        check("an RSA key is refused",
              "EC public key" in message or "P-256" in message, message)

    for bad in ("", "not base64 at all!!", "aGVsbG8="):
        try:
            packaging.read_request_code(bad)
            check("rubbish input %r is refused" % bad[:12], False)
        except ValueError:
            check("rubbish input %r is refused" % bad[:12], True)


def test_expiry_parsing():
    print("expiry is always an absolute date")
    check("never means no date", packaging.parse_expiry("never") is None)
    check("None means no date", packaging.parse_expiry(None) is None)
    check("30d means 30 days from today",
          packaging.parse_expiry("30d") == (date.today() + timedelta(days=30)).isoformat())
    check("90d means 90 days from today",
          packaging.parse_expiry("90d") == (date.today() + timedelta(days=90)).isoformat())
    check("1y means 365 days from today",
          packaging.parse_expiry("1y") == (date.today() + timedelta(days=365)).isoformat())
    check("a plain date is used as given",
          packaging.parse_expiry("2026-12-31") == "2026-12-31")
    try:
        packaging.parse_expiry("next tuesday")
        check("nonsense is refused", False)
    except ValueError:
        check("nonsense is refused", True)


def test_the_whole_journey():
    print("a customer's full journey")
    work = tempfile.mkdtemp(prefix="de_lic_")
    plain, locked, secret = make_locked_database(work)
    key_path = make_creator_key(work)

    customer_phone = packaging.generate_device_keypair()
    request_code = packaging.make_request_code(customer_phone.public_key())

    result = subprocess.run(
        [sys.executable, BUILDER, "licence", locked,
         "--secret", secret, "--key", key_path,
         "--request-code", request_code, "--expiry", "1y",
         "-o", os.path.join(work, "unlock.txt")],
        capture_output=True, text=True)
    check("the licence command works", result.returncode == 0, result.stderr[-300:])

    with open(os.path.join(work, "unlock.txt")) as handle:
        code = handle.read().strip()

    licence = packaging.decode_licence(code)
    check("the code decodes", licence["format"] == "dataeater-licence")
    check("it carries the database id", licence["database_id"] == "hf4500")
    check("it carries the expiry", licence["expiry"] ==
          (date.today() + timedelta(days=365)).isoformat())
    check("it is signed", "signature" in licence)

    _, public_text = packaging.read_creator_key_file(key_path)
    check("the signature is valid",
          packaging.verify_licence(licence, packaging.read_creator_public_key(public_text)))

    # the customer opens the database
    recovered = packaging.unwrap_content_key(licence["wrap"], customer_phone)
    check("the customer gets the right content key",
          recovered == packaging.read_secret_file(secret))

    with zipfile.ZipFile(locked) as z:
        manifest = json.loads(z.read("manifest.json").decode())
        cipher = z.read("payload.enc")
    import base64
    opened = packaging.decrypt_payload(
        packaging.EncryptedPayload(
            nonce=base64.b64decode(manifest["payload_nonce"]), ciphertext=cipher),
        recovered)
    check("the customer reads the text", "380 bar" in opened["chunks"])


def test_never_expires_by_default():
    print("never expires is the default")
    work = tempfile.mkdtemp(prefix="de_lic_")
    plain, locked, secret = make_locked_database(work)
    key_path = make_creator_key(work)
    phone = packaging.generate_device_keypair()

    result = subprocess.run(
        [sys.executable, BUILDER, "licence", locked, "--secret", secret,
         "--key", key_path,
         "--request-code", packaging.make_request_code(phone.public_key())],
        capture_output=True, text=True)
    check("no --expiry is accepted", result.returncode == 0, result.stderr[-200:])
    check("it says never", "never" in result.stdout)

    code = printed_code(result.stdout)
    licence = packaging.decode_licence(code)
    check("the stored expiry really is null", licence["expiry"] is None)


def test_a_customer_cannot_edit_their_licence():
    print("a customer cannot change what they were given")
    work = tempfile.mkdtemp(prefix="de_lic_")
    plain, locked, secret = make_locked_database(work)
    key_path = make_creator_key(work)
    phone = packaging.generate_device_keypair()

    licence = packaging.sign_licence(
        packaging.build_licence(
            content_key=packaging.read_secret_file(secret),
            device_public_key=phone.public_key(),
            expiry=(date.today() + timedelta(days=30)).isoformat(),
            database_id="hf4500"),
        packaging.read_creator_key_file(key_path)[0])
    _, public_text = packaging.read_creator_key_file(key_path)
    creator_public = packaging.read_creator_public_key(public_text)

    check("the genuine licence verifies",
          packaging.verify_licence(licence, creator_public))

    # 1. give yourself a year instead of 30 days
    tampered = copy.deepcopy(licence)
    tampered["expiry"] = (date.today() + timedelta(days=365)).isoformat()
    check("extending the expiry breaks the signature",
          not packaging.verify_licence(tampered, creator_public))

    # 2. change the database id
    tampered = copy.deepcopy(licence)
    tampered["database_id"] = "somebody-elses-database"
    check("changing the database breaks the signature",
          not packaging.verify_licence(tampered, creator_public))

    # 3. put in somebody else's wrapped key
    other_phone = packaging.generate_device_keypair()
    tampered = copy.deepcopy(licence)
    tampered["wrap"] = packaging.wrap_content_key(
        packaging.read_secret_file(secret), other_phone.public_key())
    check("swapping in another device key breaks the signature",
          not packaging.verify_licence(tampered, creator_public))

    # 4. sign it with your own key
    attacker_private, attacker_public = packaging.create_creator_keypair()
    forged = packaging.sign_licence(licence, attacker_private)
    check("a licence signed by someone else is refused",
          not packaging.verify_licence(forged, creator_public))


def test_licences_are_device_specific():
    print("each licence works on its own device only")
    work = tempfile.mkdtemp(prefix="de_lic_")
    plain, locked, secret = make_locked_database(work)
    key_path = make_creator_key(work)
    key = packaging.read_secret_file(secret)
    signer = packaging.read_creator_key_file(key_path)[0]

    phones = [packaging.generate_device_keypair() for _ in range(2)]
    licences = [packaging.sign_licence(
        packaging.build_licence(key, p.public_key(), None, "hf4500"), signer)
        for p in phones]

    for index, (phone, licence) in enumerate(zip(phones, licences)):
        check("customer %d opens it" % (index + 1),
              packaging.unwrap_content_key(licence["wrap"], phone) == key)

    # cross them
    from cryptography.exceptions import InvalidTag
    try:
        packaging.unwrap_content_key(licences[0]["wrap"], phones[1])
        check("customer 2 cannot use customer 1's licence", False, "it worked")
    except InvalidTag:
        check("customer 2 cannot use customer 1's licence", True)


def test_bad_input_is_reported_clearly():
    print("bad input is explained, not crashed on")
    work = tempfile.mkdtemp(prefix="de_lic_")
    plain, locked, secret = make_locked_database(work)
    key_path = make_creator_key(work)
    phone = packaging.generate_device_keypair()

    base = [sys.executable, BUILDER, "licence", locked,
            "--secret", secret, "--key", key_path,
            "--request-code", packaging.make_request_code(phone.public_key())]

    r = subprocess.run(base + ["--expiry", "sometime soon"],
                       capture_output=True, text=True)
    check("a bad expiry is refused", r.returncode != 0)
    check("with a helpful message", "30d" in r.stderr, r.stderr[-150:])

    r = subprocess.run([sys.executable, BUILDER, "licence", locked,
                        "--secret", secret, "--key", key_path,
                        "--request-code", "nonsense"],
                       capture_output=True, text=True)
    check("a bad Request Code is refused", r.returncode != 0)

    r = subprocess.run([sys.executable, BUILDER, "licence", plain,
                        "--secret", secret, "--key", key_path,
                        "--request-code", packaging.make_request_code(phone.public_key())],
                       capture_output=True, text=True)
    check("licensing an unencrypted database is refused", r.returncode != 0)
    check("and says so", "not encrypted" in r.stderr, r.stderr[-150:])


print("=" * 62)
print("DataEater licence command tests")
print("=" * 62)
test_create_key()
test_request_code_round_trip()
test_expiry_parsing()
test_the_whole_journey()
test_never_expires_by_default()
test_a_customer_cannot_edit_their_licence()
test_licences_are_device_specific()
test_bad_input_is_reported_clearly()

print("=" * 62)
if failures:
    print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
    sys.exit(1)
print("ALL TESTS PASSED")