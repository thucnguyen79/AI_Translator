# Phát hành lên Google Play

Hướng dẫn từng bước, theo thứ tự. 👤 = việc anh/chị tự làm (tài khoản, thanh toán, mật khẩu); các bước còn lại có thể nhờ Claude.

> Đã kiểm tra (2026-10-30): bản AAB **35 MB** (giới hạn của Play 200 MB); `targetSdk 36` đạt yêu cầu API 36 cho app mới;
> cả 6 thư viện native đã căn trang **16 KB** (`llvm-readelf`: LOAD align `0x4000`, `zipalign -c -P 16` OK); chỉ arm64-v8a (64-bit).

## Tổng quan thời gian

| Bước | Thời gian |
|---|---|
| 1. Tạo tài khoản nhà phát triển, xác minh danh tính | 1–3 ngày (chờ Google duyệt) |
| 2–4. Khoá ký, build, tạo app trên Play Console, điền thông tin | 1 buổi |
| 5. Kiểm thử kín với **≥ 12 người trong 14 ngày liên tục** (tài khoản cá nhân) | ≥ 14 ngày |
| 6. Xin quyền phát hành chính thức → Google duyệt | vài ngày |

## 1. 👤 Tạo tài khoản Google Play Console

1. Vào <https://play.google.com/console/signup>, đăng nhập bằng tài khoản Google sẽ dùng lâu dài.
2. Chọn loại tài khoản:
   - **Cá nhân**: nhanh, nhưng tài khoản mới **bắt buộc kiểm thử kín ≥ 12 người × 14 ngày** trước khi lên chính thức (bước 5).
   - **Tổ chức** (công ty): cần **số D-U-N-S** của công ty (miễn phí, đăng ký qua Dun & Bradstreet, mất vài ngày đến vài tuần); không bị yêu cầu 12 người × 14 ngày.
3. Trả phí đăng ký một lần **25 USD** (thẻ Visa/Mastercard).
4. Xác minh danh tính (CCCD/hộ chiếu) và số điện thoại; Google duyệt trong vài ngày.

## 2. 👤 Tạo khoá ký (upload key)

Bản lên Play được ký bằng **khoá tải lên (upload key)** của anh/chị; Google giữ khoá ký thật của app (Play App Signing).
Mất upload key thì vẫn xin Google cấp lại được, nhưng mất vài ngày — hãy sao lưu file `.jks` và mật khẩu (trình quản lý mật khẩu, ổ ngoài). **Không đưa lên git.**

```bash
D:\Tools\jdk-21.0.11+10\bin\keytool.exe -genkeypair -v -keystore release.jks -alias aitranslator -keyalg RSA -keysize 4096 -validity 10000
```

Tạo file `keystore.properties` ở thư mục gốc dự án (đã có trong `.gitignore`):

```properties
storeFile=release.jks
storePassword=...
keyAlias=aitranslator
keyPassword=...
```

Trên CI thay bằng biến môi trường `RELEASE_STOREFILE`, `RELEASE_STOREPASSWORD`, `RELEASE_KEYALIAS`, `RELEASE_KEYPASSWORD`.
Không có các giá trị này thì bản release được ký bằng khoá debug — **Play sẽ từ chối** bản đó.

## 3. Build bản phát hành

1. Mỗi lần nộp bản mới: tăng `versionCode` (1, 2, 3…) và sửa `versionName` (0.1.0 → 0.1.1…) trong `app/build.gradle.kts`.
2. Build App Bundle:

   ```bash
   .\gradlew.bat :app:bundleRelease
   ```

   File ra: `app/build/outputs/bundle/release/app-release.aab`.
3. Kiểm tra nhanh trước khi nộp: cài bản release lên máy (`.\gradlew.bat :app:installRelease`), mở app, tải 1 gói, nói thử 1 câu.

## 4. 👤 Tạo app và điền thông tin trên Play Console

**Tạo app**: *Create app* → tên app, ngôn ngữ mặc định **Tiếng Việt – vi**, loại *App*, *Free*, đồng ý các cam kết.

> Tên "AI Translator" rất phổ biến trên Play và dễ bị nhầm/trùng — nên đặt tên riêng, ví dụ "Dịch Du Lịch Offline – 5 thứ tiếng". Đổi tên hiển thị trong `app_name` (`strings.xml`), **không** đổi `applicationId` (`io.github.thucnguyen79.aitranslator` là cố định mãi mãi sau lần tải lên đầu tiên).

Trong mục **"Thiết lập ứng dụng" (Set up your app)** — điền lần lượt:

