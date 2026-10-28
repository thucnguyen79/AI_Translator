# Checklist tiến độ — AI Translator

> Cập nhật: **2026-10-28** · cập nhật file này sau mỗi đợt làm việc (xem nhật ký cuối file).
>
> Ký hiệu: `[x]` xong · `[ ]` chưa làm · 🟡 làm một phần (ghi rõ còn thiếu gì) · 👤 cần anh/chị làm hoặc hỗ trợ.
> Mã F… tham chiếu [backlog.md](backlog.md).

## Tổng quan

| Giai đoạn | Trạng thái | Ghi chú |
|---|---|---|
| Phân tích & thiết kế | ✅ Xong | [requirements.md](requirements.md), [backlog.md](backlog.md), [architecture.md](architecture.md) |
| 0 — Spike đo mô hình | 🟡 Gần xong | Đã chốt công nghệ; còn đo máy yếu và giọng đa dạng |
| 1 — Nền tảng app | ✅ Xong | Đa module, gói ngôn ngữ, engine, CI xanh |
| 2 — MVP (2 người) | 🟡 Đang làm | Hội thoại, chữ lớn, chẩn đoán, xoá hội thoại chạy trên máy thật; còn hoàn thiện UX |
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
- [x] F6.6 Chữ lớn toàn màn hình: chạm bong bóng → bản dịch tự quay về người cần đọc, có nút xoay
- [x] F6.7 Nghe lại + sao chép (trong màn hình chữ lớn)
- [x] F6.8 Giữ màn hình sáng
- [x] Ô nhập và nút không còn bị bàn phím che; nửa đang gõ được nhường chỗ

### F7–F8. Bàn phím & gói ngôn ngữ
- [x] F7.1 Nhập bằng bàn phím
- [x] F8.1 Danh sách gói & dung lượng, trạng thái
- [ ] F8.2 Tải theo nhu cầu 🟡 — có tải tiếp; **chưa** có tuỳ chọn chỉ Wi-Fi và kiểm tra dung lượng trống
- [x] F8.3 Kiểm tra SHA-256
- [x] F8.4 Xoá gói

### F9. Lịch sử
- [ ] F9.3 Xoá hội thoại 🟡 — có nút xoá hội thoại đang hiển thị (kèm xác nhận, chỉ ở nửa của người cầm máy); xoá phiên đã lưu / xoá tất cả chờ F9.1 (v1)

### F10. Cài đặt & chẩn đoán
- [ ] F10.1 Giao diện vi + en 🟡 — hai nửa hội thoại đã theo ngôn ngữ từng ghế (5 thứ tiếng); các tab Gói ngôn ngữ / Thông tin vẫn chỉ tiếng Việt, cần `strings.xml` + bản tiếng Anh
- [x] F10.3 Chẩn đoán (tab Thông tin): thời gian nhận dạng/dịch dưới mỗi câu, thời gian nạp mô hình, trung bình phiên, thông tin máy
- [x] F10.4 Giấy phép & ghi nguồn
- [x] Nạp sẵn mô hình khi mở app / đổi ngôn ngữ (trên máy thử: VAD 0,05 s · nhận dạng vi 2,0 s · zh 1,8 s · mỗi chiều dịch 0,3 s) → lần nói đầu không phải chờ

## v1 — đã có sẵn một phần

