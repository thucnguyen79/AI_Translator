# Checklist tiến độ — AI Translator

> Cập nhật: **2026-10-28** · commit gần nhất `04469d4` · cập nhật file này sau mỗi đợt làm việc.
>
> Ký hiệu: `[x]` xong · `[ ]` chưa làm · 🟡 làm một phần (ghi rõ còn thiếu gì) · 👤 cần anh/chị làm hoặc hỗ trợ.
> Mã F… tham chiếu [backlog.md](backlog.md).

## Tổng quan

| Giai đoạn | Trạng thái | Ghi chú |
|---|---|---|
| Phân tích & thiết kế | ✅ Xong | [requirements.md](requirements.md), [backlog.md](backlog.md), [architecture.md](architecture.md) |
| 0 — Spike đo mô hình | 🟡 Gần xong | Đã chốt công nghệ; còn đo máy yếu và giọng đa dạng |
| 1 — Nền tảng app | ✅ Xong | Đa module, gói ngôn ngữ, engine, CI xanh |
| 2 — MVP (2 người) | 🟡 Đang làm | Hội thoại vi↔en chạy trên máy thật; còn chữ lớn, chẩn đoán, hoàn thiện UX |
| 3 — v1 (5 ngôn ngữ, 3–4 người, phát hành) | ⬜ Chưa bắt đầu | Một số phần đã có sẵn trong engine |
| 4 — v2 | ⬜ Chưa bắt đầu | |

## Giai đoạn 0 — Spike ([spike/README.md](../spike/README.md))

- [x] App đo đạc: ASR, dịch, TTS, hội thoại, ghi âm bộ thử; script tải/đẩy mô hình, báo cáo
- [x] Bộ 100 câu du lịch song song vi/en/zh/ko/ja
- [x] Bergamot build từ mã nguồn, chạy trên máy thật; ngang hoặc hơn ML Kit ở cả 8 hướng
- [x] ASR tiếng Việt (zipformer-vi-30M): 8/8 câu đúng, ~70 ms/câu
- [x] Phát hiện & sửa: chữ IN HOA làm hỏng bản dịch → mô hình dấu câu vibert-capu (~31 ms/câu)
- [x] TTS: Google TTS có giọng offline vi/en/zh/ko; Supertonic 3 lấp chỗ trống tiếng Nhật
- [x] Độ trễ cả chuỗi sau khi nói xong: ~0,1–0,5 s
- [ ] 👤 Đo trên **máy yếu/tầm trung** (RAM 4–6 GB): RAM, tốc độ, nhiệt
- [ ] 👤 Ghi âm thêm người đọc: giọng Bắc/Trung/Nam, có tiếng ồn → đo lại ASR tiếng Việt
- [ ] 👤 Đo ASR zh/ko/ja/en (SenseVoice) với giọng thật
- [ ] 👤 Người bản ngữ rà câu tham chiếu zh/ko/ja và chấm tay bản dịch vi↔zh/ko/ja
- [ ] 👤 Nghe thử chất lượng giọng Supertonic (vi, ja); cân nhắc giảm `numSteps` cho nhanh hơn

## Giai đoạn 1 — Nền tảng app

- [x] Dự án đa module + build-logic, version catalog, minSdk 28, chỉ arm64
- [x] `core/model`, `core/engine`, `core/speech`, `core/translate`, `core/tts`, `core/packs`
- [x] Danh mục gói sinh tự động (`tools/gen_packs.py`), SHA-256 lấy từ nơi phát hành
- [x] Tải gói: tải tiếp khi rớt mạng, kiểm SHA-256, giải nén an toàn, cài lần lượt có hàng đợi
- [x] Engine hội thoại: khoá lượt, dịch một-sang-nhiều, ngắt lời, nhập phím, nghe lại
- [x] 23 unit test (engine, gói, dấu câu); GitHub Actions: test + APK debug
- [x] Kiểm chứng trên máy thật: tải gói vi + en, hội thoại, cuộn ở nửa xoay
- [ ] Tải gói chạy nền khi tắt app (WorkManager / foreground service) — hiện phải mở app để tải tiếp
- [ ] Ký bản release (keystore riêng) — hiện release dùng khoá debug

## Giai đoạn 2 — MVP

### F1. Thiết lập phiên
- [x] F1.1 2 ghế
- [x] F1.2 Chọn ngôn ngữ mỗi ghế; báo thiếu gói kèm nút mở Gói ngôn ngữ
- [x] F1.4 Nhớ ngôn ngữ của từng ghế

### F2. Thu âm
- [ ] F2.1 Quyền micro 🟡 — có xin quyền; **chưa** giải thích trước và chưa xử lý khi bị từ chối
- [x] F2.2 Bấm-để-nói theo ghế (bấm để nói, bấm lại để dừng)
- [x] F2.3 Khoá lượt nói
- [x] F2.4 Tự dừng khi im lặng (Silero VAD, 0,6 s)
- [ ] F2.5 Chống vòng lặp với loa 🟡 — không thu khi đang đọc (nhờ ngắt lời); **chưa** bật AEC/NS của hệ thống
- [x] F2.8 Mức âm & dòng trạng thái

