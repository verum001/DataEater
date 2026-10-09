"""End-to-end check of a REAL locked database file.

Run with:  tools/.venv/bin/python tools/tests/test_android_locked_fixture.py

WHAT THIS IS FOR
----------------
`test_encrypt_command.py` checks the builder against its own expectations.
That cannot catch the case that actually matters: a real `.dataeater` file
produced by the builder, read back by the app's Kotlin code.

So this script:
  1. copies the bundled demo database,
  2. encrypts it with the real `encrypt` code path,
  3. prints the resulting locked file's manifest,
and the Android test `LockedDatabaseFixtureTest` reads that exact manifest text
and must classify it as locked, with the creator and contact intact.

Between them, the two languages meet on one real file.

Run this after changing anything about encryption or the manifest. The Android
side prints the manifest text it was given; if this script's output no longer
matches, that test fails and you know why.
"""

import json
import os
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from dataeater_builder import packaging  # noqa: E402

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))))
DEMO_DATABASE = os.path.join(PROJECT_ROOT, "dataeater", "databases",
                             "demo.dataeater")

CREATOR = "Test Creator Ltd"
CONTACT = "licences@test-creator.example"
CREATOR_PUBLIC_KEY = "MCowBQYDK2VwAyEABT10elTfT5weCVJPjP4gFWXuD+qO6mnMUJkTrWrCk8A="


def build_locked_file():
    """Encrypts the bundled demo and returns (manifest_text, file_path)."""
    folder = tempfile.mkdtemp(prefix="dataeater-fixture-")
    plain = os.path.join(folder, "demo-plain.dataeater")
    locked = os.path.join(folder, "demo-locked.dataeater")

    with open(DEMO_DATABASE, "rb") as source:
        plain_bytes = source.read()
    with open(plain, "wb") as target:
        target.write(plain_bytes)

    packaging.encrypt_database_file(
        input_path=plain,
        output_path=locked,
        creator=CREATOR,
        contact=CONTACT,
        creator_public_key=CREATOR_PUBLIC_KEY,
    )

    with zipfile.ZipFile(locked) as archive:
        manifest_text = archive.read("manifest.json").decode("utf-8")
        names = archive.namelist()

    return manifest_text, locked, names


def main():
    manifest_text, locked_path, names = build_locked_file()

    # The locked file must NOT contain readable text. If chunks.jsonl were
    # still there, the whole scheme would be pointless.
    assert "chunks.jsonl" not in names, \
        "a locked database must not ship readable chunks: %r" % (names,)
    assert "payload.enc" in names, \
        "a locked database must carry payload.enc: %r" % (names,)

    manifest = json.loads(manifest_text)
    assert manifest["encryption"] == "aes-256-gcm", manifest.get("encryption")
    assert manifest["creator"] == CREATOR
    assert manifest["contact"] == CONTACT
    assert manifest["payload_nonce"], "a locked manifest needs the payload nonce"
    # Public details survive, so the app can show them before any key exists.
    assert manifest["name"], "the name must stay readable"
    assert manifest["description"], "the description must stay readable"

    # And the plain knowledge must really be absent from the bytes.
    with zipfile.ZipFile(locked_path) as archive:
        blob = b"".join(archive.read(name) for name in names)
    assert b"rail pressure" not in blob.lower(), \
        "searchable text leaked out of a locked database"

    print("LOCKED FIXTURE OK")
    print("  file      : %s" % locked_path)
    print("  entries   : %s" % ", ".join(names))
    print("  encryption: %s" % manifest["encryption"])
    print()
    print("--- manifest.json the Android test must classify as LOCKED ---")
    print(manifest_text, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())