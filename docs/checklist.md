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
| 2 — MVP (2 người) | ✅ Xong | Đủ các mục MVP, đã thử trên máy; còn vài trường hợp chưa tái hiện được (⚠️) |
| 3 — v1 (5 ngôn ngữ, 3–4 người, phát hành) | 🟡 Đang làm | Gần đủ tính năng v1 (đã thử trên máy); còn F3.2, F3.5, phát hành; nhận diện ngôn ngữ nói (Whisper base) cần thử với người thật |
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
- [x] 40 unit test (engine, gói, dấu câu, lịch sử, đặc trưng Whisper; thêm 1 test chạy mô hình thật khi có `LID_TEST_DIR`); GitHub Actions: test + APK debug
- [x] Kiểm chứng trên máy thật: tải gói vi + en, hội thoại, cuộn ở nửa xoay
- [x] Tải gói chạy nền khi rời app (foreground service, xem F8.2)
- [ ] Ký bản release 🟡 — cấu hình xong (`keystore.properties` hoặc biến môi trường, xem [release.md](release.md)); 👤 cần tạo khoá và cất giữ

## Giai đoạn 2 — MVP

### F1. Thiết lập phiên
- [x] F1.1 2 ghế
- [x] F1.2 Chọn ngôn ngữ mỗi ghế; báo thiếu gói kèm nút mở Gói ngôn ngữ
- [x] F1.4 Nhớ ngôn ngữ của từng ghế

### F2. Thu âm
- [x] F2.1 Quyền micro: hộp thoại giải thích trước khi hỏi; bị từ chối hẳn → hộp thoại mở Cài đặt (vẫn nhập phím được) — ⚠️ chưa thử trên máy (máy thử đã cấp quyền từ trước)
- [x] F2.2 Bấm-để-nói theo ghế (bấm để nói, bấm lại để dừng)
- [x] F2.3 Khoá lượt nói
- [x] F2.4 Tự dừng khi im lặng (Silero VAD, 0,6 s)
- [x] F2.5 Chống vòng lặp với loa: không bao giờ thu khi đang đọc (engine tuần tự, bấm micro dừng đọc trước). Không bật AEC/NS hệ thống vì có thể làm giảm chất lượng nhận dạng — đánh giá lại khi đo môi trường ồn
- [x] F2.8 Mức âm & dòng trạng thái

### F3–F5. Nhận dạng, dịch, đọc
- [x] F3.1 ASR offline vi, en
- [x] F4.1 Dịch vi↔en
- [x] F5.1 Tự đọc bản dịch, nút 🔊/🔇 riêng từng ghế (lưu lại) — thử trên máy ✓
- [x] F5.2 Đọc lần lượt, không chồng tiếng
- [x] F5.4 Thiếu giọng offline: cảnh báo + nút "Cài giọng" (trình cài dữ liệu giọng của hệ thống) + "Tải giọng dự phòng" (Supertonic); tự kiểm tra lại khi quay về app — ⚠️ chưa thử được: máy thử đã có giọng offline cho cả 5 ngôn ngữ (xem trong Chẩn đoán)

### F6. Giao diện
- [x] F6.1 Hai người chia đôi, nửa trên xoay 180°
- [x] F6.3 Bố cục "Chuyền tay": một danh sách chat thẳng, câu gốc + bản dịch, nút nói/nhập riêng từng người; chọn trong tab Thông tin — thử trên máy ✓
- [x] F6.4 Bong bóng câu gốc + bản dịch
- [x] F6.6 Chữ lớn toàn màn hình: chạm bong bóng → bản dịch tự quay về người cần đọc, có nút xoay
- [x] F6.7 Nghe lại + sao chép (trong màn hình chữ lớn)
- [x] F6.8 Giữ màn hình sáng
- [x] Ô nhập và nút không còn bị bàn phím che; nửa đang gõ được nhường chỗ

### F7–F8. Bàn phím & gói ngôn ngữ
- [x] F7.1 Nhập bằng bàn phím
- [x] F8.1 Danh sách gói & dung lượng, trạng thái
- [x] F8.2 Tải theo nhu cầu: tải tiếp khi rớt mạng; "Chỉ tải qua Wi-Fi" (hỏi trước khi dùng dữ liệu di động); kiểm tra dung lượng trống; tải nền bằng foreground service có thông báo tiến độ — thử trên máy ✓ (tải gói 한국어 khi app ở nền; hỏi dùng dữ liệu di động và báo thiếu dung lượng chưa thử được vì máy đang dùng Wi-Fi, còn trống nhiều)
- [x] F8.3 Kiểm tra SHA-256
- [x] F8.4 Xoá gói

