# Nội dung trang Google Play

Dán vào Play Console → *Grow users → Store presence → Main store listing*. Ngôn ngữ mặc định: Tiếng Việt; thêm bản dịch English.
Nếu đổi tên app, sửa tên ở đây, trong `app_name` (strings.xml) và chạy lại `store/make_feature.py --name "…"`.

| Tài sản | File |
|---|---|
| Icon 512 × 512 | [icon-512.png](icon-512.png) (nguồn: [icon.svg](icon.svg), cùng hình với icon trong app) |
| Ảnh bìa 1024 × 500 | [feature-1024x500.png](feature-1024x500.png) (tạo bằng [make_feature.py](make_feature.py)) |
| Ảnh chụp màn hình | [screenshots/](screenshots/) — 5 ảnh, rộng 1080 px, tỉ lệ ≤ 2:1 (Play không nhận ảnh dài hơn): đối mặt, rảnh tay, quanh bàn, chuyền tay, câu mẫu. Cắt từ ảnh chụp khi thử trên máy (2026-10-28), bỏ thanh trạng thái và thanh tab cũ; ảnh câu mẫu chụp ở nửa trên nên được xoay lại. Khi cắm máy có thể chụp lại bộ mới (giao diện tiếng Việt, hội thoại Việt–Anh) |

## Tiếng Việt

**Tên** (≤ 30 ký tự): AI Translator

**Mô tả ngắn** (≤ 80 ký tự):
Dịch hội thoại offline Việt–Anh–Trung–Hàn–Nhật. Miễn phí, không quảng cáo.

**Mô tả đầy đủ**:

AI Translator giúp hai đến bốn người nói khác ngôn ngữ trò chuyện với nhau trên cùng một chiếc điện thoại — ngay cả khi không có mạng. Thích hợp khi đi du lịch: gọi món, hỏi đường, đi taxi, nhận phòng khách sạn, mua sắm.

TOÀN BỘ CHẠY TRÊN MÁY
• Nhận dạng giọng nói, dịch và đọc bản dịch đều chạy ngay trên điện thoại, không gửi âm thanh hay văn bản lên mạng.
• Tải gói ngôn ngữ một lần (khoảng 100–200 MB mỗi thứ tiếng), sau đó dùng hoàn toàn offline.

5 THỨ TIẾNG
Tiếng Việt, English, 中文, 한국어, 日本語 — dịch qua lại giữa mọi cặp.

DỄ DÙNG KHI ĐI DU LỊCH
• Đặt máy giữa hai người: nửa trên tự quay về người đối diện, mỗi người một nút Nói.
• Bố cục "Chuyền tay" cho 2–4 người và "Quanh bàn" cho 3–4 người ngồi bốn phía.
• Chế độ rảnh tay: nói xong máy tự nghe tiếp; có thể tự nhận ra ai đang nói theo ngôn ngữ.
• 100 câu mẫu du lịch có sẵn bản dịch chuẩn (chào hỏi, ăn uống, đi lại, khách sạn, mua sắm, hỏi đường, khẩn cấp).
• Nhập bằng bàn phím khi ồn ào; sửa câu nhận dạng sai; chữ lớn toàn màn hình để đưa người kia đọc.
• Dịch ngược để kiểm tra người kia hiểu đúng ý bạn.

RIÊNG TƯ
• Không cần tài khoản, không quảng cáo, không theo dõi.
• Lịch sử hội thoại (chỉ chữ) lưu trên máy; có chế độ riêng tư để không lưu.

MÃ NGUỒN MỞ
Dùng các mô hình và thư viện mã nguồn mở: sherpa-onnx, SenseVoice, Zipformer, Mozilla Bergamot, Whisper, Supertonic, ONNX Runtime. Danh sách giấy phép trong mục Thông tin của app. Mã nguồn: github.com/thucnguyen79/AI_Translator

## English

**Short description** (≤ 80 characters):
Offline conversation translator: Vietnamese, English, Chinese, Korean, Japanese.

**Full description**:

AI Translator lets two to four people who speak different languages talk on one phone — even with no connection. Made for travel: ordering food, asking the way, taking a taxi, checking in, shopping.

EVERYTHING RUNS ON THE PHONE
• Speech recognition, translation and reading aloud all happen on the device; audio and text never leave it.
• Download a language pack once (about 100–200 MB per language), then use it fully offline.

5 LANGUAGES
Vietnamese, English, Chinese, Korean and Japanese, in every direction.

BUILT FOR TRAVEL
• Put the phone between two people: the top half turns to the person opposite, each with their own Speak button.
• "Pass the phone" layout for 2–4 people and "Around the table" for 3–4 people on all sides.
• Hands-free mode: listens again after each sentence and can tell who is speaking from the language.
• 100 ready-made travel phrases with human translations (greetings, food, transport, hotel, shopping, directions, emergencies).
• Type when it is noisy, fix a misheard sentence, show a sentence full screen to the other person.
• Back-translation to check the other person read what you meant.

PRIVATE
• No account, no ads, no tracking.
• Conversation history (text only) stays on the phone; private mode turns saving off.

OPEN SOURCE
Built on open-source models and libraries: sherpa-onnx, SenseVoice, Zipformer, Mozilla Bergamot, Whisper, Supertonic, ONNX Runtime. Licences are listed in the app's Info tab. Source code: github.com/thucnguyen79/AI_Translator
