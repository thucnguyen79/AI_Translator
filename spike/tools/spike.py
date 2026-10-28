#!/usr/bin/env python3
"""Spike helper: fetch dependencies and models, push them to a phone, pull and summarise results.

Usage (from the spike/ folder):
    python tools/spike.py deps                        # sherpa-onnx AAR -> app/libs
    python tools/spike.py bergamot                    # Bergamot submodules for the Android build
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
import os
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
from pathlib import Path

SPIKE = Path(__file__).resolve().parent.parent
MODELS = SPIKE / "models"
RESULTS = SPIKE / "results"
PACKAGE = "io.github.thucnguyen79.aitranslator.spike"
DEVICE_ROOT = f"/sdcard/Android/data/{PACKAGE}/files"

SHERPA_VERSION = "2.0.1"
# The static-link build embeds ONNX Runtime, so the app can use onnxruntime-android's own libonnxruntime.so
# for other models: ORT versions its symbols (OrtGetApiBase@VERS_1.28.2 vs @VERS_1.28.0), so two builds
# cannot share one .so.
SHERPA_AAR = f"sherpa-onnx-static-link-onnxruntime-{SHERPA_VERSION}.aar"
SHERPA_AAR_SHA256 = "07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575"
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
    download(f"{SHERPA_RELEASES}/v{SHERPA_VERSION}/{SHERPA_AAR}", SPIKE / "app" / "libs" / SHERPA_AAR, SHERPA_AAR_SHA256)


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


TTS_SUPERTONIC = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"


def fetch_tts() -> None:
    target = MODELS / "tts" / TTS_SUPERTONIC
    if target.is_dir():
        print(f"  ✓ models/tts/{TTS_SUPERTONIC}")
        return
    archive = download(f"{SHERPA_RELEASES}/tts-models/{TTS_SUPERTONIC}.tar.bz2", MODELS / "_downloads" / f"{TTS_SUPERTONIC}.tar.bz2")
    print(f"  ⇲ giải nén {archive.name}")
    with tarfile.open(archive, "r:bz2") as tar:
        try:
            tar.extractall(MODELS / "tts", filter="data")
        except TypeError:
            tar.extractall(MODELS / "tts")


# Vietnamese punctuation + case restoration (dragonSwing/vibert-capu, CC-BY-SA-4.0; ONNX by welcomyou)
PUNCT_REPO = "https://huggingface.co/welcomyou/vibert-capu-onnx/resolve/main"
PUNCT_FILES = {  # published path -> (local name, sha256)
    "vibert-capu.int8.onnx": ("vibert-capu.int8.onnx", "67278b23502bbd744538e2bd9a6748b61cce171cfba6c671c2e2a46f892166fa"),
    "vocab.txt": ("vocab.txt", "b32ccb4ca8bee5eda7a0f55f7adebaa515be742c9f765151ffefcd29fcb542a1"),
    "vocabulary/labels.txt": ("labels.txt", "a31075cfa185b5d24c3b65c009ad0740636b286090f78419eaba2d34202c2b45"),
}


def fetch_punct() -> None:
    for remote, (name, digest) in PUNCT_FILES.items():
        download(f"{PUNCT_REPO}/{remote}", MODELS / "punct" / "vibert-capu" / name, digest)


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
    if args.tts:
        fetch_tts()
    if args.punct:
        fetch_punct()
    if args.mt:
        fetch_bergamot(args.mt)
    if args.clean:
        shutil.rmtree(MODELS / "_downloads", ignore_errors=True)


def cmd_bergamot(_: argparse.Namespace) -> None:
    """The Bergamot submodule is shared with the app (third_party/); tools/deps.py initialises it."""
    subprocess.run([sys.executable, str(SPIKE.parent / "tools" / "deps.py"), "bergamot"], check=True)


def find_adb() -> str:
    """adb from PATH, else from the Android SDK (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties)."""
    found = shutil.which("adb")
    if found:
        return found
    sdks = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    props = SPIKE / "local.properties"
    if props.is_file():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdks.append(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    for sdk in filter(None, sdks):
        for name in ("adb.exe", "adb"):
            exe = Path(sdk) / "platform-tools" / name
            if exe.is_file():
                return str(exe)
    sys.exit("Không tìm thấy adb: thêm <Android SDK>/platform-tools vào PATH hoặc đặt ANDROID_HOME")


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run([find_adb(), *args], text=True, capture_output=True, encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} lỗi:\n{result.stderr.strip() or result.stdout.strip()}")
    return result


def push_tree(local: Path, remote: str) -> None:
    """Pushes a folder file by file. Some phones (e.g. ColorOS 14) refuse to let `adb push` create folders
    under Android/data ("secure_mkdirs() failed"), while `adb shell mkdir -p` works, so folders come first."""
    dirs = [local, *sorted(d for d in local.rglob("*") if d.is_dir())]
    remote_dirs = [f"{remote}/{d.relative_to(local).as_posix()}".rstrip("/.") for d in dirs]
    adb("shell", "mkdir", "-p", *remote_dirs)
    for d, rd in zip(dirs, remote_dirs):
        files = sorted(str(f) for f in d.iterdir() if f.is_file())
        if files:
            print(f"  → {rd.removeprefix(DEVICE_ROOT + '/')} ({len(files)} file)")
            adb("push", "--sync", *files, rd + "/")


def cmd_push(args: argparse.Namespace) -> None:
    # Folders created by adb before the app's first launch belong to `shell` and the app cannot read
    # them, so let the app create its own external files dir first.
    if adb("shell", "ls", DEVICE_ROOT, check=False).returncode != 0:
        print("  ▶ mở app lần đầu để tạo thư mục dữ liệu")
        adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
        for _ in range(20):
            time.sleep(0.5)
            if adb("shell", "ls", DEVICE_ROOT, check=False).returncode == 0:
                break
        else:
            sys.exit("App chưa tạo thư mục dữ liệu: cài app (gradlew installDebug) và mở nó một lần")
    for sub in ("asr", "vad", "bergamot", "tts", "punct"):
        src = MODELS / sub
        if src.is_dir():
            push_tree(src, f"{DEVICE_ROOT}/models/{sub}")
    audio = SPIKE / "testset" / "audio"
    if args.audio and audio.is_dir():
        push_tree(audio, f"{DEVICE_ROOT}/testset/audio")


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
        print("\n### TTS\n")
        codes = ["vi", "en", "zh", "ko", "ja"]
        print("| Máy | Engine | " + " | ".join(codes) + " |")
        print("|---|---|" + "---|" * len(codes))
        for d in tts:
            by_lang = {l["lang"]: l for l in d["langs"]}
            cells = []
            for c in codes:
                l = by_lang.get(c)
                if l is None:
                    cells.append("—")
                    continue
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
    for stream in (sys.stdout, sys.stderr):  # Windows consoles default to cp1252
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("deps", help="tải sherpa-onnx AAR").set_defaults(fn=cmd_deps)
    sub.add_parser("bergamot", help="khởi tạo submodule Bergamot cần cho Android").set_defaults(fn=cmd_bergamot)
    m = sub.add_parser("models", help="tải mô hình về spike/models")
    m.add_argument("--asr", nargs="*", metavar="ID", help=", ".join(ASR_MODELS))
    m.add_argument("--vad", action="store_true", help="Silero VAD")
    m.add_argument("--tts", action="store_true", help="Supertonic 3 TTS (vi/en/ko/ja, ~130 MB)")
    m.add_argument("--punct", action="store_true", help="vibert-capu: dấu câu + chữ hoa tiếng Việt (~110 MB)")
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
