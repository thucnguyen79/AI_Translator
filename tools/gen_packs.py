#!/usr/bin/env python3
"""Generate core/packs/src/main/assets/packs.json: the language-pack catalog the app downloads from.

Every file gets its URL, size and SHA-256 from the publisher's API (GitHub release asset digests, the
Firefox Translations registry, Hugging Face LFS metadata), so the app can verify downloads.

    python tools/gen_packs.py

Standard library only. Re-run when a model version changes and bump CATALOG_VERSION.
"""
from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "core" / "packs" / "src" / "main" / "assets" / "packs.json"
CATALOG_VERSION = 2

SHERPA = "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/{tag}"
FIREFOX = "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models/records"
FIREFOX_CDN = "https://firefox-settings-attachments.cdn.mozilla.net/"
HF_API = "https://huggingface.co/api/models/{repo}?blobs=true"
HF_FILE = "https://huggingface.co/{repo}/resolve/main/{path}"

VAD = ("asr-models", "silero_vad.onnx")
ASR_VI = ("asr-models", "sherpa-onnx-zipformer-vi-30M-int8-2026-02-09.tar.bz2")
ASR_MULTI = ("asr-models", "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2")
TTS_SUPERTONIC = ("tts-models", "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2")
# Spoken language identification (F3.4): Whisper tiny's int8 encoder + decoder (tokens are not needed for LID).
LID_REPO = "csukuangfj/sherpa-onnx-whisper-tiny"
LID_FILES = {"tiny-encoder.int8.onnx": "encoder.int8.onnx", "tiny-decoder.int8.onnx": "decoder.int8.onnx"}
PUNCT_REPO = "welcomyou/vibert-capu-onnx"
PUNCT_FILES = {"vibert-capu.int8.onnx": "vibert-capu.int8.onnx", "vocab.txt": "vocab.txt", "vocabulary/labels.txt": "labels.txt"}
# Small text files are not in Git LFS, so Hugging Face reports no hash: pinned here (checked when generated).
PUNCT_SMALL_SHA256 = {
    "vocab.txt": "b32ccb4ca8bee5eda7a0f55f7adebaa515be742c9f765151ffefcd29fcb542a1",
    "vocabulary/labels.txt": "a31075cfa185b5d24c3b65c009ad0740636b286090f78419eaba2d34202c2b45",
}
MOZ_LANG = {"zh": "zh-Hans"}
OK_FILTERS = {"", "env.appinfo.OS == 'Android'"}


def get_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "aitranslator-gen-packs"})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


_release_cache: dict[str, dict] = {}


def sherpa_asset(tag: str, name: str) -> dict:
    if tag not in _release_cache:
        release = get_json(SHERPA.format(tag=tag))
        assets, page = {}, 1
        while True:
            batch = get_json(f"https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/{release['id']}/assets?per_page=100&page={page}")
            if not batch:
                break
            assets.update({a["name"]: a for a in batch})
            page += 1
        _release_cache[tag] = assets
    a = _release_cache[tag][name]
    digest = a.get("digest") or ""
    if not digest.startswith("sha256:"):
        sys.exit(f"No SHA-256 digest for {name}")
    return {"url": a["browser_download_url"], "size": a["size"], "sha256": digest.removeprefix("sha256:")}


def sherpa_file(tag_name: tuple[str, str], path: str, extract: bool = False) -> dict:
    f = sherpa_asset(*tag_name) | {"path": path}
    if extract:
        f["extract"] = "tar.bz2"
    return f


def bergamot_files(records: list, lang: str) -> list[dict]:
    moz = MOZ_LANG.get(lang, lang)
    out = []
    for src, trg, key in ((moz, "en", f"{lang}en"), ("en", moz, f"en{lang}")):
        cands = [x for x in records if x["fromLang"] == src and x["toLang"] == trg
                 and x.get("filter_expression", "").strip() in OK_FILTERS]
        version = max({x["version"] for x in cands if x["fileType"] == "model"}, key=version_key)
        by_type: dict[str, dict] = {}
        for x in sorted((x for x in cands if x["version"] == version), key=lambda x: x.get("filter_expression", "")):
            by_type.setdefault(x["fileType"], x)
        for x in by_type.values():
            a = x["attachment"]
            out.append({"url": FIREFOX_CDN + a["location"], "size": a["size"], "sha256": a["hash"], "path": f"mt/{key}/{a['filename']}"})
    return out


def version_key(v: str) -> tuple:
    main, _, pre = v.partition("a")
    nums = tuple(int(p) for p in main.split(".") if p.isdigit())
    return nums + ((1, 0) if not pre else (0, int(pre or 0)))


def hf_files(repo: str, files: dict[str, str], folder: str, pinned: dict[str, str] | None = None) -> list[dict]:
    meta = {s["rfilename"]: s for s in get_json(HF_API.format(repo=repo))["siblings"]}
    out = []
    for remote, local in files.items():
        s = meta[remote]
        sha = (s.get("lfs") or {}).get("sha256") or (pinned or {})[remote]
        out.append({"url": HF_FILE.format(repo=repo, path=remote), "size": s["size"], "sha256": sha, "path": f"{folder}/{local}"})
    return out


def punct_files() -> list[dict]:
    return hf_files(PUNCT_REPO, PUNCT_FILES, "punct", PUNCT_SMALL_SHA256)


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    records = get_json(FIREFOX)["data"]
    packs = [
        {"id": "core", "kind": "shared", "langs": [], "requires": [],
         "license": "MIT (Silero VAD)",
         "files": [sherpa_file(VAD, "vad/silero_vad.onnx")]},
        {"id": "speech-multi", "kind": "shared", "langs": ["en", "zh", "ko", "ja"], "requires": [],
         "license": "FunASR Model License (SenseVoice)",
         "files": [sherpa_file(ASR_MULTI, "asr", extract=True)]},
        {"id": "vi", "kind": "language", "langs": ["vi"], "requires": ["core"],
         "license": "Apache-2.0 (zipformer) · CC-BY-SA-4.0 (vibert-capu) · MPL-2.0 (Bergamot)",
         "files": [sherpa_file(ASR_VI, "asr", extract=True), *punct_files(), *bergamot_files(records, "vi")]},
        {"id": "en", "kind": "language", "langs": ["en"], "requires": ["core", "speech-multi"],
         "license": "", "files": []},
    ]
    for lang in ("zh", "ko", "ja"):
        packs.append({"id": lang, "kind": "language", "langs": [lang], "requires": ["core", "speech-multi"],
                      "license": "MPL-2.0 (Bergamot)", "files": bergamot_files(records, lang)})
    packs.append({"id": "tts-supertonic", "kind": "optional", "langs": ["vi", "en", "ko", "ja"], "requires": [],
                  "license": "OpenRAIL-M (Supertonic 3)",
                  "files": [sherpa_file(TTS_SUPERTONIC, "tts", extract=True)]})
    packs.append({"id": "lid", "kind": "optional", "langs": [], "requires": [],
                  "license": "MIT (Whisper tiny, OpenAI)",
                  "files": hf_files(LID_REPO, LID_FILES, "lid")})

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"version": CATALOG_VERSION, "packs": packs}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for p in packs:
        print(f"  {p['id']:15} {sum(f['size'] for f in p['files']) / 1e6:7.1f} MB  {len(p['files'])} file")
    print(f"  ✓ {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
