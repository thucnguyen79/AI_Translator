# Đặc tả yêu cầu — AI Translator (Android)

> Phiên bản: 0.2 · Cập nhật: 2026-10-28 · Trạng thái: Bản nháp, đã chốt phạm vi

## 1. Tầm nhìn sản phẩm

Ứng dụng Android **miễn phí, chạy offline**, giúp **2–4 người du lịch** nói các ngôn ngữ khác nhau
trò chuyện trực tiếp qua **một chiếc điện thoại dùng chung**: nói vào máy → máy hiện chữ và đọc bản dịch
cho những người còn lại.

## 2. Các quyết định phạm vi đã chốt

| # | Chủ đề | Quyết định | Hệ quả thiết kế |
|---|---|---|---|
| D1 | Bối cảnh | **Du lịch** (nhà hàng, khách sạn, taxi, chợ, hỏi đường, mua sắm) | Câu ngắn, văn nói, môi trường ồn; ưu tiên tốc độ và dễ dùng hơn độ chính xác thuật ngữ |
| D2 | Ngôn ngữ | **Việt (vi), Anh (en), Trung phổ thông (zh), Hàn (ko), Nhật (ja)** | Tiếng Việt là ngôn ngữ trung tâm; 4 cặp chính: vi↔en, vi↔zh, vi↔ko, vi↔ja |
| D3 | Thiết bị | **Một máy dùng chung, tối đa 4 người** | Không cần backend/phòng nhiều máy; giao diện phải phục vụ nhiều người nhìn từ nhiều hướng |
| D4 | Offline | **Bắt buộc** | Toàn bộ ASR, dịch, TTS chạy trên máy; tải gói ngôn ngữ theo nhu cầu |
| D5 | Kinh doanh | **Miễn phí** | Chi phí vận hành ≈ 0: không gọi API trả phí, không server riêng |
| D6 | Lưu trữ | **Có lưu lịch sử**, chưa có yêu cầu pháp lý | Lưu cục bộ trên máy, người dùng tự xoá/xuất |
| D7 | Công nghệ | **Ưu tiên miễn phí, mã nguồn mở** | Xem [architecture.md](architecture.md) |

### Ngoài phạm vi (hiện tại)
- Chế độ nhiều máy / phòng hội thoại qua mạng, người nghe qua web.
- Tài khoản, đăng nhập, thanh toán, quảng cáo.
- Dịch cuộc gọi điện thoại, dịch video/ứng dụng khác.
- Nhân bản giọng nói (voice cloning).
- Dịch văn bản trong ảnh (camera/OCR) — có thể xem xét sau.

## 3. Người dùng & kịch bản tiêu biểu

| Mã | Kịch bản | Người tham gia |
|---|---|---|
| S1 | Gọi món ở nhà hàng tại Seoul | Khách Việt ↔ nhân viên Hàn (2 người) |
| S2 | Đi taxi ở Bắc Kinh, mạng roaming chập chờn | Khách Việt ↔ tài xế Trung (2 người, **offline**) |
| S3 | Homestay Việt Nam đón khách | Chủ nhà Việt ↔ 2 khách Nhật + 1 khách Anh (4 người, 3 ngôn ngữ) |
| S4 | Hỏi đường, mua sắm nhanh | Khách Việt giơ màn hình chữ lớn cho người bản xứ đọc |
| S5 | Xem lại cuộc trò chuyện tối hôm trước | 1 người, xem lịch sử / xuất file |

## 4. Mô hình tương tác trên một máy

- **Ghế (seat):** mỗi người tham gia là một "ghế" gồm: tên ngắn (tuỳ chọn), ngôn ngữ, màu sắc, nút micro riêng.
- **Bố cục 2 người:** màn hình chia đôi, nửa trên xoay 180° cho người đối diện.
- **Bố cục 3–4 người:** điện thoại đặt nằm giữa bàn, màn hình chia 3–4 vùng, mỗi vùng xoay về phía người ngồi tương ứng
  (0°/90°/180°/270°). Mỗi vùng chỉ hiện bản dịch **sang ngôn ngữ của ghế đó**.
- **Bố cục danh sách (dự phòng):** dạng chat thông thường, cầm máy chuyền tay.
- **Một micro, một người nói tại một thời điểm:** khi một ghế đang nói, các nút micro khác bị khoá tạm.
- **Một câu → nhiều bản dịch:** một câu nói được dịch sang **tất cả ngôn ngữ khác nhau** của các ghế còn lại
  (tối đa 3 ngôn ngữ đích); câu không cần dịch cho ghế cùng ngôn ngữ.