### F9. Lịch sử
- [x] F9.3 Xoá hội thoại: nút 🗑 làm trống màn hình (bản lưu vẫn ở Lịch sử); xoá từng phiên / xoá tất cả trong tab Lịch sử — thử trên máy ✓ (xoá từng phiên)

### F10. Cài đặt & chẩn đoán
- [x] F10.1 Giao diện vi + en: **mặc định tiếng Việt** dù máy để ngôn ngữ nào; đổi sang English ở mục "Ngôn ngữ giao diện" (tab Thông tin), áp dụng ngay, cả thông báo tải gói; hội thoại vẫn theo ngôn ngữ từng ghế (5 thứ tiếng) — thử trên máy ✓ (máy để tiếng Anh, app hiện tiếng Việt; chuyển qua lại)
- [x] F10.3 Chẩn đoán (tab Thông tin): thời gian nhận dạng/dịch dưới mỗi câu, thời gian nạp mô hình, trung bình phiên, thông tin máy
- [x] F10.4 Giấy phép & ghi nguồn
- [x] Nạp sẵn mô hình khi mở app / đổi ngôn ngữ (trên máy thử: VAD 0,05 s · nhận dạng vi 2,0 s · zh 1,8 s · mỗi chiều dịch 0,3 s) → lần nói đầu không phải chờ

## v1 — đã có sẵn một phần

- [x] F2.7 Ngắt lời (bấm mic khi đang đọc)
- [ ] F3.3 Dấu câu & chuẩn hoá 🟡 — có dấu câu tiếng Việt (vibert-capu); chưa chuẩn hoá số/giờ/tiền
- [x] F4.3 Dịch qua tiếng Anh cho các cặp không trực tiếp
- [x] F4.4 Một câu dịch sang mọi ngôn ngữ khác trong phiên
- [ ] F3.1 / F4.2 zh, ko, ja 🟡 — đã nối trong app, gói tải được; **chưa** thử trên máy thật
- [x] F1.1 3–4 người ở bố cục "Chuyền tay" (chọn số người trong tab Thông tin; mặt đối mặt luôn 2 người) — thử trên máy ✓ (3 người)
- [x] F6.2 Bố cục "Quanh bàn": 3–4 vùng, mỗi vùng quay về người ngồi phía đó (trên 180°, trái 90°, phải 270°); hộp câu mẫu, sửa câu, chữ lớn cũng quay theo; vùng hai bên không vuốt quán tính (tránh trôi sai hướng), thanh công cụ gọn (⌨/🔊 nằm trong khung chọn ngôn ngữ) — thử trên máy ✓ (4 người, chữ lớn xoay 90°/270°, kéo cuộn vùng bên đúng hướng)
- [x] F1.5 Đổi ngôn ngữ giữa chừng: dịch bù câu cũ, không mất lịch sử
- [x] F1.3 Màu & tên ghế: mỗi người một màu + số thứ tự (thử trên máy ✓); đặt tên trong tab Thông tin, hiện ở vùng của người đó, nút nói, bong bóng, lịch sử và file chia sẻ — thử trên máy ✓
- [x] F2.6 Chế độ rảnh tay (menu ⋮ ở vùng người cầm máy, hoặc nút 🎧 ở Chuyền tay): nói xong máy tự nghe tiếp; bấm mic của ai thì người đó nói ngay; lỗi micro thì tự tắt — thử trên máy ✓ (không có gói nhận diện: tự nghe người 1, đã thu và dịch được một câu nói thật)
- [x] F3.4 Nhận diện ngôn ngữ nói: 3 gói tuỳ chọn **Tiny 103 MB · Base 160 MB (nên dùng) · Small 375 MB**, tải/xoá riêng, chọn "Dùng bản này" trong Gói ngôn ngữ (bản đã chọn bị xoá thì tự dùng bản khác đã cài) — thử trên máy ✓ (tải Tiny, chuyển qua lại, xoá); khi rảnh tay, ai nói cũng được, máy chọn người theo ngôn ngữ; không có gói thì nghe lần lượt. Thử trên máy ✓ (phát file mẫu qua loa: zh → vi → vi → zh → vi đúng 5/5). Cách làm và số đo:
  - Chỉ so các ngôn ngữ đang có ở bàn (tự chạy mô hình bằng ONNX Runtime) thay vì để Whisper chọn trong 99 ngôn ngữ — tiny tự chọn: tiếng Việt sai 7/12 (ra zh, th, ms, ar)
  - Cặp ngôn ngữ đúng trên file mẫu (sạch / mô phỏng thu từ xa), có bù +2 điểm cho tiếng Việt (Whisper nghiêng về tiếng Trung): tiny 56/56 · 40/56 · base 56/56 · 50/56 · small 56/56 · 53/56 (không bù: 30, 37, 43 khi thu từ xa). Small chậm gấp ~3 lần base
  - Luật theo lượt chỉ làm trọng tài khi gần hoà (0,5 điểm): ngưỡng 2 làm "một người nói liền 2 câu" chỉ đúng 25/56 khi thu từ xa
  - Chậm hơn: nhận dạng mỗi câu ~1,3 s sau khi nói xong (có nhận diện ngôn ngữ), nạp mô hình 0,8 s
  - ⚠️ Mới đo trên 7 file mẫu + phát qua loa; 👤 cần thử với người thật nói gần máy
