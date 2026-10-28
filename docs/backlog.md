# Backlog tính năng — AI Translator

> Phạm vi: một máy dùng chung, 2–4 người, offline, miễn phí. Xem bối cảnh tại [requirements.md](requirements.md).
>
> Ưu tiên: **MVP** = bản dùng thử đầu tiên · **v1** = phát hành chính thức · **v2** = mở rộng

## F1. Thiết lập phiên hội thoại
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F1.1 | Chọn số người | 2, 3 hoặc 4 ghế | MVP (2) · v1 (3–4) |
| F1.2 | Chọn ngôn ngữ mỗi ghế | Chỉ cho chọn ngôn ngữ đã tải gói; báo rõ nếu thiếu gói | MVP |
| F1.3 | Màu & tên ngắn mỗi ghế | Phân biệt người nói trên màn hình | v1 |
| F1.4 | Nhớ cấu hình gần nhất | Mở app là vào ngay phiên cũ | MVP |
| F1.5 | Đổi ngôn ngữ giữa chừng | Không mất lịch sử phiên đang chạy | v1 |

## F2. Thu âm
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F2.1 | Quyền micro | Xin quyền kèm giải thích; xử lý khi bị từ chối | MVP |
| F2.2 | Bấm-để-nói theo ghế | Nhấn giữ hoặc nhấn bật/tắt; nút ghế xác định người nói & ngôn ngữ nguồn | MVP |
| F2.3 | Khoá lượt nói | Một ghế đang nói thì các nút khác tạm khoá | MVP |
| F2.4 | Tự dừng khi im lặng (VAD) | Tự kết thúc câu sau ~0,6–0,8 s im lặng | MVP |
| F2.5 | Chống vòng lặp với loa | Không thu khi máy đang đọc TTS; bật AEC/NS của hệ thống | MVP |
| F2.6 | Chế độ rảnh tay | Nghe liên tục + tự nhận diện ngôn ngữ trong tập ngôn ngữ của phiên | v1 |
| F2.7 | Ngắt lời | Bấm micro khi TTS đang đọc → dừng đọc, bắt đầu thu | v1 |
| F2.8 | Mức âm & trạng thái | Sóng âm, trạng thái nghe / nhận dạng / dịch / đọc | MVP |
| F2.9 | Tai nghe Bluetooth | Định tuyến mic/loa qua tai nghe | v2 |

## F3. Nhận dạng giọng nói (ASR, offline)
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F3.1 | ASR offline 5 ngôn ngữ | vi, en, zh, ko, ja | MVP (vi, en) · v1 (đủ 5) |
| F3.2 | Chữ tạm thời | Hiện chữ trong khi nói (streaming hoặc giải mã lại theo đoạn) | v1 |
| F3.3 | Dấu câu & chuẩn hoá | Thêm dấu câu, chuẩn hoá số/giờ/tiền | v1 |
| F3.4 | Nhận diện ngôn ngữ nói | Phục vụ chế độ rảnh tay (F2.6) | v1 |
| F3.5 | Chọn mô hình theo cấu hình máy | Máy yếu dùng mô hình nhỏ | v1 |

## F4. Dịch (offline)
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F4.1 | Dịch vi↔en | | MVP |
| F4.2 | Dịch vi↔zh, vi↔ko, vi↔ja | | v1 |
| F4.3 | Dịch qua ngôn ngữ trung gian | Cặp không có mô hình trực tiếp (vd ko↔ja) đi qua tiếng Anh | v1 |
| F4.4 | Một-sang-nhiều | Dịch sang tất cả ngôn ngữ đích khác nhau trong phiên, chạy song song/tuần tự có ưu tiên | v1 |
| F4.5 | Dịch ngược để kiểm tra | Hiện bản dịch ngược nhỏ dưới câu của người nói | v1 |
| F4.6 | Ngữ cảnh hội thoại | Dùng câu trước để dịch đại từ/xưng hô (tuỳ khả năng mô hình) | v2 |

