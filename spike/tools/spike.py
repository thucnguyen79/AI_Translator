#!/usr/bin/env python3
"""Spike helper: fetch dependencies and models, push them to a phone, pull and summarise results.

Usage (from the spike/ folder):
    python tools/spike.py deps                        # sherpa-onnx AAR -> app/libs
    python tools/spike.py models --asr vi-zipformer-30m sensevoice-2024 --vad --mt vi zh ko ja
    python tools/spike.py push                        # models/ (+ testset/audio) -> phone
    python tools/spike.py pull                        # phone results -> results/<device>/
    python tools/spike.py report                      # markdown tables from results/

Only the Python standard library is required; `pip install sacrebleu` makes `report` re-score chrF.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tarfile
import urllib.request
from pathlib import Path

SPIKE = Path(__file__).resolve().parent.parent
MODELS = SPIKE / "models"
RESULTS = SPIKE / "results"
PACKAGE = "io.github.thucnguyen79.aitranslator.spike"
DEVICE_ROOT = f"/sdcard/Android/data/{PACKAGE}/files"

SHERPA_VERSION = "2.0.1"
SHERPA_AAR_SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
SHERPA_RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

# id -> archive name in https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models (must match AsrCatalog)
ASR_MODELS = {
    "vi-zipformer-30m": "sherpa-onnx-zipformer-vi-30M-int8-2026-02-09",
    "vi-zipformer-70kh": "sherpa-onnx-zipformer-vi-int8-2025-04-20",
    "sensevoice-2024": "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17",
    "sensevoice-2025": "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09",
    "whisper-tiny": "sherpa-onnx-whisper-tiny",
    "whisper-base": "sherpa-onnx-whisper-base",
    "whisper-small": "sherpa-onnx-whisper-small",
}

# Firefox Translations (Bergamot) model registry.
REMOTE_SETTINGS = "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models/records"
ATTACHMENTS = "https://firefox-settings-attachments.cdn.mozilla.net/"
MOZ_LANG = {"zh": "zh-Hans"}
# Keep release builds usable on Android; skip desktop-only and nightly/beta-only records.
OK_FILTERS = {"", "env.appinfo.OS == 'Android'"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path, expected_sha256: str | None = None) -> Path:
    if dest.is_file() and (expected_sha256 is None or sha256(dest) == expected_sha256):
        print(f"  ✓ {dest.relative_to(SPIKE)}")
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"  ↓ {url}")
    with urllib.request.urlopen(url) as r, tmp.open("wb") as f:
        total = int(r.headers.get("Content-Length") or 0)
        done = 0
        while chunk := r.read(1 << 20):
            f.write(chunk)
            done += len(chunk)
            if total:
                print(f"\r    {done / 1e6:7.1f} / {total / 1e6:.1f} MB", end="", flush=True)
    print()
    if expected_sha256 and sha256(tmp) != expected_sha256:
        tmp.unlink()
        sys.exit(f"SHA-256 mismatch for {url}")
    tmp.replace(dest)
    return dest


def cmd_deps(_: argparse.Namespace) -> None:
    aar = SPIKE / "app" / "libs" / f"sherpa-onnx-{SHERPA_VERSION}.aar"
    download(f"{SHERPA_RELEASES}/v{SHERPA_VERSION}/sherpa-onnx-{SHERPA_VERSION}.aar", aar, SHERPA_AAR_SHA256)


def fetch_asr(model_id: str) -> None:
    name = ASR_MODELS[model_id]
    target = MODELS / "asr" / name
    if target.is_dir():
        print(f"  ✓ models/asr/{name}")
        return
    archive = download(f"{SHERPA_RELEASES}/asr-models/{name}.tar.bz2", MODELS / "_downloads" / f"{name}.tar.bz2")
    print(f"  ⇲ giải nén {archive.name}")
    with tarfile.open(archive, "r:bz2") as tar:
        try:
            tar.extractall(MODELS / "asr", filter="data")
        except TypeError:  # Python < 3.10.12 has no extraction filters
            tar.extractall(MODELS / "asr")
    # Drop test wavs and fp32 duplicates when an int8 variant exists, to keep the push small.
    for f in target.rglob("*"):
        if f.is_dir() and f.name == "test_wavs":
            shutil.rmtree(f)
    for f in list(target.glob("*.onnx")):
        if ".int8." not in f.name and (target / f.name.replace(".onnx", ".int8.onnx")).exists():
            f.unlink()


def fetch_vad() -> None:
    download(f"{SHERPA_RELEASES}/asr-models/silero_vad.onnx", MODELS / "vad" / "silero_vad.onnx")


def fetch_bergamot(langs: list[str]) -> None:
    print("  ↓ danh mục mô hình Firefox Translations")
    with urllib.request.urlopen(REMOTE_SETTINGS) as r:
        records = json.load(r)["data"]
    for lang in langs:
        moz = MOZ_LANG.get(lang, lang)
        for src, trg, key in ((moz, "en", f"{lang}en"), ("en", moz, f"en{lang}")):
            cands = [x for x in records if x["fromLang"] == src and x["toLang"] == trg
                     and x.get("filter_expression", "").strip() in OK_FILTERS]
            versions = {x["version"] for x in cands if x["fileType"] == "model"}
            if not versions:
                print(f"  ✗ không có mô hình {src}→{trg}")
                continue
            version = max(versions, key=version_key)
            files = [x for x in cands if x["version"] == version]
            # Same version may exist both unfiltered and Android-only: keep one per file type.
            by_type: dict[str, dict] = {}
            for x in sorted(files, key=lambda x: x.get("filter_expression", "")):
                by_type.setdefault(x["fileType"], x)
            for x in by_type.values():
                a = x["attachment"]
                download(ATTACHMENTS + a["location"], MODELS / "bergamot" / key / a["filename"], a["hash"])
            print(f"  ✓ {key} v{version}: {', '.join(sorted(by_type))}")


def version_key(v: str) -> tuple:
    """'2.1' > '2.0' > '2.0a2' > '2.0a1'."""
    main, _, pre = v.partition("a")
    nums = tuple(int(p) for p in main.split(".") if p.isdigit())
    return nums + ((1, 0) if not pre else (0, int(pre or 0)))


def cmd_models(args: argparse.Namespace) -> None:
    for m in args.asr or []:
        if m not in ASR_MODELS:
            sys.exit(f"ASR model không rõ: {m}. Chọn trong: {', '.join(ASR_MODELS)}")
        fetch_asr(m)
    if args.vad:
        fetch_vad()
    if args.mt:
        fetch_bergamot(args.mt)
    if args.clean:
        shutil.rmtree(MODELS / "_downloads", ignore_errors=True)


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    exe = shutil.which("adb") or "adb"
    return subprocess.run([exe, *args], check=check, text=True, capture_output=True)


def cmd_push(args: argparse.Namespace) -> None:
    adb("shell", "mkdir", "-p", f"{DEVICE_ROOT}/models", f"{DEVICE_ROOT}/testset")
    for sub in ("asr", "vad", "bergamot"):
        src = MODELS / sub
        if src.is_dir():
            print(f"  → models/{sub}")
            adb("push", str(src), f"{DEVICE_ROOT}/models/")
    audio = SPIKE / "testset" / "audio"
    if args.audio and audio.is_dir():
        print("  → testset/audio")
        adb("push", str(audio), f"{DEVICE_ROOT}/testset/")


def cmd_pull(_: argparse.Namespace) -> None:
    model = adb("shell", "getprop", "ro.product.model").stdout.strip().replace(" ", "_") or "device"
    dest = RESULTS / model
    dest.mkdir(parents=True, exist_ok=True)
    adb("pull", f"{DEVICE_ROOT}/results/.", str(dest))
    print(f"  ✓ {dest.relative_to(SPIKE)}")


def chrf(hyps: list[str], refs: list[str]) -> float | None:
    try:
        import sacrebleu  # type: ignore
    except ImportError:
        return None
    return sacrebleu.corpus_chrf(hyps, [refs]).score


def cmd_report(_: argparse.Namespace) -> None:
    asr, mt, tts, conv = [], [], [], []
    for f in sorted(RESULTS.rglob("*.json*")):
        dev = f.parent.name
        if f.suffix == ".jsonl":
            conv += [dict(json.loads(l), device=dev) for l in f.read_text(encoding="utf-8").splitlines() if l.strip()]
            continue
        d = json.loads(f.read_text(encoding="utf-8"))
        {"asr": asr, "mt": mt, "tts": tts}.get(d.get("type"), []).append(d)

    def device(d: dict) -> str:
        return d["device"]["model"]

    if asr:
        print("\n### ASR\n")
        print("| Máy | Mô hình | Ngôn ngữ | Luồng | Số câu | CER | WER | Giải mã TB (ms) | p95 (ms) | RTF | Nạp (ms) | RAM (MB) |")
        print("|---|---|---|---|---|---|---|---|---|---|---|---|")
        for d in asr:
            s = d["summary"]
            wer = f"{s['wer'] * 100:.1f}%" if s.get("wer") is not None else "—"
            print(f"| {device(d)} | {d['model']} | {d['lang']} | {d['threads']} | {s['files']} | {s['cer'] * 100:.1f}% | {wer} "
                  f"| {s['decodeMsMean']:.0f} | {s['decodeMsP95']:.0f} | {s['rtfMean']:.3f} | {d['loadMs']} | {d['modelPssMb']:.0f} |")
    if mt:
        print("\n### Dịch\n")
        print("| Máy | Bộ dịch | Hướng | Qua en | chrF (máy) | chrF (sacrebleu) | TB (ms) | p95 (ms) |")
        print("|---|---|---|---|---|---|---|---|")
        for d in mt:
            for x in d["directions"]:
                s = x["summary"]
                sb = chrf([i["hyp"] for i in x["items"]], [i["ref"] for i in x["items"]])
                print(f"| {device(d)} | {d['engine']} | {x['from']}→{x['to']} | {'có' if x['pivot'] else ''} "
                      f"| {s['chrF']:.1f} | {'' if sb is None else f'{sb:.1f}'} | {s['msMean']:.0f} | {s['msP95']:.0f} |")
    if tts:
        print("\n### TTS (Android)\n")
        print("| Máy | Engine | " + " | ".join(l["lang"] for l in tts[0]["langs"]) + " |")
        print("|---|---|" + "---|" * len(tts[0]["langs"]))
        for d in tts:
            cells = []
            for l in d["langs"]:
                ok = "✓" if l["offlineVoices"] else "✗"
                cells.append(f"{ok} {l['timeToAudioMs']} ms" if l["timeToAudioMs"] is not None else ok)
            print(f"| {device(d)} | {d['engine']} | " + " | ".join(cells) + " |")
    if conv:
        print("\n### Hội thoại (end-to-end)\n")
        print("| Máy | Hướng | Số lượt | ASR TB (ms) | Dịch TB (ms) | → chữ TB (ms) | TTS bắt đầu TB (ms) |")
        print("|---|---|---|---|---|---|---|")
        groups: dict[tuple, list] = {}
        for t in conv:
            groups.setdefault((t["device"], f"{t['from']}→{t['to']}"), []).append(t)
        for (dev, direction), ts in sorted(groups.items()):
            mean = lambda k: sum(t[k] for t in ts if t.get(k) is not None) / max(1, sum(1 for t in ts if t.get(k) is not None))
            print(f"| {dev} | {direction} | {len(ts)} | {mean('asrMs'):.0f} | {mean('mtMs'):.0f} "
                  f"| {mean('asrMs') + mean('mtMs'):.0f} | {mean('ttsStartMs'):.0f} |")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):  # Windows consoles default to cp1252
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("deps", help="tải sherpa-onnx AAR").set_defaults(fn=cmd_deps)
    m = sub.add_parser("models", help="tải mô hình về spike/models")
    m.add_argument("--asr", nargs="*", metavar="ID", help=", ".join(ASR_MODELS))
    m.add_argument("--vad", action="store_true", help="Silero VAD")
    m.add_argument("--mt", nargs="*", metavar="LANG", help="Bergamot, vd: vi zh ko ja (cả 2 chiều với en)")
    m.add_argument("--clean", action="store_true", help="xoá file nén sau khi giải nén")
    m.set_defaults(fn=cmd_models)
    ps = sub.add_parser("push", help="đẩy mô hình lên điện thoại qua adb")
    ps.add_argument("--audio", action="store_true", help="đẩy cả testset/audio")
    ps.set_defaults(fn=cmd_push)
    sub.add_parser("pull", help="lấy kết quả đo về spike/results/<máy>").set_defaults(fn=cmd_pull)
    sub.add_parser("report", help="in bảng markdown từ spike/results").set_defaults(fn=cmd_report)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
