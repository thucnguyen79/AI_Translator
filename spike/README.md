# Giai đoạn 0 — Spike đo mô hình offline

App Android thử nghiệm để **đo trên điện thoại thật** chất lượng và tốc độ của các mô hình offline,
rồi chốt lựa chọn cho MVP (xem [docs/architecture.md](../docs/architecture.md) mục 6).

## App có gì

| Tab | Việc làm | Kết quả ghi ra `results/` |
|---|---|---|
| Mô hình | Thông tin máy, mô hình đã có, số file ghi âm | — |
| Hội thoại | Demo 2 người A/B: nói → nhận dạng → dịch → đọc, đo thời gian từng bước | `conversation-*.jsonl` |
| ASR | Đo CER/WER, thời gian giải mã, RTF, RAM trên bộ ghi âm; thử nhanh bằng micro | `asr-<model>-<lang>-t<threads>-*.json` |
| Dịch | Đo chrF và độ trễ trên 100 câu du lịch cho 8 hướng dịch; dịch thử | `mt-<engine>-*.json` |
| TTS | Kiểm tra giọng đọc offline của Android cho 5 ngôn ngữ, đo thời gian tới khi phát | `tts-android-*.json` |
| Ghi âm | Ghi bộ thử: đọc từng câu → lưu WAV 16 kHz theo người đọc | `testset/audio/<lang>/<người>/<id>.wav` |

Bộ câu: [app/src/main/assets/sentences.tsv](app/src/main/assets/sentences.tsv) — 100 câu du lịch song song vi/en/zh/ko/ja
(chào hỏi, ăn uống, đi lại, khách sạn, mua sắm, hỏi đường, khẩn cấp, giao tiếp). Đặt file `testset/sentences.tsv`
trên máy để thay bộ câu mà không cần build lại.

## Mô hình được so sánh

| Khối | Ứng viên | Ghi chú |
|---|---|---|
| ASR vi | `vi-zipformer-30m` (26 MB), `vi-zipformer-70kh` (60 MB), `whisper-base/small` | sherpa-onnx |
| ASR zh/en/ja/ko | `sensevoice-2024`, `sensevoice-2025` (~163 MB, dùng chung 4 tiếng), `whisper-*` | sherpa-onnx |
| VAD | Silero VAD | tự dừng sau 0,6 s im lặng |
| Dịch | **Bergamot** (Mozilla, MPL-2.0, qua tiếng Anh) · **ML Kit** (Google, mã đóng — chỉ làm mốc) | Bergamot cần bước bật thêm, xem dưới |
| TTS | Android `TextToSpeech` của máy | sherpa-onnx TTS để sau nếu thiếu giọng offline |

## Cách chạy

Yêu cầu: JDK 17+, Android SDK (compileSdk 37), Python 3.10+, `adb`, điện thoại Android 8+ arm64 bật USB debugging.

```bash
cd spike
python tools/spike.py deps                                   # sherpa-onnx AAR (kiểm SHA-256)
python tools/spike.py models --vad --asr vi-zipformer-30m sensevoice-2024 --mt vi zh ko ja
./gradlew installDebug                                       # hoặc gradlew.bat trên Windows
python tools/spike.py push                                   # đẩy models/ lên máy
```

Trên máy: mở app → tab **Mô hình** kiểm tra đủ ✓ → **Ghi âm** bộ thử → chạy **ASR**, **Dịch**, **TTS** → thử **Hội thoại**.

```bash
python tools/spike.py pull      # kéo results/ về spike/results/<tên máy>/
pip install sacrebleu           # tuỳ chọn: chấm lại chrF chuẩn
python tools/spike.py report    # in bảng markdown để dán vào báo cáo
```

Gợi ý ghi âm: mỗi ngôn ngữ ≥ 3 người đọc; với tiếng Việt cố gắng có giọng Bắc, Trung, Nam; ghi một lượt yên tĩnh và một lượt
có tiếng ồn nền (quán cà phê, đường phố) với mã người đọc khác nhau (vd `nam-bac-1`, `nam-bac-1-on`).
File ghi âm là dữ liệu giọng nói cá nhân nên **không đưa lên git** (`spike/testset/audio/` đã nằm trong `.gitignore`).

## Bật Bergamot (dịch mã nguồn mở)

Bergamot không có bản build Android sẵn; app tự biên dịch nó khi mã nguồn có trong `app/src/main/cpp/bergamot`.
Cách đã được dự án RTranslator dùng thành công là bản fork có sẵn cấu hình Android của tác giả RTranslator:

```bash
git submodule add https://github.com/niedev/bergamot-translator spike/app/src/main/cpp/bergamot
git -C spike/app/src/main/cpp/bergamot submodule update --init --depth 1 3rd_party/marian-dev 3rd_party/ssplit-cpp
```

Sau đó build lại (cần NDK 28.2.13676358 và CMake 3.22.1 trong Android SDK; lần đầu biên dịch Marian mất khá lâu).
Tab **Mô hình** sẽ hiện "Thư viện Bergamot trong APK: có". Phần JNI ở [app/src/main/cpp/bergamot_jni.cpp](app/src/main/cpp/bergamot_jni.cpp)
**chưa được biên dịch thử** — cần kiểm tra khi bật.

## Tiêu chí quyết định (đề xuất)

| Hạng mục | Đạt nếu (máy tầm trung) |
|---|---|
| ASR tiếng Việt | CER ≤ 15% (yên tĩnh), ≤ 25% (ồn); giải mã p95 ≤ 1 s với câu ≤ 5 s |
| ASR zh/ja/ko/en | CER ≤ 15% (yên tĩnh) |
| Dịch | chrF của Bergamot không thấp hơn ML Kit quá 5 điểm; các hướng qua tiếng Anh được chấm tay ≥ 3,5/5 |
| TTS | Có giọng offline cho đủ 5 ngôn ngữ trên ≥ 2/3 máy thử; nếu không → bổ sung sherpa-onnx TTS |
| Hội thoại | Từ lúc VAD báo hết câu đến lúc hiện chữ dịch ≤ 2,5 s (trung vị) |

## Kết quả

Chưa đo. Sau mỗi đợt đo, dán bảng từ `python tools/spike.py report` vào đây kèm nhận xét và quyết định.