- [ ] F3.2 Chữ tạm thời · F3.5 Mô hình theo cấu hình máy
- [x] F4.5 Dịch ngược để kiểm tra: bật trong tab Thông tin, hiện "↩ Dịch ngược: …" dưới câu của người nói, chạy sau khi đọc xong — thử trên máy ✓ (đã bắt được bản dịch sai của câu gõ không dấu)
- [x] F5.3 Tốc độ đọc Chậm/Bình thường/Nhanh; chọn giọng từng ngôn ngữ (Tự động / Giọng 1, 2… của máy / Supertonic) có nút ▶ nghe thử — thử trên máy ✓ (tiếng Việt có 6 giọng offline; chọn, nghe thử, trả về Tự động)
- [x] F6.5 Sửa câu nhận dạng sai rồi dịch lại: nút "✎ Sửa" dưới câu của mình (Chuyền tay: nhấn giữ hoặc ✎); hộp sửa quay về người nói — thử trên máy ✓
- [x] F7.2 Bộ cụm từ du lịch: 100 câu × 5 thứ tiếng, 8 chủ đề, nút "📖 Câu mẫu" mỗi nửa (quay về đúng người), dùng bản dịch soạn sẵn, không cần gói ngôn ngữ — thử trên máy ✓ (cả 2 nửa, cuộn ở nửa xoay đúng chiều); 👤 người bản ngữ nên rà câu zh/ko/ja
- [x] F9.1 Tự lưu phiên (chỉ chữ, JSON trong bộ nhớ riêng của app) · F9.2 xem & tìm (cả trong bản dịch) · F9.4 chia sẻ dạng chữ song ngữ · F9.5 chế độ riêng tư — tab Lịch sử — thử trên máy ✓ (tự lưu, tìm trong bản dịch, mở phiên, bảng chia sẻ, xoá); chế độ riêng tư chưa thử
- [x] F10.2 Cỡ chữ (4 mức, nhân thêm trên cỡ chữ của máy) & giao diện Theo máy/Sáng/Tối (biểu tượng thanh trạng thái đổi màu theo) — thử trên máy ✓
- [ ] Phát hành Google Play 🟡 — [hướng dẫn từng bước](release.md); đã kiểm tra: AAB 35 MB, targetSdk 36, thư viện native căn trang 16 KB ✓, chính sách quyền riêng tư có link công khai. Đã có trong `store/`: icon 512, ảnh bìa 1024×500, mô tả Store vi + en; 5 ảnh chụp màn hình (cắt từ ảnh thử trên máy). 👤 Còn: tài khoản Play Console (25 USD), khoá ký, video khai báo dịch vụ nền, kiểm thử kín ≥ 12 người × 14 ngày (tài khoản cá nhân)

## Lỗi & tồn đọng kỹ thuật

