#!/usr/bin/env python3
"""
DataEater demo database builder  --  v0.1 (PROOF OF CONCEPT)
================================================================
This is a rough prototype of the future "dataeater-builder" Linux tool.
Right now it only builds our synthetic demo database. Later it will read
PDF / DOCX / Markdown files instead of hard-coded text.

What it does, in plain English:
  1. Takes a list of text pieces ("chunks") written below.
  2. Writes them into  chunks.jsonl  (one JSON object per line).
  3. Writes a description of each document into  sources.json.
  4. Calculates a SHA-256 fingerprint for every file (tamper detection).
  5. Puts everything into manifest.json.
  6. Zips it all up into  demo.dataeater

Run it with:   python3 tools/build_demo_db.py
"""

import hashlib
import json
import os
import zipfile
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# WHERE THE OUTPUT GOES
# ---------------------------------------------------------------------------

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR = os.path.join(PROJECT_DIR, "dataeater", "databases")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "demo.dataeater")

# ---------------------------------------------------------------------------
# THE DOCUMENTS
# ---------------------------------------------------------------------------
# !!! EVERY WORD BELOW IS INVENTED BY US FOR TESTING. !!!
# The product "HF-4500", all part numbers and all fault codes are fictional.
# This is why we are allowed to ship it in a public repository (CC0 licence).

SOURCES = [
    {
        "id": "s1",
        "title": "HF-4500 Fuel System - Service Manual (DEMO)",
        "publisher": "DataEater Project (fictional, synthetic test data)",
        "language": "en",
        "license": "CC0-1.0",
        "year": 2026,
    },
    {
        "id": "s2",
        "title": "HF-4500 Diagnostic Trouble Codes - Reference (DEMO)",
        "publisher": "DataEater Project (fictional, synthetic test data)",
        "language": "en",
        "license": "CC0-1.0",
        "year": 2026,
    },
]

# Each tuple is one chunk of knowledge.
#   (id, source_id, section, page, text)
CHUNKS = [
    # ---- Document 1 : Service Manual -------------------------------------
    ("c001", "s1", "1.1 System Overview", 3,
     "The HF-4500 is a four-cylinder diesel engine fitted with a common rail "
     "fuel system. High-pressure pumps deliver fuel to a shared rail, where it "
     "is stored at 1600 bar and metered to each injector electronically."),
    ("c002", "s1", "1.2 System Overview", 4,
     "The supply pump draws diesel from the tank and feeds the high-pressure "
     "pump. A 15 micron primary filter and a 4 micron secondary filter protect "
     "the injection system from contamination."),
    ("c003", "s1", "2.1 Routine Service", 7,
     "Replace the primary fuel filter every 30,000 km or every 12 months, "
     "whichever comes first. Replace the secondary filter every 60,000 km."),
    ("c004", "s1", "2.1 Routine Service", 7,
     "Always relieve injection system pressure before opening any fuel line. "
     "The maximum residual pressure is 250 bar. Wear eye protection when "
     "disconnecting high-pressure lines."),
    ("c005", "s1", "3.1 Fuel Pressure Testing", 11,
     "Connect gauge GT-2000 to test port P1 on the high-pressure pipe. Start "
     "the engine and allow the rail pressure to stabilise. Idle rail pressure "
     "must be 380 bar at 850 rpm."),
    ("c006", "s1", "3.1 Fuel Pressure Testing", 12,
     "If idle rail pressure is below 340 bar, the supply pump or the pressure "
     "control valve is faulty. If pressure is above 420 bar, check that the "
     "pressure control valve GT-4410 is not stuck closed."),
    ("c007", "s1", "3.2 Injector Testing", 14,
     "Injector sealing rings must be replaced every time an injector is removed. "
     "Tighten injector hold-down bolts to a torque of 45 Nm using a torque "
     "wrench. Overtightening damages the cylinder head."),
    ("c008", "s1", "3.2 Injector Testing", 15,
     "Cylinder balance is measured with gauge BT-900 attached to the diagnostic "
     "connector. A difference above 8 percent between cylinders indicates a "
     "stuck or leaking injector."),
    ("c009", "s1", "4.1 Bleeding the System", 18,
     "To bleed the fuel system: switch the ignition on for 30 seconds so the "
     "electric pump primes the circuit, then crank the engine for a maximum of "
     "20 seconds. Repeat up to three times. Do not crank continuously for more "
     "than one minute in total, this will overheat the starter motor."),
    ("c010", "s1", "4.1 Bleeding the System", 19,
     "Air remains trapped in the system after a filter change or a fuel line "
     "disconnection. Symptom: the engine cranks but does not start, and "
     "fuel returns to the supply line instead of reaching the rail."),
    ("c011", "s1", "5.1 Electrical Connections", 23,
     "The fuel pressure control valve GT-4410 is controlled by the engine "
     "control unit ECU pin 61. A broken wire at connector CN-14 causes rail "
     "pressure to remain at maximum while the engine is off."),
    ("c012", "s1", "5.1 Electrical Connections", 24,
     "Before disconnecting the battery, note that the ECU loses learned "
     "adaptations. After reconnecting, clear fault memory and allow the engine "
     "to idle for 60 seconds without adding throttle."),
    ("c013", "s1", "6.1 Warranty Conditions", 27,
     "Warranty is void if the secondary fuel filter GT-220 was not replaced at "
     "the correct interval, or if non-approved fuel was used. Approved fuel must "
     "meet specification EN 590 and be filtered to 4 microns."),

    # ---- Document 2 : Fault codes -----------------------------------------
    ("c101", "s2", "P0087 Fuel Rail Pressure Too Low", 2,
     "Fault code P0087 means the fuel rail pressure is below the threshold "
     "set by the engine control unit. Common causes: empty fuel tank, blocked "
     "primary filter, air in the system, or a failed supply pump."),
    ("c102", "s2", "P0087 Fuel Rail Pressure Too Low", 3,
     "To diagnose P0087, first check fuel level and the primary filter GT-110, "
     "then bleed the system following section 4.1. Only after those steps "
     "should the supply pump be tested."),
    ("c103", "s2", "P0088 Fuel Rail Pressure Too High", 5,
     "Fault code P0088 indicates rail pressure above 1800 bar. This is usually "
     "caused by pressure control valve GT-4410 failing closed. Do not continue "
     "to run the engine, as excess pressure damages the injectors."),
    ("c104", "s2", "P0201 Injector Circuit Cylinder 1", 8,
     "Fault code P0201 reports a fault in the injector circuit for cylinder 1. "
     "Check connector CN-21, the 5 V supply and the return line. Measure "
     "resistance of the injector coil: a healthy coil measures 0.9 to 1.3 ohm."),
    ("c105", "s2", "P0201 Injector Circuit Cylinder 1", 9,
     "An open circuit in the injector return path shows as P0201 and usually "
     "leaves the cylinder dead while the engine continues to run. Swap the "
     "injector connectors to identify which cylinder is affected."),
    ("c106", "s2", "P0522 Engine Oil Pressure Sensor", 14,
     "Fault code P0522 means the oil pressure sensor signal is outside the "
     "expected range. Check oil level first, then the sensor at connector "
     "CN-08 and the 5 V reference voltage."),
    ("c107", "s2", "P1000 Generic System Fault", 18,
     "Fault code P1000 is a manufacturer-specific fault indicating a system "
     "error recorded in the ECU. Read the freeze frame data before clearing the "
     "fault memory, because the conditions at the time of the fault are lost "
     "after clearing."),
    ("c108", "s2", "Clearing Fault Memory", 21,
     "To clear fault memory: switch the ignition off, wait 30 seconds, then "
     "switch it on. Stored faults and freeze frame data are deleted. Always "
     "write down the fault codes before clearing them."),
]


