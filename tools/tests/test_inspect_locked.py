"""Tests for `inspect` on a LOCKED database.

Run with:  tools/.venv/bin/python tools/tests/test_inspect_locked.py

THE BUG THIS COVERS
-------------------
`inspect` used to read `sources.json` and `chunks.jsonl` without ever looking at the
manifest's `encryption` field. A locked database has neither of those files - the text
is inside one encrypted `payload.enc` - so it died with a raw Python traceback:

    KeyError: "There is no item named 'sources.json' in the archive"

This is the same mistake the Android app used to make, and it is worth naming as a
pattern: **a reader that looks for content files before checking whether the database is
encrypted will always report an encrypted database as damaged.**

The Android side is fixed and tested in `DatabaseLockTest`. This is the Linux side.
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
]
SOURCES = [
    {"id": "s1", "title": "HF-4500 Service Manual", "publisher": "DataEater",
     "language": "en", "license": "CC0-1.0", "year": 2026},
]

MANIFEST = {
    "format": "dataeater",
    "format_version": 1,
    "database_id": "locked-demo",
    "name": "Locked Demo Manual",
    "description": "A database whose text is encrypted.",
    "language": "en",
    "license": "CC0-1.0",
    "encryption": "none",
    "counts": {"documents": 1, "chunks": 1},
}


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def make_plain_database(path):
    """Writes a small plain `.dataeater` file."""
    manifest = dict(MANIFEST)
    chunks_text = "\n".join(CHUNK_LINES) + "\n"
    sources_text = json.dumps(SOURCES, ensure_ascii=False) + "\n"
    manifest["files"] = {
        "chunks.jsonl": {"sha256": packaging.sha256_of_text(chunks_text)},
        "sources.json": {"sha256": packaging.sha256_of_text(sources_text)},
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(packaging.MANIFEST_FILE,
                         json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        archive.writestr(packaging.CHUNKS_FILE, chunks_text)
        archive.writestr(packaging.SOURCES_FILE, sources_text)


def run_inspect(path):
    """Runs `inspect` and returns (returncode, stdout, stderr)."""
    finished = subprocess.run(
        [sys.executable, BUILDER, "inspect", path],
        capture_output=True, text=True)
    return finished.returncode, finished.stdout, finished.stderr


def test_locked_database_does_not_crash():
    """The whole point. It used to raise a raw traceback."""
    folder = tempfile.mkdtemp(prefix="dataeater-inspect-")
    plain = os.path.join(folder, "plain.dataeater")
    locked = os.path.join(folder, "locked.dataeater")

    make_plain_database(plain)
    packaging.encrypt_database_file(
        input_path=plain, output_path=locked,
        creator="Northgate Technical Publishing",
        contact="licences@northgate.example",
        creator_public_key="MCowBQYDK2VwAyEABT10elTfT5weCVJPjP4gFWXuDq",
    )

    code, out, err = run_inspect(locked)

    check("it does not crash on a locked database",
          "Traceback" not in err, err.strip().splitlines()[-1:] and
          err.strip().splitlines()[-1] or "")
    check("it exits successfully", code == 0, "exit code %d" % code)
    check("it says the database is locked", "LOCKED DATABASE" in out, out[:200])
    check("it explains the text cannot be shown",
          "encrypted" in out.lower(), out[:200])


def test_it_does_not_leak_the_encrypted_text():
    """A creator checking their work must not see the customer-only content."""
    folder = tempfile.mkdtemp(prefix="dataeater-inspect-")
    plain = os.path.join(folder, "plain.dataeater")
    locked = os.path.join(folder, "locked.dataeater")

    make_plain_database(plain)
    packaging.encrypt_database_file(input_path=plain, output_path=locked)

    _, out, _ = run_inspect(locked)

    check("it does not print the encrypted text",
          "380 bar" not in out, "found '380 bar' in the inspect output")
    check("it does not print a chunk", "First chunk" not in out)


def test_it_shows_the_creator_details():
    """These are exactly the fields a customer is shown on the locked screen."""
    folder = tempfile.mkdtemp(prefix="dataeater-inspect-")
    plain = os.path.join(folder, "plain.dataeater")
    locked = os.path.join(folder, "locked.dataeater")

    make_plain_database(plain)
    packaging.encrypt_database_file(
        input_path=plain, output_path=locked,
        creator="Northgate Technical Publishing",
        contact="licences@northgate.example",
        creator_public_key="MCowBQYDK2VwAyEABT10elTfT5weCVJPjP4gFWXuDq",
    )

    _, out, _ = run_inspect(locked)

    check("it shows the creator name",
          "Northgate Technical Publishing" in out)
    check("it shows the contact address",
          "licences@northgate.example" in out)


def test_it_warns_when_the_public_key_is_missing():
    """
    Without `--creator-public-key` the customer's app cannot check that an
    Unlock Code really came from this creator. That is worth a warning, not a
    silent omission.
    """
    folder = tempfile.mkdtemp(prefix="dataeater-inspect-")
    plain = os.path.join(folder, "plain.dataeater")
    locked = os.path.join(folder, "locked.dataeater")

    make_plain_database(plain)
    packaging.encrypt_database_file(
        input_path=plain, output_path=locked, creator="Someone")

    _, out, _ = run_inspect(locked)

    check("it warns about a missing creator public key",
          "WARNING" in out and "creator public key" in out.lower())


def test_a_plain_database_still_shows_its_text():
    """The fix must not break the ordinary case."""
    folder = tempfile.mkdtemp(prefix="dataeater-inspect-")
    plain = os.path.join(folder, "plain.dataeater")
    make_plain_database(plain)

    code, out, err = run_inspect(plain)

    check("a plain database still exits successfully", code == 0, err[:200])
    check("a plain database still lists its documents",
          "HF-4500 Service Manual" in out, out[:200])
    check("a plain database still shows its first chunk",
          "First chunk" in out and "380 bar" in out, out[:300])
    check("a plain database is not called locked", "LOCKED DATABASE" not in out)


def main():
    print("=" * 62)
    print("DataEater 'inspect a locked database' tests")
    print("=" * 62)
    test_locked_database_does_not_crash()
    test_it_does_not_leak_the_encrypted_text()
    test_it_shows_the_creator_details()
    test_it_warns_when_the_public_key_is_missing()
    test_a_plain_database_still_shows_its_text()

    print("=" * 62)
    if failures:
        print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
        return 1
    print("ALL TESTS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())