- [x] ~~Cuộn ở nửa trên bị trôi ngược~~ (sửa `04469d4`)
- [x] ~~Tải 2 gói cùng lúc tranh chấp gói dùng chung; gói English lỗi ENOENT~~ (sửa `d72459c`)
- [ ] Người ngồi phía trên mở bàn phím → bàn phím vẫn hiện ở mép người kia (giới hạn của dùng chung 1 máy); ô nhập không còn bị che
- [x] ~~Lần nói đầu tiên chờ nạp mô hình~~ → nạp sẵn ở nền
- [x] ~~APK debug ~97 MB~~ → bản phát hành dạng App Bundle chỉ 35 MB (APK release 86 MB vì thư viện native không nén)
- [x] ~~Hàng chọn ngôn ngữ ở nửa trên không cuộn được (trôi ngược khi xoay)~~ → chip tự xuống dòng, luôn đủ 5 ngôn ngữ
- [x] ~~Bấm tải gói làm app crash (thiếu quyền ACCESS_NETWORK_STATE)~~
- [x] ~~Bong bóng cao thêm (dịch ngược, dịch bù) mà danh sách không cuộn theo → nút ✎ bị che~~
- [x] ~~Giao diện Sáng: biểu tượng thanh trạng thái vẫn màu trắng~~
- [x] ~~Cỡ chữ lớn: chữ trên nút nói bị cắt (Chuyền tay), nhãn tab xuống dòng~~ → nút tự thu chữ, cỡ chữ không áp cho thanh tab
- [ ] Xoá một phiên trong Lịch sử khi nó vẫn là hội thoại đang mở: nói tiếp thì phiên được lưu lại (đúng ý: đó là hội thoại hiện tại)
- [x] ~~Quanh bàn: nút nói ở hai vùng bên quá nhỏ~~ → bỏ ⌨/🔊 khỏi thanh công cụ vùng bên, vùng giữa cao hơn
- [x] ~~Tên giọng khó hiểu ("vi-vn-x-gft-local")~~ → "Giọng 1, 2…"
- [ ] Chuyền tay 4 người: khung hội thoại còn thấp (4 hàng chọn ngôn ngữ + 4 hàng nút); ẩn khi đang gõ
- [ ] Rảnh tay: câu nói ra đúng lúc máy đang đọc bản dịch thì bị bỏ lỡ (máy không nghe khi đang đọc, để không tự dịch giọng của chính nó)
- [ ] Nhận diện ngôn ngữ thêm ~1 s mỗi câu (bộ mã hoá Whisper luôn xử lý khung 30 s) → có thể chạy song song với nhận dạng
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

## Yêu cầu của anh/chị (2026-10-28, lần 2)

- [x] Bố trí lại nút ở chế độ mặt đối mặt: bỏ hàng chip ngôn ngữ, gom vào một thanh công cụ (nút ngôn ngữ ▾ chọn ngay trong vùng, ⌨, 📖, 🔊, ⋮ cho người cầm máy, nút nói) → phần hội thoại cao thêm khoảng 100 dp mỗi nửa — thử trên máy ✓
- [x] Nền mỗi vùng mang màu của người ngồi đó (mặt đối mặt, quanh bàn); Chuyền tay: khung hội thoại nền riêng, hàng điều khiển của mỗi người tô màu người đó, bong bóng viền màu người nói — thử trên máy ✓
- [x] F6.2 bố cục 4 phía · F2.6 rảnh tay · F3.4 nhận diện ngôn ngữ · chọn giọng · đặt tên (các mục ở trên)

## Yêu cầu của anh/chị (2026-10-28, lần 3)

- [x] Cho chọn tải/xoá/dùng bản Whisper nào: tiny, base, small (mục F3.4)
- [x] Thanh tab dưới chỉ còn biểu tượng 💬 🕘 ⬇ ⓘ, cao 56 dp thay vì 80 dp (TalkBack vẫn đọc tên tab) — thử trên máy ✓

## Việc tiếp theo (đề xuất theo thứ tự)

1. 👤 Thử rảnh tay "ai nói cũng được" với người thật (vi + một ngôn ngữ khác), nói gần máy; nghe thử tốc độ đọc; chế độ riêng tư
2. 👤 Thử các mục ⚠️ cũ: hỏi quyền micro (cài mới app), dùng dữ liệu di động khi tải, máy thiếu giọng offline
3. Phát hành: 👤 tạo khoá ký, kiểm tra kích thước bản release, ảnh chụp màn hình
4. Còn lại của v1: F3.2 chữ tạm thời · F3.5 mô hình theo cấu hình máy
5. 👤 Song song: thử zh/ko/ja trên máy, đo máy yếu, ghi âm giọng vùng miền