## F5. Đọc bản dịch (TTS, offline)
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F5.1 | Tự đọc bản dịch | Bật/tắt theo ghế | MVP |
| F5.2 | Hàng đợi phát | Nhiều bản dịch → đọc lần lượt, không chồng tiếng | MVP |
| F5.3 | Tốc độ đọc, chọn giọng | | v1 |
| F5.4 | Kiểm tra giọng offline | Báo nếu máy thiếu giọng đọc offline và hướng dẫn cài | MVP |

## F6. Giao diện hội thoại
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F6.1 | Bố cục 2 người chia đôi xoay 180° | | MVP |
| F6.2 | Bố cục 3–4 vùng theo hướng ngồi | Mỗi vùng chỉ hiện bản dịch sang ngôn ngữ của ghế đó | v1 |
| F6.3 | Bố cục danh sách (chat) | Chuyền tay | MVP |
| F6.4 | Bong bóng gốc + dịch | Màu theo ghế, thời gian | MVP |
| F6.5 | Sửa câu nhận dạng sai | Chạm để sửa → dịch lại | v1 |
| F6.6 | Chữ lớn toàn màn hình | Giơ cho người đối diện đọc | MVP |
| F6.7 | Phát lại / sao chép một câu | | MVP |
| F6.8 | Giữ màn hình sáng trong phiên | | MVP |

## F7. Cụm từ du lịch & nhập bàn phím
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F7.1 | Nhập bằng bàn phím | Dự phòng khi ồn hoặc nhận dạng sai | MVP |
| F7.2 | Bộ cụm từ soạn sẵn | Chào hỏi, ăn uống, đi lại, mua sắm, khách sạn, khẩn cấp; bản dịch soạn sẵn (không cần mô hình) | v1 |
| F7.3 | Cụm từ yêu thích | Lưu câu hay dùng | v2 |

## F8. Gói ngôn ngữ
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F8.1 | Danh sách gói & dung lượng | Trạng thái: chưa tải / đang tải / sẵn sàng | MVP |
| F8.2 | Tải theo nhu cầu | Tiếp tục tải khi rớt mạng; tuỳ chọn chỉ tải qua Wi-Fi | MVP |
| F8.3 | Kiểm tra toàn vẹn | Kiểm SHA-256 sau khi tải | MVP |
| F8.4 | Xoá gói | Giải phóng dung lượng | MVP |
| F8.5 | Cập nhật gói | Có phiên bản mới thì báo | v2 |

## F9. Lịch sử
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F9.1 | Tự lưu phiên | Lưu văn bản (không lưu âm thanh) vào CSDL cục bộ | v1 |
| F9.2 | Xem, tìm kiếm | | v1 |
| F9.3 | Xoá phiên / xoá tất cả | MVP: nút xoá hội thoại đang hiển thị (có xác nhận); v1: xoá từng phiên đã lưu / xoá tất cả | MVP (phiên hiện tại) · v1 |
| F9.4 | Xuất TXT song ngữ / chia sẻ | Qua share sheet của Android | v1 |
| F9.5 | Tắt lưu lịch sử | Chế độ riêng tư | v1 |

## F10. Cài đặt & chẩn đoán
| ID | Tính năng | Mô tả / tiêu chí chấp nhận | Ưu tiên |
|---|---|---|---|
| F10.1 | Ngôn ngữ giao diện | vi, en (sau: zh, ko, ja) | MVP (vi, en) |
| F10.2 | Cỡ chữ, chế độ tối | | v1 |
| F10.3 | Màn hình chẩn đoán | Thời gian từng bước (ASR/MT/TTS) để đo độ trễ | MVP |
| F10.4 | Giấy phép & ghi nguồn | Liệt kê mô hình/thư viện mã nguồn mở | MVP |

## Định nghĩa các mốc

- **MVP:** 2 người, vi↔en, bấm-để-nói, offline hoàn toàn, chữ lớn, nhập bàn phím, quản lý gói, màn hình chẩn đoán.
  Mục tiêu: kiểm chứng chất lượng & độ trễ trên máy thật.
- **v1:** đủ 5 ngôn ngữ, 3–4 người, chế độ rảnh tay, chữ tạm thời, cụm từ du lịch, lịch sử & xuất file. Phát hành Google Play.
- **v2:** ngữ cảnh hội thoại, tai nghe Bluetooth, cập nhật gói, cụm từ yêu thích, (cân nhắc) dịch ảnh.
