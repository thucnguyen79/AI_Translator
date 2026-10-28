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

Yêu cầu: JDK 17+, Android SDK (compileSdk 37), Python 3.10+, `adb`, điện thoại Android 9+ arm64 bật USB debugging.

```bash
cd spike
python tools/spike.py deps                                   # sherpa-onnx AAR (kiểm SHA-256)
python tools/spike.py bergamot                               # mã nguồn Bergamot (submodule)
python tools/spike.py models --vad --asr vi-zipformer-30m sensevoice-2024 --mt vi zh ko ja
./gradlew installDebug                                       # hoặc gradlew.bat trên Windows
python tools/spike.py push                                   # đẩy models/ lên máy (tự mở app lần đầu)
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

## Bergamot (dịch mã nguồn mở)

Bergamot không có bản build Android sẵn nên app biên dịch nó từ mã nguồn. Mã nằm ở submodule
`app/src/main/cpp/bergamot`, trỏ tới bản fork [niedev/bergamot-translator](https://github.com/niedev/bergamot-translator)
của tác giả RTranslator (đã có cấu hình build Android). Sau khi clone repo, khởi tạo đúng các phần cần cho arm64
(bỏ qua fbgemm, nccl, bộ test… nặng vài GB):

```bash
python tools/spike.py bergamot
```

Build cần NDK 28.2.13676358 và CMake 3.22.1 trong Android SDK; lần đầu biên dịch Marian mất khá lâu.
Nếu thư mục `app/src/main/cpp/bergamot` trống, app vẫn build được nhưng chỉ dịch bằng ML Kit.
Tab **Mô hình** cho biết APK có thư viện Bergamot hay không.

## Tiêu chí quyết định (đề xuất)

| Hạng mục | Đạt nếu (máy tầm trung) |
|---|---|
| ASR tiếng Việt | CER ≤ 15% (yên tĩnh), ≤ 25% (ồn); giải mã p95 ≤ 1 s với câu ≤ 5 s |
| ASR zh/ja/ko/en | CER ≤ 15% (yên tĩnh) |
| Dịch | chrF của Bergamot không thấp hơn ML Kit quá 5 điểm; các hướng qua tiếng Anh được chấm tay ≥ 3,5/5 |
| TTS | Có giọng offline cho đủ 5 ngôn ngữ trên ≥ 2/3 máy thử; nếu không → bổ sung sherpa-onnx TTS |
| Hội thoại | Từ lúc VAD báo hết câu đến lúc hiện chữ dịch ≤ 2,5 s (trung vị) |

## Kết quả

Sau mỗi đợt đo, dán bảng từ `python tools/spike.py report` vào đây kèm nhận xét và quyết định.

### Đợt 1 — 2026-10-28 · OPPO CPH2357 (MediaTek Dimensity, 8 nhân, RAM 12 GB, Android 14)

**Dịch** — 100 câu du lịch, từng câu một; chrF tính trên máy, 1 bản tham chiếu:

| Hướng | Qua en | Bergamot chrF | ML Kit chrF | Bergamot TB / p95 (ms) | ML Kit TB / p95 (ms) |
|---|---|---|---|---|---|
| vi→en |  | **63.2** | 59.7 | 12 / 16 | 20 / 26 |
| en→vi |  | **63.9** | 61.0 | 33 / 45 | 20 / 25 |
| vi→zh | có | 36.0 | **36.3** | 41 / 53 | 38 / 52 |
| zh→vi | có | **57.3** | 53.3 | 66 / 88 | 36 / 48 |
| vi→ko | có | **24.8** | 21.1 | 44 / 59 | 40 / 52 |
| ko→vi | có | **54.5** | 44.8 | 64 / 82 | 36 / 50 |
| vi→ja | có | **38.5** | 37.2 | 43 / 59 | 43 / 56 |
| ja→vi | có | **57.4** | 50.2 | 69 / 88 | 37 / 51 |

Bergamot: nạp 8 mô hình 1,0 s, RAM +434 MB.

- Bergamot **ngang hoặc tốt hơn** ML Kit ở cả 8 hướng → đạt tiêu chí, giữ Bergamot làm bộ dịch chính.
- Tốc độ không phải vấn đề: < 100 ms/câu kể cả khi dịch qua tiếng Anh.
- Điểm yếu chung của cả hai: **văn nói du lịch tiếng Việt → tiếng Anh**, lỗi lan sang mọi hướng vi→zh/ko/ja.
  Ví dụ "Cho tôi xem thực đơn" → "Can I show you the menu?", "Cho tôi mang về" → "Let me bring it back",
  "Tôi bị móc túi" → "I've been pick-ached", "Làm ơn bật đồng hồ tính tiền" → "Please turn on the cashier".
  → Ưu tiên **cụm từ du lịch soạn sẵn** (F7.2) và **dịch ngược để kiểm tra** (F4.5); cân nhắc mô hình lớn hơn cho vi→en ở v2.
- chrF của hướng sang zh/ko/ja thấp hơn một phần do chấm theo ký tự với 1 bản tham chiếu; cần chấm tay để kết luận.

**TTS (Google TTS của máy)**:

| vi | en | zh | ko | ja |
|---|---|---|---|---|
| ✓ 683 ms | ✓ 805 ms | ✓ 719 ms | ✓ 762 ms | ✗ chưa có giọng offline |

- 4/5 ngôn ngữ có giọng offline sẵn; tiếng Nhật phải tải dữ liệu giọng → app cần hướng dẫn tải (F5.4).
- Thời gian tới khi phát 0,5–0,8 s là đáng kể trong ngân sách độ trễ; cần đo lại sau lần gọi đầu.

**ASR**: chưa đo — cần ghi âm bộ thử (tab **Ghi âm**).
