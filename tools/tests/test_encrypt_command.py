"""Tests for the `encrypt` command and the locked database it produces.

Run with:  tools/.venv/bin/python tools/tests/test_encrypt_command.py

The questions these answer:

  - does a locked database really contain no readable knowledge?
  - can it be opened again with the content key from the secret file?
  - is the database reusable for many customers?
  - are the creator's secrets kept out of the database and out of git?
"""

import json
import os
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from dataeater_builder import packaging

BUILDER = os.path.join(os.path.dirname(__file__), "..", "dataeater-builder")

failures = []

CHUNK_LINES = [
    '{"id":"c001","source_id":"s1","section":"3.1 Pressure","page":11,'
    '"lang":"en","text":"Idle rail pressure must be 380 bar at 850 rpm."}',
    '{"id":"c002","source_id":"s1","section":"4.1 Bleeding","page":19,'
    '"lang":"en","text":"Switch the ignition on for 30 seconds."}',
]
SOURCES = [
    {"id": "s1", "title": "HF-4500 Service Manual", "publisher": "DataEater",
     "language": "en", "license": "CC0-1.0", "year": 2026},
]


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def make_plain_database(folder):
    """A small but realistic plain database, made through the real code.

    It goes through DatabaseBuild and render_* exactly like the `build`
    command does, so the counts in the manifest are real. Building the ZIP by
    hand would produce a manifest claiming zero documents, which would be a
    broken database rather than a small one.
    """
    from dataeater_builder.chunker import Chunk

    build = packaging.DatabaseBuild(
        database_id="hf4500",
        name="HF-4500 Service Manual",
        description="Demo database",
        license="CC0-1.0",
    )
    build.finalise()
    build.sources.append(
        packaging.SourceRecord(source_id="s1", title=SOURCES[0]["title"],
                                publisher="DataEater", year=2026,
                                license="CC0-1.0")
    )
    build.chunks.append(
        Chunk(chunk_id="c001", source_id="s1", section="3.1 Pressure",
              page_start=11, page_end=11,
              text="Idle rail pressure must be 380 bar at 850 rpm.")
    )
    build.chunks.append(
        Chunk(chunk_id="c002", source_id="s1", section="4.1 Bleeding",
              page_start=19, page_end=19,
              text="Switch the ignition on for 30 seconds.")
    )

    sources_text = packaging.render_sources(build.sources)
    chunks_text = packaging.render_chunks(build.chunks)
    manifest_text = packaging.render_manifest(build, chunks_text, sources_text)

    path = os.path.join(folder, "plain.dataeater")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", manifest_text)
        archive.writestr("sources.json", sources_text)
        archive.writestr("chunks.jsonl", chunks_text)
    return path


def run_encrypt(plain_path, locked_path, secret_path, extra=()):
    return subprocess.run(
        [sys.executable, BUILDER, "encrypt", plain_path,
         "-o", locked_path, "--secret", secret_path] + list(extra),
        capture_output=True, text=True,
    )


def test_command_succeeds():
    print("the command runs")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")

    result = run_encrypt(plain, locked, secret, ["--creator", "Example Company",
                                                 "--contact", "sales@example.com"])
    check("it exits cleanly", result.returncode == 0, result.stderr[-300:])
    check("the locked file exists", os.path.exists(locked))
    check("the secret file exists", os.path.exists(secret))


def test_locked_database_contents():
    print("what a locked database contains")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret, ["--creator", "Example Company",
                                        "--contact", "sales@example.com"])

    with zipfile.ZipFile(locked) as archive:
        names = archive.namelist()
        check("manifest.json is first", names[0] == "manifest.json", str(names))
        check("the text files are gone",
              "chunks.jsonl" not in names and "sources.json" not in names, str(names))
        check("payload.enc is present", "payload.enc" in names, str(names))

        manifest = json.loads(archive.read("manifest.json").decode())
        raw_blob = archive.read("payload.enc")

    check("the manifest is still readable", manifest["name"] == "HF-4500 Service Manual")
    check("it says it is encrypted", manifest["encryption"] == "aes-256-gcm",
          manifest.get("encryption"))
    check("the creator is shown", manifest.get("creator") == "Example Company")
    check("the contact is shown", manifest.get("contact") == "sales@example.com")
    check("the chunk count is still visible",
          manifest["counts"]["chunks"] == 2, str(manifest["counts"]))

    # nothing about any customer
    check("no unlock code is in the database", "unlock_code" not in manifest)
    check("no licence is in the database", "licence" not in manifest)
    check("no expiry is in the database", "expiry" not in manifest)

    check("'380 bar' is not in the locked file", b"380 bar" not in raw_blob)
    check("'HF-4500 Service Manual' is not in the payload",
          b"HF-4500" not in raw_blob)
    check("'c001' is not in the payload", b"c001" not in raw_blob)