### F3–F5. Nhận dạng, dịch, đọc
- [x] F3.1 ASR offline vi, en
- [x] F4.1 Dịch vi↔en
- [ ] F5.1 Tự đọc bản dịch 🟡 — luôn đọc; **chưa** có bật/tắt theo ghế
- [x] F5.2 Đọc lần lượt, không chồng tiếng
- [ ] F5.4 Kiểm tra giọng offline 🟡 — có cảnh báo; **chưa** có nút hướng dẫn cài giọng / gợi ý tải Supertonic

### F6. Giao diện
- [x] F6.1 Hai người chia đôi, nửa trên xoay 180°
- [ ] F6.3 Bố cục danh sách (chuyền tay)
- [x] F6.4 Bong bóng câu gốc + bản dịch
- [ ] F6.6 **Chữ lớn toàn màn hình** (giơ cho người đối diện đọc)
- [ ] F6.7 Phát lại / sao chép 🟡 — có nghe lại; **chưa** có sao chép
- [x] F6.8 Giữ màn hình sáng

### F7–F8. Bàn phím & gói ngôn ngữ
- [x] F7.1 Nhập bằng bàn phím
- [x] F8.1 Danh sách gói & dung lượng, trạng thái
- [ ] F8.2 Tải theo nhu cầu 🟡 — có tải tiếp; **chưa** có tuỳ chọn chỉ Wi-Fi và kiểm tra dung lượng trống
- [x] F8.3 Kiểm tra SHA-256
- [x] F8.4 Xoá gói

### F10. Cài đặt & chẩn đoán
- [ ] F10.1 Giao diện vi + en 🟡 — chữ đang viết thẳng tiếng Việt trong code; cần chuyển sang `strings.xml` + bản tiếng Anh
- [ ] F10.3 **Màn hình chẩn đoán** (thời gian ASR/dịch/đọc mỗi lượt — engine đã đo sẵn)
- [x] F10.4 Giấy phép & ghi nguồn

## v1 — đã có sẵn một phần

- [x] F2.7 Ngắt lời (bấm mic khi đang đọc)
- [ ] F3.3 Dấu câu & chuẩn hoá 🟡 — có dấu câu tiếng Việt (vibert-capu); chưa chuẩn hoá số/giờ/tiền
- [x] F4.3 Dịch qua tiếng Anh cho các cặp không trực tiếp
- [x] F4.4 Một câu dịch sang mọi ngôn ngữ khác trong phiên
- [ ] F3.1 / F4.2 zh, ko, ja 🟡 — đã nối trong app, gói tải được; **chưa** thử trên máy thật
- [ ] F1.1 / F6.2 3–4 ghế (engine hỗ trợ; thiếu giao diện)
- [ ] F1.3 Màu & tên ghế · F1.5 Đổi ngôn ngữ không mất lịch sử
- [ ] F2.6 Chế độ rảnh tay · F3.2 Chữ tạm thời · F3.4 Nhận diện ngôn ngữ nói · F3.5 Mô hình theo cấu hình máy
- [ ] F4.5 Dịch ngược để kiểm tra
- [ ] F5.3 Tốc độ đọc, chọn giọng
- [ ] F6.5 Sửa câu nhận dạng sai rồi dịch lại
- [ ] F7.2 Bộ cụm từ du lịch soạn sẵn (ưu tiên cao: cả 2 bộ dịch yếu với văn nói du lịch)
- [ ] F9.x Lịch sử, tìm kiếm, xuất file, chế độ riêng tư
- [ ] F10.2 Cỡ chữ, chế độ tối
- [ ] Phát hành Google Play (chính sách quyền riêng tư, ảnh chụp, ký release)

## Lỗi & tồn đọng kỹ thuật

- [x] ~~Cuộn ở nửa trên bị trôi ngược~~ (sửa `04469d4`)
- [x] ~~Tải 2 gói cùng lúc tranh chấp gói dùng chung; gói English lỗi ENOENT~~ (sửa `d72459c`)
- [ ] Người ngồi phía trên mở bàn phím → bàn phím hiện ở mép người kia và che nửa dưới
- [ ] Lần nói đầu tiên chờ nạp mô hình 1–2 s → nạp trước khi vào màn hình hội thoại
- [ ] APK debug ~97 MB (thư viện native không nén) → kiểm tra kích thước bản release, cân nhắc App Bundle
- [ ] Một vài lỗi dấu câu của vibert-capu ("Sao Bà không hiểu?…", viết hoa sau dấu phẩy)

## Việc tiếp theo (đề xuất theo thứ tự)

1. F6.6 Chữ lớn toàn màn hình + F6.7 sao chép
2. F10.3 Màn hình chẩn đoán + nạp sẵn mô hình khi mở hội thoại
3. F2.1 Xử lý từ chối quyền micro · F5.1 bật/tắt đọc theo ghế · F5.4 nút cài giọng / gợi ý Supertonic
4. F10.1 Chuyển chuỗi sang `strings.xml`, thêm giao diện tiếng Anh
5. F6.3 Bố cục danh sách · sửa vị trí bàn phím cho người phía trên
6. F8.2 Tuỳ chọn chỉ Wi-Fi, kiểm tra dung lượng trống, tải nền
7. 👤 Song song: thử zh/ko/ja trên máy, đo máy yếu, ghi âm giọng vùng miền

## Nhật ký cập nhật

| Ngày | Commit | Thay đổi |
|---|---|---|
| 2026-10-28 | `04469d4` | Tạo checklist; Giai đoạn 1 xong, MVP đang làm |
