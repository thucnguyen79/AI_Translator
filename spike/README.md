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
| Dấu câu vi | vibert-capu (int8 110 MB, CC-BY-SA-4.0) qua ONNX Runtime · quy tắc dự phòng | mô hình zipformer-vi chỉ ra CHỮ IN HOA |
| TTS | Android `TextToSpeech` của máy · Supertonic 3 (sherpa-onnx) khi máy thiếu giọng offline | |

## Cách chạy

Yêu cầu: JDK 17+, Android SDK (compileSdk 37), Python 3.10+, `adb`, điện thoại Android 9+ arm64 bật USB debugging.

```bash
cd spike
python tools/spike.py deps                                   # sherpa-onnx AAR (kiểm SHA-256)
python tools/spike.py bergamot                               # mã nguồn Bergamot (submodule)
python tools/spike.py models --vad --asr vi-zipformer-30m sensevoice-2024 --mt vi zh ko ja --punct --tts
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

**ASR** — `vi-zipformer-30m`, 2 luồng, 8 câu đầu do 1 người đọc (yên tĩnh):

| Câu | CER | WER | Giải mã TB / p95 | RTF | Nạp | RAM |
|---|---|---|---|---|---|---|
| 8 | 0,0% | 0,0% | 71 / 90 ms | 0,027 | 1,2 s | +85 MB |

- Nhận dạng tiếng Việt chính xác và rất nhanh (câu 3 s giải mã < 0,1 s). Mẫu còn quá nhỏ — cần thêm người đọc,
  giọng vùng miền và tiếng ồn trước khi kết luận.
- Mô hình trả về **CHỮ IN HOA, không dấu câu** ("SAO BÀ KHÔNG HIỂU TÔI NÓI TIẾNG VIỆT À").

**Hội thoại** — lượt nói thật trong tab Hội thoại (tính từ lúc VAD báo hết câu):

| Hướng | Lượt | ASR | Dịch | → chữ | TTS bắt đầu |
|---|---|---|---|---|---|
| vi→en | 4 | 52 ms | 40 ms | 92 ms | 146 ms |
| en→vi | 1 | 144 ms | 207 ms | 351 ms | 137 ms |

Toàn bộ chuỗi xử lý sau khi nói xong chỉ mất **0,1–0,5 s** — thời gian chờ thực tế chủ yếu là 0,6 s im lặng để VAD
nhận ra hết câu. TTS khi đã khởi động chỉ ~0,15 s (con số 0,5–0,8 s ở trên là lần gọi đầu).

**Phát hiện: chữ IN HOA làm hỏng bản dịch.** Ở lượt nói thật, vi→en ra "HANDSELEN", "WHY DID NOT UNDERS UNDERSEAR…"
dù ASR nhận đúng. Kiểm chứng trên máy với cùng câu, chỉ khác cách viết:

| Đầu vào Bergamot | Bản dịch |
|---|---|
| XIN CHÀO | HANDSELEN |
| Xin chào. | Hello. |
| BẠN CÓ THỂ NÓI CHẬM HƠN ĐƯỢC KHÔNG | CAN YOU SAY LESS THAN |
| Bạn có thể nói chậm hơn được không? | Can you speak more slowly? |
| XIN LỖI BẠN CÓ NÓI ĐƯỢC TIẾNG ANH KHÔNG | SORETLY SAY ENGLISH. |
| Xin lỗi bạn có nói được tiếng Anh không? | Are you sorry if you can speak English? |
| Xin lỗi, bạn có nói được tiếng Anh không? | Sorry, can you speak English? |

→ Đã thêm bước khôi phục chữ & dấu câu giữa ASR và dịch ([AsrText.kt](app/src/main/java/io/github/thucnguyen79/aitranslator/spike/asr/AsrText.kt)):
chữ thường + viết hoa đầu câu và tên riêng, "?" khi có từ để hỏi (không, chưa, à, gì, đâu, bao nhiêu…), "." còn lại,
dấu phẩy sau lời chào/xin lỗi ở đầu câu. Đây là quy tắc tạm cho spike; sản phẩm nên dùng mô hình khôi phục dấu câu
tiếng Việt (cần tìm/đánh giá ở bước sau).

### Đợt 2 — 2026-10-28 · cùng máy

**Dấu câu & chữ hoa tiếng Việt bằng mô hình** — [vibert-capu](https://huggingface.co/welcomyou/vibert-capu-onnx)
(BERT tiếng Việt, int8 110 MB, CC-BY-SA-4.0), chạy bằng ONNX Runtime
([ViPunctuator.kt](app/src/main/java/io/github/thucnguyen79/aitranslator/spike/asr/ViPunctuator.kt)):

- Trên 8 câu đã ghi âm: cả 8 câu đúng chữ hoa và dấu câu ("Xin lỗi, bạn có nói được tiếng Anh không?",
  "Tôi đến từ Việt Nam."); **~31 ms/câu**, nạp ~1 s, RAM ~+150 MB.
- Thử trên máy tính với câu dài hơn: "Tôi muốn đi Hà Nội, sau đó đi Đà Nẵng rồi về Sài Gòn.",
  "Cái này giá bao nhiêu? Có thể giảm giá một chút không?". Lỗi còn gặp: "Sao Bà không hiểu? Tôi nói tiếng Việt à?"
  (viết hoa và tách câu sai), "Chào bạn, Hôm nay…" (viết hoa sau dấu phẩy).
- Mô hình bỏ qua câu < 3 từ → dùng quy tắc. Tab Hội thoại có công tắc để so sánh mô hình và quy tắc.
- Không có mô hình nào sẵn trong sherpa-onnx cho tiếng Việt; các mô hình đa ngôn ngữ khác hoặc không có tiếng Việt,
  hoặc > 200 MB, hoặc không khôi phục chữ hoa.
- Kỹ thuật: ONNX Runtime đánh phiên bản symbol (`OrtGetApiBase@VERS_1.28.2`), nên không dùng chung được
  `libonnxruntime.so` của sherpa-onnx với `onnxruntime-android` → chuyển sang bản AAR sherpa-onnx liên kết tĩnh.

**TTS dự phòng: Supertonic 3** (Supertone, OpenRAIL-M, 1 mô hình ~130 MB cho 31 ngôn ngữ gồm vi/en/ko/ja, **không có zh**):

| Engine | vi | en | zh | ko | ja |
|---|---|---|---|---|---|
| Google TTS (máy) | ✓ 683 ms | ✓ 805 ms | ✓ 719 ms | ✓ 762 ms | ✗ không có giọng offline |
| Supertonic 3 | ✓ 1802 ms | ✓ 980 ms | — | ✓ 1389 ms | ✓ 1261 ms |

(thời gian tới khi bắt đầu phát, lần gọi đầu; Supertonic tổng hợp cả câu rồi mới phát)

- Lấp được chỗ trống tiếng Nhật; Google TTS (có giọng offline) + Supertonic phủ đủ 5 ngôn ngữ offline trên máy này.
- App chọn: giọng offline của Android → Supertonic → giọng cần mạng (kèm cảnh báo ở tab Hội thoại).
- Chậm hơn Google TTS ~1 s; có thể giảm `numSteps` (đang 5) nếu cần nhanh hơn — cần nghe thử chất lượng.
- Kỹ thuật: callback âm thanh của sherpa-onnx phải là một class (JNI tìm `invoke([F)Ljava/lang/Integer;`),
  vì Kotlin 2 biên dịch lambda bằng invokedynamic.