def test_it_can_be_opened_again():
    print("opening it again with the secret")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)

    key = packaging.read_secret_file(secret)
    check("the secret gives a 32 byte key", len(key) == 32, str(len(key)))

    texts = packaging.read_database_texts(plain)
    payload = packaging.encrypt_payload(texts["chunks"], texts["sources"], key)

    with zipfile.ZipFile(locked) as archive:
        manifest = json.loads(archive.read("manifest.json").decode())
        cipher = archive.read("payload.enc")

    import base64
    opened = packaging.decrypt_payload(
        packaging.EncryptedPayload(
            nonce=base64.b64decode(manifest["payload_nonce"]),
            ciphertext=cipher,
        ),
        key,
    )
    check("the original text comes back", opened["chunks"] == texts["chunks"])
    check("the original sources come back", opened["sources"] == texts["sources"])


def test_wrong_key_cannot_open_it():
    print("a wrong key cannot open it")
    from cryptography.exceptions import InvalidTag
    import base64

    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)

    with zipfile.ZipFile(locked) as archive:
        manifest = json.loads(archive.read("manifest.json").decode())
        cipher = archive.read("payload.enc")

    payload = packaging.EncryptedPayload(
        nonce=base64.b64decode(manifest["payload_nonce"]), ciphertext=cipher)
    try:
        packaging.decrypt_payload(payload, packaging.generate_content_key())
        check("a wrong key is refused", False, "it opened")
    except InvalidTag:
        check("a wrong key is refused", True)


def test_the_database_is_reusable():
    print("one locked database, many customers")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)
    key = packaging.read_secret_file(secret)

    with zipfile.ZipFile(locked) as archive:
        cipher_before = archive.read("payload.enc")

    licences = []
    for _ in range(3):
        phone = packaging.generate_device_keypair()
        licences.append((phone, packaging.wrap_content_key(key, phone.public_key())))

    for index, (phone, licence) in enumerate(licences):
        recovered = packaging.unwrap_content_key(licence, phone)
        check("customer %d gets the same key" % (index + 1), recovered == key)

    with zipfile.ZipFile(locked) as archive:
        check("the database was never re-encrypted",
              archive.read("payload.enc") == cipher_before)


def test_double_encrypt_is_refused():
    print("a locked database cannot be locked again")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)

    result = run_encrypt(locked, os.path.join(work, "twice.dataeater"),
                         os.path.join(work, "twice.secret"))
    check("it refuses", result.returncode != 0)
    check("the reason is clear", "already encrypted" in (result.stderr + result.stdout),
          result.stderr[-200:])


def test_the_key_is_not_replaced_by_accident():
    print("an existing key is never silently replaced")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)
    first_key = packaging.read_secret_file(secret)

    # Running again without the flag must REUSE the key, so that licences
    # already issued to customers keep working.
    result = run_encrypt(plain, locked, secret)
    check("without the flag it succeeds", result.returncode == 0, result.stderr[-200:])
    check("without the flag the key is UNCHANGED",
          packaging.read_secret_file(secret) == first_key)
    check("and it says which key it used", "existing key" in result.stdout)

    # Only with the flag does a brand new key get made.
    result = run_encrypt(plain, locked, secret, ["--force-new-key"])
    check("a new key is only made when asked", result.returncode == 0, result.stderr[-200:])
    check("the key really did change",
          packaging.read_secret_file(secret) != first_key)


def test_secrets_are_safe():
    print("the secret file")
    work = tempfile.mkdtemp(prefix="de_enc_")
    plain = make_plain_database(work)
    locked = os.path.join(work, "locked.dataeater")
    secret = os.path.join(work, "locked.secret")
    run_encrypt(plain, locked, secret)

    check("the file is named so git ignores it",
          any(part in ("*.key", "*secret*") for part in
              (".gitignore",)) or "secret" in os.path.basename(secret))
    check("the file is marked private for this user",
          (os.stat(secret).st_mode & 0o077) == 0,
          oct(os.stat(secret).st_mode))
    with open(secret) as handle:
        record = json.load(handle)
    check("it explains what it is", "Never commit it" in record["warning"])
    check("it says which database it belongs to",
          record["database"] == "locked.dataeater")


print("=" * 62)
print("DataEater encrypt command tests")
print("=" * 62)
test_command_succeeds()
test_locked_database_contents()
test_it_can_be_opened_again()
test_wrong_key_cannot_open_it()
test_the_database_is_reusable()
test_double_encrypt_is_refused()
test_the_key_is_not_replaced_by_accident()
test_secrets_are_safe()

print("=" * 62)
if failures:
    print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
    sys.exit(1)
print("ALL TESTS PASSED")