## Nhật ký cập nhật

| Ngày | Commit | Thay đổi |
|---|---|---|
| 2026-10-28 | `04469d4` | Tạo checklist; Giai đoạn 1 xong, MVP đang làm |
| 2026-10-28 | `e297de9` | F6.6 chữ lớn, F6.7 sao chép, F10.3 chẩn đoán, nạp sẵn mô hình, F9.3 xoá hội thoại, sửa bàn phím che ô nhập |
| 2026-10-28 | — | Thử đổi ngôn ngữ giữa hội thoại trên máy: ghi 3 lỗi (F1.5) |
| 2026-10-28 | `3552b6c` | Sửa đổi ngôn ngữ giữa hội thoại (F1.5), icon mới, chữ theo ngôn ngữ từng nửa, chữ lớn theo người đọc |
| 2026-10-28 | `a226e88` | F2.1, F2.5, F5.1, F5.4, F6.3, F8.2 (Wi-Fi, dung lượng, tải nền), F10.1 song ngữ — chưa thử trên máy vì mất kết nối |
| 2026-10-28 | `f358c16` | Thử trên máy các mục MVP mới; sửa crash khi tải gói, hàng chọn ngôn ngữ ở nửa trên; thêm route TTS vào Chẩn đoán |
| 2026-10-28 | `53e3c71` | v1: F7.2 câu mẫu, F6.5 sửa câu, F4.5 dịch ngược, F9 lịch sử, F5.3 tốc độ đọc, F10.2 cỡ chữ & giao diện, F1.1 3–4 người, F1.3 màu ghế; cấu hình ký release, chính sách quyền riêng tư — chưa thử trên máy (máy chưa cắm) |
| 2026-10-28 | `d1bb405` | Thử v1 trên máy: câu mẫu, sửa câu, dịch ngược, lịch sử, 3 người, cỡ chữ, giao diện ✓; sửa tự cuộn, cuộn câu mẫu ở nửa xoay, màu thanh trạng thái, nút nói bị cắt chữ |
| 2026-10-28 | `fb828f0` | Thanh công cụ gọn + nền phân vùng (yêu cầu mới), F6.2 Quanh bàn, F2.6 rảnh tay, F3.4 gói nhận diện ngôn ngữ (103 MB), chọn giọng, tên từng người — chưa thử trên máy (máy chưa cắm) |
| 2026-10-28 | `f5abf50` | Thử trên máy: thanh công cụ gọn, nền phân vùng, Quanh bàn 4 người, chữ lớn xoay 4 hướng, rảnh tay, tên, chọn giọng ✓; sửa nút nói vùng bên quá nhỏ, đánh số giọng |
| 2026-10-28 | `157b90b` | F3.4: tải gói trên máy, đo độ chính xác; tự chạy Whisper bằng ONNX Runtime, chỉ chọn trong ngôn ngữ ở bàn + luật theo lượt; tiếng Việt thu từ loa vẫn chưa tin cậy |
| 2026-10-28 | `7c25bcc` | F3.4 chuyển sang Whisper base (160 MB) + bù tiếng Việt + ngưỡng theo lượt 0,5; tự xoá gói cũ; thử trên máy 5/5 |
| 2026-10-28 | `a8c649a` | 3 bản nhận diện ngôn ngữ (tiny/base/small) chọn được; thanh tab chỉ biểu tượng, gọn hơn — thử trên máy ✓ |
| 2026-10-28 | `79defb1` | Giao diện mặc định tiếng Việt, chọn được English trong tab Thông tin — thử trên máy ✓ |
| 2026-10-30 | `3340d64` | Hướng dẫn phát hành Google Play (docs/release.md); kiểm tra AAB 35 MB, căn trang 16 KB, targetSdk 36 |
| 2026-10-30 | `4a1e5d3` | Tài sản Google Play: icon 512, ảnh bìa 1024×500, mô tả vi + en (store/) |
| 2026-10-30 | `1939754` | 5 ảnh chụp màn hình cho Google Play (store/screenshots), cắt từ ảnh thử trước đó |
