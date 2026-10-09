"""Guard unnecessary permissions in the main app's merged manifests.
INTERNET is allowed for explicit model downloads and opt-in OpenRouter requests.
The preserved DataEater_offline copy retains the original no-INTERNET guard.
"""

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]

ANDROID_NAMESPACE = "{http://schemas.android.com/apk/res/android}"

# Permissions that would let the app talk to a server, or learn things about
# the user that have nothing to do with reading a document.
#
# Not a general privacy audit — a short list of the ones that would break the
# promise this project is built on. If a new one is added deliberately, it
# belongs here with a reason, not quietly on the phone.
FORBIDDEN = {
    "android.permission.ACCESS_NETWORK_STATE": "model downloads do not need this permission",
    "android.permission.ACCESS_WIFI_STATE": "model downloads do not need this permission",
    "android.permission.ACCESS_COARSE_LOCATION": "documents are not located",
    "android.permission.ACCESS_FINE_LOCATION": "documents are not located",
    "android.permission.CAMERA": "reading documents needs no camera",
    "android.permission.RECORD_AUDIO": "reading documents needs no microphone",
    "android.permission.READ_CONTACTS": "documents are not address books",
    "android.permission.READ_CALL_LOG": "documents are not call logs",
    "android.permission.READ_SMS": "documents are not messages",
}


def merged_manifests():
    """
    Every merged manifest Gradle produced.

    There can be several — one per build variant and one per Android SDK level
    — and a permission could appear in only one of them, so all are read.
    """
    build = REPOSITORY / "app" / "build"
    found = sorted(build.glob("**/AndroidManifest.xml"))
    # The packaged manifests, which are what is really shipped, plus the
    # intermediates, which is where a merge difference would show up first.
    packaged = sorted(build.glob("**/packaged-manifests/**/AndroidManifest.xml"))
    return found + packaged


def permissions_in(path: Path):
    """The permission names declared in one manifest."""
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError:
        # A manifest that will not parse is a build problem, not a privacy
        # one, and the build has already failed by the time this runs.
        return None
    names = []
    for element in root.iter("uses-permission"):
        name = element.get(ANDROID_NAMESPACE + "name")
        if name:
            names.append(name)
    return names


def main() -> int:
    manifests = merged_manifests()

    if not manifests:
        print("Could not find a merged manifest under app/build/.")
        print("Build the app first:")
        print("    JAVA_HOME=~/android-studio/jbr ./gradlew assembleDebug")
        return 2

    offences = []
    for manifest in manifests:
        try:
            root = ElementTree.parse(manifest).getroot()
            if root.get("package", "").endswith(".test"):
                continue
            app = root.find("application")
        except ElementTree.ParseError:
            continue
        if app is not None and app.get(ANDROID_NAMESPACE + "allowBackup") != "false":
            offences.append((manifest, "android:allowBackup", "private app data must not enter automatic backups"))
    domains = {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"}
    rules = REPOSITORY / "app/src/main/res/xml"
    for name, sections in [("backup_rules.xml", [None]), ("data_extraction_rules.xml", ["cloud-backup", "device-transfer"])]:
        file = rules / name
        root = ElementTree.parse(file).getroot()
        for section in sections:
            node = root if section is None else root.find(section)
            excluded = set() if node is None else {e.get("domain") for e in node.findall("exclude") if e.get("path") == "."}
            if not domains <= excluded:
                offences.append((file, "backup exclusion", f"{section or 'legacy backup'} must exclude every private storage domain"))


    for manifest in manifests:
        names = permissions_in(manifest)
        if names is None:
            continue
        for name in names:
            if name in FORBIDDEN:
                offences.append((manifest, name, FORBIDDEN[name]))

    if offences:
        print("FAIL: DataEater must not request these permissions.\n")
        for manifest, name, reason in offences:
            print(f"  {name}")
            print(f"    in {manifest.relative_to(REPOSITORY)}")
            print(f"    {reason}\n")
        print("If one of these is genuinely needed, it has to be argued for in")
        print("docs/SECURITY.md and removed from this list deliberately.")
        return 1

    print(f"OK: backup policy verified; no forbidden permission in {len(manifests)} merged manifest(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
