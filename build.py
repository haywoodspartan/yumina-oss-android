"""Portable Windows build: Python 3.10+, no globally installed JDK/SDK required.

Downloads pinned public build artifacts, compiles, tests, aligns, signs, and
verifies the APK. In a standalone checkout run: python build.py.
The original workspace also supports: python android/build.py.
Private signing material stays under data/android-signing (never web-served).
STORY_WRITER_ANDROID_DATA_DIR and STORY_WRITER_ANDROID_OUTPUT_DIR override
the data and public output folders respectively.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import struct
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ANDROID = Path(__file__).resolve().parent
# Preserve the original installation's cache, signing identity and download
# location, while keeping a standalone checkout self-contained.
PARENT = ANDROID.parent
ROOT = PARENT if (PARENT / "start.py").is_file() and (PARENT / "web" / "package.json").is_file() else ANDROID
DATA = Path(os.environ.get("STORY_WRITER_ANDROID_DATA_DIR", str(ROOT / "data"))).expanduser().resolve()
CACHE = DATA / "android-toolchain"
BUILD = ANDROID / "build"
SIGNING = DATA / "android-signing"
APP = ANDROID / "app" / "src" / "main"
OUTPUT_DIR = Path(os.environ.get("STORY_WRITER_ANDROID_OUTPUT_DIR", str(ROOT / "downloads"))).expanduser().resolve()
OUTPUT = OUTPUT_DIR / "yumina-android.apk"


def run(args: list, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def download(item):
    name, spec = item
    target = CACHE / name
    if not target.exists():
        print(f"Downloading {name}...", flush=True)
        partial = target.with_suffix(".part")
        with urllib.request.urlopen(spec["url"], timeout=120) as response, partial.open("wb") as file:
            shutil.copyfileobj(response, file)
        partial.replace(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != spec["sha256"]:
        raise RuntimeError(f"Checksum mismatch for {target}. Remove that cached file and retry.")
    return target


def extract(archive, directory):
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        for entry in source.infolist():
            target = (directory / entry.filename).resolve()
            if not target.is_relative_to(directory.resolve()):
                raise RuntimeError("Unsafe archive member")
        source.extractall(directory)


def aligned_apk(resources, dex, destination):
    # Store compiled resources and DEX uncompressed, with 4-byte-aligned data.
    # Android 11+ requires this for resources.arsc. No native libraries are used.
    with zipfile.ZipFile(resources) as source, zipfile.ZipFile(destination, "w") as out:
        entries = [(e.filename, source.read(e)) for e in source.infolist()]
        entries.extend((p.name, p.read_bytes()) for p in sorted(dex.glob("*.dex")))
        for name, data in entries:
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            offset = out.fp.tell() + 30 + len(name.encode("utf-8"))
            padding = (-offset - 4) % 4
            info.extra = struct.pack("<HH", 0xCAFE, padding) + bytes(padding)
            out.writestr(info, data)


def verify_alignment(path):
    with zipfile.ZipFile(path) as archive, path.open("rb") as file:
        assert "classes.dex" in archive.namelist()
        for item in archive.infolist():
            if item.compress_type == zipfile.ZIP_STORED:
                file.seek(item.header_offset + 26)
                name_length, extra_length = struct.unpack("<HH", file.read(4))
                assert (item.header_offset + 30 + name_length + extra_length) % 4 == 0, item.filename


def main():
    if os.name != "nt":
        raise SystemExit("This portable toolchain is pinned for Windows x64. Use the Gradle project on other hosts.")
    CACHE.mkdir(parents=True, exist_ok=True)
    BUILD.mkdir(exist_ok=True)
    versions = json.loads((ANDROID / "tools" / "toolchain.lock.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        list(pool.map(download, versions.items()))
    if not list((CACHE / "jdk21").glob("*/bin/javac.exe")): extract(CACHE / "jdk21.zip", CACHE / "jdk21")
    if not (CACHE / "aapt2" / "aapt2.exe").exists(): extract(CACHE / "aapt2.jar", CACHE / "aapt2")
    java_bin = next((CACHE / "jdk21").glob("*/bin/javac.exe")).parent
    java, javac = java_bin / "java.exe", java_bin / "javac.exe"
    aapt = CACHE / "aapt2" / "aapt2.exe"
    framework = CACHE / "android.jar"
    generated, classes, dex, helpers = [BUILD / name for name in ("generated", "classes", "dex", "helpers")]
    for directory in (generated, classes, dex, helpers):
        # Clear only known output files; never recursively delete a computed path.
        directory.mkdir(exist_ok=True)
        for old in directory.rglob("*.class"): old.unlink()
        for old in directory.glob("*.dex"): old.unlink()
    ca = APP / "res/raw/story_writer_ca.pem"
    pem = ca.read_bytes()
    if pem.count(b"-----BEGIN CERTIFICATE-----") != 1 or b"PRIVATE KEY" in pem:
        raise RuntimeError("Bundle exactly one public CA certificate, never a private key")
    run([javac, "-encoding", "UTF-8", "-d", helpers, ANDROID / "tools/VerifyCa.java"])
    run([java, "-cp", helpers, "VerifyCa", ca])
    print("Compiling Android resources...", flush=True)
    manifest = ET.parse(APP / "AndroidManifest.xml")
    manifest.getroot().set("package", "ai.storywriter.mobile")
    manifest.write(BUILD / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    run([aapt, "compile", "--dir", APP / "res", "-o", BUILD / "resources.zip"])
    run([aapt, "link", "-I", framework, "--manifest", BUILD / "AndroidManifest.xml", "--java", generated,
         "--min-sdk-version", "26", "--target-sdk-version", "36", "--version-code", "8", "--version-name", "1.3.0",
         "-o", BUILD / "resources.apk", BUILD / "resources.zip"])
    source = list((APP / "java").rglob("*.java")) + list(generated.rglob("*.java"))
    run([javac, "-encoding", "UTF-8", "--release", "8", "-classpath", framework, "-d", classes, *source])
    print("Testing connection validation...", flush=True)
    run([javac, "-encoding", "UTF-8", "--release", "8", "-d", helpers,
         APP / "java/ai/storywriter/mobile/ServerAddress.java", ANDROID / "tests/ServerAddressTest.java"])
    run([java, "-cp", helpers, "ai.storywriter.mobile.ServerAddressTest"])
    with zipfile.ZipFile(BUILD / "classes.jar", "w") as archive:
        for file in classes.rglob("*.class"): archive.write(file, file.relative_to(classes).as_posix())
    print("Compiling Android bytecode...", flush=True)
    run([java, "-cp", CACHE / "r8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "26", "--lib", framework, "--lib", java_bin.parent,
         "--output", dex, BUILD / "classes.jar"])
    aligned_apk(BUILD / "resources.apk", dex, BUILD / "unsigned.apk")
    verify_alignment(BUILD / "unsigned.apk")
    SIGNING.mkdir(parents=True, exist_ok=True)
    password_file, keystore = SIGNING / "password.txt", SIGNING / "release.p12"
    if keystore.exists() and not password_file.exists():
        raise RuntimeError("Existing signing key has no password file. Restore it from your private backup.")
    if not password_file.exists(): password_file.write_text(secrets.token_urlsafe(36), encoding="utf-8")
    env = dict(os.environ, STORY_WRITER_SIGNING_PASSWORD=password_file.read_text(encoding="utf-8").strip())
    if not keystore.exists():
        run([java_bin / "keytool.exe", "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12",
             "-storepass:env", "STORY_WRITER_SIGNING_PASSWORD", "-alias", "story-writer", "-keyalg", "RSA",
             "-keysize", "3072", "-validity", "10000", "-dname", "CN=Story Writer Local Android, O=Story Writer", "-noprompt"], env=env)
    run([javac, "-encoding", "UTF-8", "-cp", CACHE / "apksig.jar", "-d", helpers, ANDROID / "tools/SignApk.java"])
    run([java, "-cp", os.pathsep.join([str(helpers), str(CACHE / "apksig.jar")]), "SignApk", keystore,
         BUILD / "unsigned.apk", BUILD / "story-writer-android.apk"], env=env)
    verify_alignment(BUILD / "story-writer-android.apk")
    with zipfile.ZipFile(BUILD / "story-writer-android.apk") as packaged:
        if packaged.read("res/raw/story_writer_ca.pem") != pem:
            raise RuntimeError("The APK did not retain the verified CA certificate")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(BUILD / "story-writer-android.apk")], text=True)
    for required in ("name='ai.storywriter.mobile'", "minSdkVersion:'26'", "targetSdkVersion:'36'", "name='ai.storywriter.mobile.MainActivity'"):
        if required not in badging: raise RuntimeError(f"Packaged manifest is missing {required}")
    permissions = [line for line in badging.splitlines() if line.startswith("uses-permission:")]
    if permissions != ["uses-permission: name='android.permission.INTERNET'"]:
        raise RuntimeError(f"Unexpected packaged permissions: {permissions}")
    # Publish only after compilation, tests, alignment, and signature checks pass.
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(BUILD / "story-writer-android.apk", OUTPUT.with_suffix(".apk.tmp"))
    OUTPUT.with_suffix(".apk.tmp").replace(OUTPUT)
    sha = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    (OUTPUT.parent / "yumina-android.sha256").write_text(f"{sha}  {OUTPUT.name}\n", encoding="ascii")
    print(f"Built and verified: {OUTPUT}\nSHA-256: {sha}", flush=True)


if __name__ == "__main__": main()