- [x] F2.7 Ngắt lời (bấm mic khi đang đọc)
- [ ] F3.3 Dấu câu & chuẩn hoá 🟡 — có dấu câu tiếng Việt (vibert-capu); chưa chuẩn hoá số/giờ/tiền
- [x] F4.3 Dịch qua tiếng Anh cho các cặp không trực tiếp
- [x] F4.4 Một câu dịch sang mọi ngôn ngữ khác trong phiên
- [ ] F3.1 / F4.2 zh, ko, ja 🟡 — đã nối trong app, gói tải được; **chưa** thử trên máy thật
- [ ] F1.1 / F6.2 3–4 ghế (engine hỗ trợ; thiếu giao diện)
- [x] F1.5 Đổi ngôn ngữ giữa chừng: dịch bù câu cũ, không mất lịch sử
- [ ] F1.3 Màu & tên ghế
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
- [ ] Người ngồi phía trên mở bàn phím → bàn phím vẫn hiện ở mép người kia (giới hạn của dùng chung 1 máy); ô nhập không còn bị che
- [x] ~~Lần nói đầu tiên chờ nạp mô hình~~ → nạp sẵn ở nền
- [ ] APK debug ~97 MB (thư viện native không nén) → kiểm tra kích thước bản release, cân nhắc App Bundle
- [ ] Một vài lỗi dấu câu của vibert-capu ("Sao Bà không hiểu?…", viết hoa sau dấu phẩy)
- [x] **Đổi ngôn ngữ giữa hội thoại** (thử trên máy 2026-10-28, liên quan F1.5) — đã sửa:
  - [x] ~~Câu cũ không được dịch sang ngôn ngữ mới~~ → tự dịch bù ngầm, giữ mọi bản dịch đã có
  - [x] ~~Bong bóng hiện cùng một câu hai lần~~
  - [x] ~~Chọn ngôn ngữ chưa tải gói thì ẩn toàn bộ hội thoại~~ → vẫn hiện, chỉ khoá micro/nhập, báo đúng gói còn thiếu
  - [x] Câu mới sau khi đổi được dịch đúng sang ngôn ngữ mới; đổi lại như cũ không mất câu nào
- [ ] Nhận dạng tiếng Trung (SenseVoice) cho ra câu vô nghĩa "你唱城去。" ở một lượt nói thật → cần đo kỹ (👤 cần giọng tiếng Trung)

## Yêu cầu của anh/chị (2026-10-28)

- [x] Sửa 3 lỗi đổi ngôn ngữ giữa hội thoại (mục trên, F1.5)
- [x] Đổi icon app: icon thích ứng vector (bong bóng "A" và "文", nền gradient xanh–tím) + icon đơn sắc theo chủ đề
- [x] Chữ của mỗi nửa theo ngôn ngữ của nửa đó (vi/en/zh/ko/ja): Nói, Nhập/Ẩn bàn phím, Dừng, Xoá, Nghe lại, trạng thái, cảnh báo, hộp xác nhận xoá
- [x] Chữ lớn toàn màn hình: hiện ngôn ngữ của người ở hướng đang quay tới (cả các nút); bấm xoay đổi sang người phía bên kia; sửa chữ tiếng Việt cỡ lớn bị chồng dòng

## Việc tiếp theo (đề xuất theo thứ tự)

1. F2.1 Xử lý từ chối quyền micro · F5.1 bật/tắt đọc theo ghế · F5.4 nút cài giọng / gợi ý Supertonic
2. F10.1 Chuyển chuỗi sang `strings.xml`, thêm giao diện tiếng Anh
3. F6.3 Bố cục danh sách (chuyền tay)
4. F8.2 Tuỳ chọn chỉ Wi-Fi, kiểm tra dung lượng trống, tải nền
5. 👤 Song song: thử zh/ko/ja trên máy, đo máy yếu, ghi âm giọng vùng miền

## Nhật ký cập nhật

| Ngày | Commit | Thay đổi |
|---|---|---|
| 2026-10-28 | `04469d4` | Tạo checklist; Giai đoạn 1 xong, MVP đang làm |
| 2026-10-28 | `e297de9` | F6.6 chữ lớn, F6.7 sao chép, F10.3 chẩn đoán, nạp sẵn mô hình, F9.3 xoá hội thoại, sửa bàn phím che ô nhập |
| 2026-10-28 | — | Thử đổi ngôn ngữ giữa hội thoại trên máy: ghi 3 lỗi (F1.5) |
| 2026-10-28 | (commit này) | Sửa đổi ngôn ngữ giữa hội thoại (F1.5), icon mới, chữ theo ngôn ngữ từng nửa, chữ lớn theo người đọc |