def sha256_of_text(text: str) -> str:
    """Return the SHA-256 fingerprint of a string, as 'sha256:<hex>'."""
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return "sha256:" + digest


def build():
    # --- Step 1: build chunks.jsonl -------------------------------------
    chunks_lines = []
    for chunk_id, source_id, section, page, text in CHUNKS:
        record = {
            "id": chunk_id,
            "source_id": source_id,
            "section": section,
            "page": page,
            "lang": "en",
            "text": text,
        }
        chunks_lines.append(json.dumps(record, ensure_ascii=False))
    chunks_text = "\n".join(chunks_lines) + "\n"

    # --- Step 2: build sources.json --------------------------------------
    sources_text = json.dumps(SOURCES, ensure_ascii=False, indent=2) + "\n"

    # --- Step 3: build manifest.json -------------------------------------
    # The manifest is the first thing the app reads. It says what this
    # database is and how to check that it has not been modified.
    manifest = {
        "format": "dataeater",
        "format_version": 1,
        "database_id": "demo",
        "name": "Demo Technical Knowledge Base",
        "description": (
            "Small synthetic knowledge base about a fictional diesel fuel "
            "injection system. Used to test DataEater. Not real service data."
        ),
        "language": "en",
        "created_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "builder_version": "0.1.0",
        "license": "CC0-1.0",
        "encryption": "none",
        "counts": {
            "documents": len(SOURCES),
            "chunks": len(CHUNKS),
        },
        "files": {
            # Each file listed here gets checked against its fingerprint
            # when the database is opened. This detects tampering.
            "chunks.jsonl": {
                "sha256": sha256_of_text(chunks_text),
                "records": len(CHUNKS),
            },
            "sources.json": {
                "sha256": sha256_of_text(sources_text),
            },
        },
    }
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"

    # --- Step 4: write the .dataeater file ------------------------------
    # A .dataeater file is a ZIP archive with a fixed layout.
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with zipfile.ZipFile(OUTPUT_FILE, "w", zipfile.ZIP_DEFLATED) as zf:
        # manifest.json is written FIRST on purpose: the app must be able to
        # find it without scanning the whole archive.
        zf.writestr("manifest.json", manifest_text)
        zf.writestr("sources.json", sources_text)
        zf.writestr("chunks.jsonl", chunks_text)

    # --- Step 5: report -------------------------------------------------
    size = os.path.getsize(OUTPUT_FILE)
    print("DataEater demo database created")
    print("  file  :", OUTPUT_FILE)
    print("  size  :", size, "bytes")
    print("  docs  :", len(SOURCES))
    print("  chunks:", len(CHUNKS))
    print()
    print("Contents of the package:")
    with zipfile.ZipFile(OUTPUT_FILE) as zf:
        for info in zf.infolist():
            print(f"   {info.filename:<16} {info.file_size:>6} bytes")


if __name__ == "__main__":
    build()