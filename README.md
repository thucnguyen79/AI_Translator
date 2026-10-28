# AI Translator

Ứng dụng Android **miễn phí, chạy offline** giúp 2–4 người du lịch nói các ngôn ngữ khác nhau trò chuyện
trực tiếp qua **một điện thoại dùng chung**.

- **Ngôn ngữ:** Tiếng Việt, Anh, Trung, Hàn, Nhật (trung tâm là tiếng Việt)
- **Offline hoàn toàn:** nhận dạng giọng nói, dịch và đọc bản dịch đều chạy trên máy; mạng chỉ dùng để tải gói ngôn ngữ
- **Mã nguồn mở:** sherpa-onnx (ASR, VAD, TTS), Mozilla Bergamot (dịch), vibert-capu (dấu câu tiếng Việt), Supertonic 3 (TTS dự phòng)

## Trạng thái

- **Giai đoạn 0 — spike:** xong phần lớn, kết quả đo trên máy thật ở [spike/README.md](spike/README.md).
- **Giai đoạn 1 — nền tảng:** dự án đa module, quản lý gói ngôn ngữ, engine hội thoại, màn hình hội thoại 2 người.

## Build

Yêu cầu: JDK 17+ (đã thử JDK 21), Android SDK (compileSdk 37, NDK 28.2.13676358, CMake 3.22.1), Python 3.10+, Git.

```bash
python tools/deps.py          # sherpa-onnx AAR (kiểm SHA-256) + mã nguồn Bergamot (submodule)
./gradlew testDebugUnitTest   # unit test
./gradlew :app:installDebug   # cài lên điện thoại arm64, Android 9+
```

Lần build đầu biên dịch Marian/Bergamot bằng NDK nên mất vài phút. Mở app → **Gói ngôn ngữ** → tải ít nhất 2 ngôn ngữ.

## Cấu trúc

| Module | Nội dung |
|---|---|
| `app` | Giao diện Compose (hội thoại 2 người, gói ngôn ngữ, thông tin), nối các thành phần (`AppContainer`) |
| `core/model` | `Lang`, các interface `AudioInput`, `SpeechRecognizer`, `Translator`, `SpeechSynthesizer` |
| `core/engine` | `ConversationEngine`: lượt nói, khoá micro, dịch một-sang-nhiều, ngắt lời, nhập phím |
| `core/speech` | Thu âm + Silero VAD, sherpa-onnx ASR, khôi phục dấu câu tiếng Việt |
| `core/translate` | Bergamot qua JNI (biên dịch từ `third_party/bergamot-translator`), dịch qua tiếng Anh |
| `core/tts` | Giọng đọc của máy, Supertonic 3, chọn engine theo ngôn ngữ |
| `core/packs` | Danh mục gói (`packs.json`), tải tiếp khi rớt mạng, kiểm SHA-256, giải nén |
| `spike/` | App đo đạc của Giai đoạn 0 (dự án Gradle riêng) |
| `tools/` | `deps.py` (phụ thuộc build), `gen_packs.py` (sinh danh mục gói từ API của nơi phát hành) |

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Tầm nhìn, quyết định phạm vi, kịch bản, yêu cầu phi chức năng, rủi ro |
| [docs/backlog.md](docs/backlog.md) | Danh sách tính năng chi tiết theo mức ưu tiên MVP / v1 / v2 |
| [docs/architecture.md](docs/architecture.md) | Kiến trúc, lựa chọn mô hình, gói ngôn ngữ, lộ trình |
| [spike/README.md](spike/README.md) | Kết quả đo mô hình trên điện thoại thật |
