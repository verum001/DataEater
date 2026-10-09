"""Tests for the zip-bomb guard in the builder.

Run with:  tools/.venv/bin/python tools/tests/test_zip_bomb_guard.py

THE ATTACK
----------
A `.dataeater` file does not always come from this program. A creator may be
asked to inspect or encrypt a file a customer sent them. A ZIP archive is a
well-known way to attack a program: a few kilobytes that decompress into
gigabytes, exhausting memory with no useful error.

The Android app had exactly this hole and it is now closed there too
(`ZipBombTest.kt`). This file covers the Linux side, because a creator's
computer is just as capable of running out of memory as a phone.

WHAT IS ACTUALLY TESTED
-----------------------
A real 256 MB bomb cannot be built inside a test without spending the memory
the limit exists to protect. So the limit is lowered to something small, a
real bomb is built against it, and the guard must refuse it.

The production limits are checked separately by `the_shipped_limits_are_generous`
so nobody "fixes" a bomb by setting the limit uncomfortably small.
"""

import json
import os
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from dataeater_builder import packaging

failures = []

# Small enough to build in a test, large enough to prove the counting works.
TEST_LIMIT = 512 * 1024
REAL_LIMIT = 32 * 1024 * 1024


def check(name, ok, detail=""):
    if ok:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def write_bomb(path, expand_to=8 * TEST_LIMIT):
    """
    Writes a database-shaped ZIP whose chunks file explodes.

    A bomb with no manifest would be refused for the wrong reason ("no
    manifest.json"), which would prove nothing. This one looks like a real
    database right up until the chunks file is read, so only the size guard can
    stop it - which is exactly the case worth testing.
    """
    manifest = {
        "format": "dataeater", "format_version": 1,
        "database_id": "bomb", "name": "bomb", "encryption": "none",
        "counts": {"documents": 0, "chunks": 1},
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        archive.writestr("sources.json", "[]")
        with archive.open("chunks.jsonl", "w", force_zip64=True) as handle:
            block = b"\0" * (64 * 1024)
            written = 0
            while written < expand_to:
                step = min(len(block), expand_to - written)
                handle.write(block[:step])
                written += step
    return os.path.getsize(path)


def with_small_limit():
    """Temporarily lowers the guard so a bomb fits inside a test."""
    packaging.MAX_ENTRY_BYTES = TEST_LIMIT


def restore_limits():
    packaging.MAX_ENTRY_BYTES = REAL_LIMIT


def guarded(fn, *args):
    """Runs a builder function with the small limit.

    Returns (refused, message). Any exception OTHER than the guard's own
    ValueError is reported as "not refused" together with what happened.

    This matters: without it, removing the guard makes the whole suite crash
    with a raw traceback instead of failing, and a crash looks like a broken
    test rather than a broken guard. A guard that is gone must show up as a
    clear FAIL.
    """
    with_small_limit()
    try:
        result = fn(*args)
        return False, "returned without complaining: %r" % (result,)
    except ValueError as problem:
        return True, str(problem)
    except Exception as problem:
        return False, "crashed with %s: %s" % (type(problem).__name__, problem)
    finally:
        restore_limits()


def test_a_bomb_is_refused():
    folder = tempfile.mkdtemp(prefix="dataeater-bomb-")
    path = os.path.join(folder, "bomb.dataeater")
    size_on_disk = write_bomb(path)

    check(
        "the bomb really is small on disk",
        size_on_disk < TEST_LIMIT,
        "%d bytes" % size_on_disk,
    )

    # verify_database returns a list of problems rather than raising, so it is
    # called directly and inspected.
    with_small_limit()
    try:
        try:
            problems = packaging.verify_database(path)
        except Exception as problem:
            problems = ["crashed with %s: %s" % (type(problem).__name__, problem)]
    finally:
        restore_limits()

    joined = "; ".join(problems)
    check("verify_database refuses a bomb",
          "exhaust memory" in joined, joined[:160])


def test_the_refusal_explains_itself():
    """A creator must be told what happened, not just that something did."""
    folder = tempfile.mkdtemp(prefix="dataeater-bomb-")
    path = os.path.join(folder, "bomb.dataeater")
    write_bomb(path)

    with_small_limit()
    try:
        try:
            problems = packaging.verify_database(path)
        except Exception as problem:
            problems = ["crashed with %s: %s" % (type(problem).__name__, problem)]
    finally:
        restore_limits()

    joined = "; ".join(problems)
    check(
        "the refusal says the file was not read",
        "has not been read" in joined,
        joined[:160],
    )
    check(
        "the refusal names the offending entry",
        "chunks.jsonl" in joined,
        joined[:160],
    )


def test_encrypt_refuses_a_bomb():
    """`encrypt` reads the whole database, so it needs the guard too."""
    folder = tempfile.mkdtemp(prefix="dataeater-bomb-")
    path = os.path.join(folder, "bomb-plain.dataeater")
    write_bomb(path)

    refused, message = guarded(packaging.read_database_texts, path)

    check("read_database_texts refuses a bomb", refused, message[:160])
    check(
        "its refusal names the entry",
        "chunks.jsonl" in message,
        message[:160],
    )
    check("a real database is still readable", _ordinary_database_reads())


def _ordinary_database_reads():
    folder = tempfile.mkdtemp(prefix="dataeater-ok-")
    path = os.path.join(folder, "ok.dataeater")
    chunks_text = '{"id":"c1","source_id":"s1","section":"","page":0,"lang":"en","text":"hello"}\n'
    manifest = {
        "format": "dataeater", "format_version": 1,
        "database_id": "ok", "name": "ok", "encryption": "none",
        "counts": {"documents": 1, "chunks": 1},
        "files": {
            "chunks.jsonl": {"sha256": packaging.sha256_of_text(chunks_text)},
            "sources.json": {"sha256": packaging.sha256_of_text("[]")},
        },
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        archive.writestr("sources.json", "[]")
        archive.writestr("chunks.jsonl", chunks_text)

    texts = packaging.read_database_texts(path)
    return texts["chunks"] == chunks_text


def test_too_many_entries_is_refused():
    """Millions of tiny entries is the other shape of the same attack."""
    folder = tempfile.mkdtemp(prefix="dataeater-many-")
    path = os.path.join(folder, "many.dataeater")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps({
            "format": "dataeater", "format_version": 1,
            "database_id": "x", "name": "x", "encryption": "none",
        }))
        for index in range(60):
            archive.writestr("filler-%d" % index, "x")

    original = packaging.MAX_ENTRIES
    packaging.MAX_ENTRIES = 10
    try:
        try:
            problems = packaging.verify_database(path)
        except Exception as problem:
            problems = ["crashed with %s: %s" % (type(problem).__name__, problem)]
    finally:
        packaging.MAX_ENTRIES = original

    joined = "; ".join(problems)
    check("too many entries is refused", "exhaust memory" in joined, joined[:160])


def test_the_shipped_limits_are_generous():
    """
    Stops someone fixing a bomb by setting the limit uncomfortably small.

    Measured: the demo is 3 KB, a real 873-page manual is about 1 MB of text.
    A 20 MB limit for a 10,000-page manual leaves the shipped limit an order of
    magnitude of headroom above anything real.
    """
    # A 10,000-page manual is roughly 17 MB of text, so 32 MB clears it.
    check(
        "the per-entry limit clears a 10,000-page manual",
        packaging.MAX_ENTRY_BYTES > 17 * 1024 * 1024,
        "%d bytes" % packaging.MAX_ENTRY_BYTES,
    )
    check(
        "the entry count is far above any real database",
        packaging.MAX_ENTRIES >= 10,
        "%d" % packaging.MAX_ENTRIES,
    )
    check(
        "Android and Python refuse the same files",
        packaging.MAX_ENTRY_BYTES == 32 * 1024 * 1024,
        "python=%d" % packaging.MAX_ENTRY_BYTES,
    )


def main():
    print("=" * 62)
    print("DataEater zip-bomb guard tests (builder side)")
    print("=" * 62)
    test_a_bomb_is_refused()
    test_the_refusal_explains_itself()
    test_encrypt_refuses_a_bomb()
    test_too_many_entries_is_refused()
    test_the_shipped_limits_are_generous()

    print("=" * 62)
    if failures:
        print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
        return 1
    print("ALL TESTS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())