#!/usr/bin/env python3
"""Fetch build dependencies that are not on Maven Central / not checked in.

    python tools/deps.py            # everything below
    python tools/deps.py sherpa     # sherpa-onnx AAR -> third_party/m2 (SHA-256 checked)
    python tools/deps.py bergamot   # only the Bergamot submodules the Android arm64 build needs

Standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SHERPA_VERSION = "2.0.1"
# The static-link build embeds ONNX Runtime, so onnxruntime-android can ship its own libonnxruntime.so:
# ORT versions its symbols (OrtGetApiBase@VERS_1.28.2 vs @VERS_1.28.0), two builds cannot share one .so.
SHERPA_URL = (
    f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{SHERPA_VERSION}/"
    f"sherpa-onnx-static-link-onnxruntime-{SHERPA_VERSION}.aar"
)
SHERPA_SHA256 = "07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575"
SHERPA_M2 = ROOT / "third_party" / "m2" / "com" / "k2fsa" / "sherpa" / "onnx" / "sherpa-onnx-android" / SHERPA_VERSION

BERGAMOT_DIR = ROOT / "third_party" / "bergamot-translator"
# (repo relative to BERGAMOT_DIR, submodules needed for arm64, submodules to skip — fbgemm, nccl, tests… are GBs)
BERGAMOT_SUBMODULES = [
    (".", ["3rd_party/marian-dev", "3rd_party/ssplit-cpp"], ["bergamot-translator-tests", "3rd_party/pybind11"]),
    ("3rd_party/marian-dev", ["src/3rd_party/sentencepiece", "src/3rd_party/ruy", "src/3rd_party/simd_utils"],
     ["examples", "regression-tests", "src/3rd_party/nccl", "src/3rd_party/fbgemm", "src/3rd_party/intgemm",
      "src/3rd_party/simple-websocket-server", "src/3rd_party/onnxjs"]),
    ("3rd_party/marian-dev/src/3rd_party/ruy", ["third_party/cpuinfo"], ["third_party/googletest"]),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_sherpa() -> None:
    dest = SHERPA_M2 / f"sherpa-onnx-android-{SHERPA_VERSION}.aar"
    if dest.is_file() and sha256(dest) == SHERPA_SHA256:
        print(f"  ✓ {dest.relative_to(ROOT)}")
        return
    print(f"  ↓ {SHERPA_URL}")
    tmp = dest.with_suffix(".part")
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(SHERPA_URL) as r, tmp.open("wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
    if sha256(tmp) != SHERPA_SHA256:
        tmp.unlink()
        sys.exit("SHA-256 mismatch for the sherpa-onnx AAR")
    tmp.replace(dest)
    print(f"  ✓ {dest.relative_to(ROOT)}")


def fetch_bergamot() -> None:
    def git(*args: str, cwd: Path) -> None:
        subprocess.run(["git", *args], cwd=cwd, check=True)

    git("submodule", "update", "--init", str(BERGAMOT_DIR.relative_to(ROOT)), cwd=ROOT)
    for repo, needed, skipped in BERGAMOT_SUBMODULES:
        cwd = BERGAMOT_DIR / repo
        for s in skipped:
            git("config", f"submodule.{s}.update", "none", cwd=cwd)
        git("submodule", "update", "--init", "--depth", "1", *needed, cwd=cwd)
    print("  ✓ third_party/bergamot-translator")


def main() -> None:
    for stream in (sys.stdout, sys.stderr):  # Windows consoles default to cp1252
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("what", nargs="?", choices=["all", "sherpa", "bergamot"], default="all")
    what = p.parse_args().what
    if what in ("all", "sherpa"):
        fetch_sherpa()
    if what in ("all", "bergamot"):
        fetch_bergamot()


if __name__ == "__main__":
    main()