| Mục | Điền gì cho app này |
|---|---|
| Chính sách quyền riêng tư | `https://github.com/thucnguyen79/AI_Translator/blob/main/docs/privacy-policy.md` (repo đang công khai; nếu sau này chuyển repo về riêng tư thì phải đưa trang này sang GitHub Pages hoặc web khác) |
| Quyền truy cập ứng dụng | Không cần đăng nhập — mọi chức năng dùng được ngay |
| Quảng cáo | Không có quảng cáo |
| Phân loại nội dung | Làm bảng câu hỏi IARC: loại *Utility/Productivity*; không bạo lực, không nội dung người dùng chia sẻ công khai, không mua bán → thường ra mức "Mọi lứa tuổi" |
| Đối tượng mục tiêu | Chọn **18+** (hoặc 13+); không chọn trẻ em để tránh chính sách Gia đình |
| An toàn dữ liệu (Data safety) | **Không thu thập, không chia sẻ dữ liệu**. Micro chỉ xử lý trên máy, không gửi đi → không tính là "thu thập". Có mã hoá khi truyền: Có (chỉ tải gói qua HTTPS). Cho phép xoá dữ liệu: người dùng tự xoá lịch sử trong app |
| Ứng dụng chính phủ / tài chính / sức khoẻ | Không |
| **Quyền dịch vụ chạy nền (Foreground service)** | Khai báo loại **Data sync** (`FOREGROUND_SERVICE_DATA_SYNC`): "tải gói ngôn ngữ do người dùng bấm tải, tiếp tục khi rời app, có thông báo tiến độ". Google yêu cầu **video ngắn** (quay màn hình: bấm Tải gói → về màn hình chính → thông báo tiến độ chạy) — tải lên YouTube (chế độ không công khai) và dán link |

**Trang giới thiệu trên Store (Main store listing)**:

| Thành phần | Yêu cầu |
|---|---|
| Mô tả ngắn | ≤ 80 ký tự, ví dụ: "Dịch hội thoại offline Việt–Anh–Trung–Hàn–Nhật, không cần mạng, miễn phí" |
| Mô tả đầy đủ | ≤ 4000 ký tự: tính năng (nói/nhập/câu mẫu, 2–4 người, rảnh tay, lịch sử), **cần tải gói ngôn ngữ 100–200 MB mỗi thứ tiếng lần đầu**, không quảng cáo, không thu thập dữ liệu, ghi nguồn mô hình mã nguồn mở |
| Icon | PNG 512 × 512 |
| Ảnh bìa (feature graphic) | PNG/JPG 1024 × 500 |
| Ảnh chụp màn hình điện thoại | 2–8 ảnh, tỉ lệ 9:16, cạnh 320–3840 px (Claude có thể chụp từ máy thử qua adb) |
| Thể loại | Công cụ (Tools) hoặc Du lịch & Địa phương (Travel & Local) |

**Giấy phép mô hình** (đã ghi trong tab Thông tin): Supertonic dùng giấy phép OpenRAIL-M — giấy phép này yêu cầu chuyển tiếp các điều khoản hạn chế sử dụng cho người dùng; nên thêm vào mô tả hoặc trang chính sách một dòng dẫn link tới giấy phép Supertonic. SenseVoice (FunASR Model License) và vibert-capu (CC-BY-SA-4.0) cần ghi nguồn — đã có.

## 5. 👤 Kiểm thử kín (bắt buộc với tài khoản cá nhân mới)

1. *Testing → Closed testing* → tạo track, tải `app-release.aab` lên, viết ghi chú phát hành.
2. Thêm người thử bằng danh sách email (Google Groups hoặc danh sách email trong Console) — cần **ít nhất 12 người**.
3. Gửi link tham gia cho họ; mỗi người bấm "Trở thành người thử nghiệm" rồi cài app từ Play.
4. Giữ **≥ 12 người tham gia liên tục ít nhất 14 ngày** (ai rời đi rồi vào lại thì đếm lại từ đầu). Nhờ họ dùng thật: tải gói, nói vài câu — Google có thể hỏi về việc kiểm thử khi xét duyệt.
5. Trong 14 ngày đó có thể sửa lỗi và tải bản mới (tăng `versionCode`).

## 6. 👤 Xin phát hành chính thức

1. Sau 14 ngày: *Dashboard → Apply for production* → trả lời các câu hỏi về đợt kiểm thử (bao nhiêu người, phản hồi gì, đã sửa gì).
2. Google duyệt quyền (thường vài ngày) → *Production → Create release* → tải AAB → chọn quốc gia → gửi duyệt.
3. Bản đầu tiên thường được duyệt trong 1–7 ngày.

## Cập nhật về sau

Tăng `versionCode` → `bundleRelease` → *Production → Create new release* → tải AAB → ghi chú thay đổi.
Mỗi năm Google nâng yêu cầu `targetSdk` (thường hạn cuối tháng 8): kiểm tra và nâng `targetSdk` trong `gradle/libs.versions.toml`.

## Lưu ý kỹ thuật

- Gói ngôn ngữ tải từ GitHub, Hugging Face, máy chủ Mozilla khi người dùng bấm — là dữ liệu mô hình, không phải mã chạy, nên không vi phạm chính sách tải mã của Play.
- R8 (thu gọn mã) đang tắt vì sherpa-onnx và Bergamot gọi lớp Kotlin từ mã native; bật thì phải viết luật giữ lớp và thử lại kỹ.
- Kích thước tải về từ Play (~35 MB) chưa gồm gói ngôn ngữ: nói rõ trong mô tả để tránh đánh giá xấu.
