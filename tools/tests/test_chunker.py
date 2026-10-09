"""Tests for the chunker. Run with:  tools/.venv/bin/python -m pytest tools/tests
or simply:  tools/.venv/bin/python tools/tests/test_chunker.py
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from dataeater_builder.chunker import (
    Chunk,
    Page,
    chunk_pages,
    looks_like_heading,
    merge_short_chunks,
    split_long_paragraph,
    split_paragraphs,
)

failures = []


def check(name, condition, detail=""):
    if condition:
        print("  PASS  " + name)
    else:
        print("  FAIL  " + name + ("  -> " + detail if detail else ""))
        failures.append(name)


def test_heading_detection():
    print("heading detection")
    check("numbered heading", looks_like_heading("3.1 Fuel Pressure Testing"))
    check("chapter heading", looks_like_heading("4. Routine Service"))
    check("shouting fault code", looks_like_heading("P0087 FUEL RAIL PRESSURE TOO LOW"))
    check("title case", looks_like_heading("Fuel Pressure Testing"))
    check("a normal sentence is not a heading",
          not looks_like_heading("Replace the primary fuel filter every 30,000 km."))
    check("a long paragraph is not a heading",
          not looks_like_heading("word " * 30))
    check("empty is not a heading", not looks_like_heading(""))


def test_paragraph_split():
    print("paragraph splitting")
    text = "First one.\n\nSecond one.\n\n\nThird one."
    parts = split_paragraphs(text)
    check("three paragraphs", len(parts) == 3, str(parts))
    check("no empty parts", all(p for p in parts))


def test_long_paragraph_split():
    print("oversized paragraph")
    paragraph = "Sentence number one here. Sentence number two here. " \
                "Sentence number three here. Sentence number four here."
    pieces = split_long_paragraph(paragraph, 40)
    check("it was split", len(pieces) > 1, str(pieces))
    check("no piece is empty", all(p.strip() for p in pieces))
    check("a short paragraph is left alone",
          split_long_paragraph("short", 100) == ["short"])


def test_basic_chunking():
    print("chunking a document")
    pages = [
        Page(1, "1.1 System Overview\n\n" + "word " * 200),
        Page(2, "1.2 System Overview\n\n" + "word " * 200),
    ]
    chunks = chunk_pages(pages, target_chars=400, source_id="s1")

    check("we got chunks", len(chunks) >= 2, str(len(chunks)))
    check("pages are recorded", all(c.page_start >= 1 for c in chunks))
    check("a section was detected", chunks[0].section == "1.1 System Overview",
          repr(chunks[0].section))
    check("source id is stored", all(c.source_id == "s1" for c in chunks))
    check("ids are unique", len({c.chunk_id for c in chunks}) == len(chunks))
    check("page numbers never go backwards",
          all(a.page_start <= b.page_start for a, b in zip(chunks, chunks[1:])))


def test_no_content_is_lost():
    print("nothing is lost")
    body = "This is a real sentence about rail pressure. " * 30
    pages = [Page(1, "2.1 Testing\n\n" + body)]
    chunks = chunk_pages(pages, target_chars=300)

    joined = " ".join(c.text for c in chunks)
    missing = body.split(".")[0].strip()
    check("the original text survives chunking", missing in joined)


def test_single_page_chunk():
    print("one page only")
    pages = [Page(7, "Just a short note about nothing in particular.")]
    chunks = chunk_pages(pages)
    check("one chunk produced", len(chunks) == 1)
    check("page number kept", chunks[0].page_start == 7)
    check("empty document gives no chunks", chunk_pages([]) == [])


def test_merge_short_chunks():
    print("merging tiny chunks")
    tiny = [
        Chunk("One line.", 1, 1),
        Chunk("Two lines.", 1, 1),
        Chunk("A much longer chunk that is already big enough. " * 10, 2, 2),
        Chunk("Another small one.", 2, 2),
    ]
    merged = merge_short_chunks(tiny, minimum_chars=50)
    check("fewer chunks after merging", len(merged) < len(tiny), str(len(merged)))
    check("text was not lost",
          "Two lines." in " ".join(c.text for c in merged))
    check("page range was widened", merged[0].page_end >= merged[0].page_start)


print("=" * 60)
print("DataEater chunker tests")
print("=" * 60)
test_heading_detection()
test_paragraph_split()
test_long_paragraph_split()
test_basic_chunking()
test_no_content_is_lost()
test_single_page_chunk()
test_merge_short_chunks()
print("=" * 60)
if failures:
    print("FAILED: %d test(s): %s" % (len(failures), ", ".join(failures)))
    sys.exit(1)
print("ALL TESTS PASSED")