## 5. Yêu cầu chức năng

Mức ưu tiên: **MVP** = bản dùng thử đầu tiên · **v1** = phát hành chính thức · **v2** = mở rộng.
Danh sách chi tiết theo từng hạng mục xem [backlog.md](backlog.md).

| Hạng mục | Tóm tắt |
|---|---|
| F1. Thiết lập phiên | Chọn số người (2–4), ngôn ngữ, màu, bố cục; nhớ cấu hình gần nhất |
| F2. Thu âm | Bấm-để-nói theo ghế (MVP), nghe tự động bằng VAD (v1), chống vòng lặp với loa |
| F3. Nhận dạng giọng nói | Offline, hiển thị chữ tạm thời, tự thêm dấu câu |
| F4. Dịch | Offline, một-sang-nhiều, xử lý cặp không trực tiếp qua ngôn ngữ trung gian |
| F5. Đọc bản dịch | Offline, hàng đợi phát, bật/tắt theo ghế, tốc độ đọc |
| F6. Giao diện hội thoại | Bố cục theo số người, bong bóng gốc + dịch, sửa câu sai, chữ lớn, phát lại |
| F7. Cụm từ du lịch | Bộ câu soạn sẵn theo chủ đề (ăn uống, đi lại, mua sắm, khẩn cấp), nhập bằng bàn phím |
| F8. Gói ngôn ngữ | Tải/xoá gói theo ngôn ngữ, hiển thị dung lượng, kiểm tra toàn vẹn, tiếp tục tải khi rớt mạng |
| F9. Lịch sử | Lưu cục bộ, tìm kiếm, xoá, xuất TXT song ngữ / chia sẻ |
| F10. Cài đặt | Ngôn ngữ giao diện, cỡ chữ, giọng đọc, chế độ tối, chẩn đoán hiệu năng |

## 6. Yêu cầu phi chức năng

| Mã | Yêu cầu | Chỉ tiêu ban đầu (sẽ hiệu chỉnh sau giai đoạn thử nghiệm mô hình) |
|---|---|---|
| N1 | Độ trễ (offline, máy tầm trung) | Nói xong → hiện bản dịch: **≤ 2,5 s (p50)**, ≤ 4 s (p95); chữ tạm thời ≤ 1 s |
| N2 | Thiết bị tối thiểu | Android 8.0 (API 26), **arm64-v8a**, RAM ≥ 4 GB (khuyến nghị 6 GB) |
| N3 | Dung lượng | APK cơ bản ≤ 60 MB; mỗi gói ngôn ngữ mục tiêu ≤ 300 MB |
| N4 | Pin & nhiệt | ≤ 10% pin / 30 phút hội thoại liên tục; không gây nóng máy kéo dài |
| N5 | Hoạt động offline | 100% tính năng hội thoại chạy khi bật chế độ máy bay (sau khi đã tải gói) |
| N6 | Quyền riêng tư | Không gửi âm thanh/văn bản ra ngoài máy; mặc định **không lưu âm thanh**; lịch sử chỉ nằm trên máy |
| N7 | Chất lượng | Bộ kiểm thử câu du lịch cho từng cặp ngôn ngữ; theo dõi WER (ASR) và điểm dịch (chrF/COMET) |
| N8 | Trợ năng | Hỗ trợ TalkBack, cỡ chữ hệ thống, độ tương phản đạt WCAG AA |
| N9 | Giấy phép | Chỉ dùng mô hình/thư viện có giấy phép cho phép phân phối miễn phí; ghi nguồn đầy đủ trong app |

## 7. Rủi ro chính

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Chất lượng ASR tiếng Việt offline (giọng vùng miền, tiếng ồn) | Cao | Thử nghiệm nhiều mô hình sớm; cho phép sửa câu; dự phòng nhập bàn phím / cụm từ soạn sẵn |
| Dịch trực tiếp vi↔zh/ko/ja offline ít mô hình mở chất lượng tốt | Cao | Pivot qua tiếng Anh; đo chất lượng; cân nhắc mô hình đa ngôn ngữ |
| Dung lượng gói ngôn ngữ lớn | Trung bình | Lượng tử hoá int8; tải theo nhu cầu; chia sẻ mô hình dùng chung |
| Máy yếu chạy chậm/nóng | Trung bình | Phân hạng thiết bị; chọn mô hình nhỏ hơn cho máy yếu |
| Giấy phép mô hình hạn chế (non-commercial) | Thấp–TB | App miễn phí nhưng vẫn kiểm tra từng giấy phép; ghi nhận nguồn |
