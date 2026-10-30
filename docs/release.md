# Phát hành "Dịch Du Lịch Offline" lên Google Play

Làm lần lượt từ trên xuống. 👤 = anh/chị tự làm (mật khẩu, tài khoản); 🤖 = nhờ Claude làm được.
Tên menu trong Play Console có thể hiện tiếng Anh hoặc tiếng Việt tuỳ cài đặt — ghi cả hai.

> Đã kiểm tra (2026-10-30): AAB **35 MB** (giới hạn 200 MB) · `targetSdk 36` (yêu cầu API 36 cho app mới) ·
> 6 thư viện native căn trang **16 KB** · chỉ arm64-v8a (64-bit) · mã gói `io.github.thucnguyen79.aitranslator`.

## Bước 1 — 👤 Tạo khoá ký (một lần, ~5 phút)

Mở PowerShell tại thư mục dự án `D:\Projects\AI_Translators`:

```bash
D:\Tools\jdk-21.0.11+10\bin\keytool.exe -genkeypair -v -keystore release.jks -alias dichdulich -keyalg RSA -keysize 4096 -validity 10000
```

- Nhập mật khẩu khoá 2 lần (ghi lại ngay vào trình quản lý mật khẩu).
- Các câu hỏi tên/tổ chức/thành phố: điền tuỳ ý (ví dụ tên, `Ho Chi Minh`, `VN`), cuối cùng gõ `yes`.

Tạo file `keystore.properties` cùng thư mục (file này và `release.jks` đã được git bỏ qua, **không bao giờ đưa lên GitHub**):

```properties
storeFile=release.jks
storePassword=MẬT_KHẨU_VỪA_ĐẶT
keyAlias=dichdulich
keyPassword=MẬT_KHẨU_VỪA_ĐẶT
```

Sao lưu `release.jks` + mật khẩu ra một nơi khác (USB, Google Drive cá nhân). Nếu mất, vẫn xin Google đổi khoá được nhưng mất vài ngày.

## Bước 2 — 🤖 Build bản phát hành

```bash
.\gradlew.bat :app:bundleRelease
```

File cần tải lên: `app\build\outputs\bundle\release\app-release.aab`. Mỗi lần tải bản mới lên Play phải tăng `versionCode` trong `app/build.gradle.kts` (1 → 2 → 3…).

## Bước 3 — 👤 Tạo app trên Play Console

1. Trang chủ Play Console → **Create app** (Tạo ứng dụng).
2. Điền:
   - **App name**: `Dịch Du Lịch Offline`
   - **Default language**: `Vietnamese – vi`
   - **App or game**: App · **Free or paid**: Free
   - Tích 2 ô cam kết (Developer Program Policies, US export laws) → **Create app**.

> Không bán app (Free) thì sau này không đổi sang trả phí được — đúng với app này.

## Bước 4 — 👤 Thử nội bộ trước (khuyên làm, không cần duyệt)

Giúp kiểm tra bản tải từ Play chạy được trên máy của chính anh/chị trước khi mời người khác.

1. **Test and release → Testing → Internal testing** (Kiểm thử nội bộ) → tab **Testers** → **Create email list** → đặt tên "Nội bộ", thêm email Gmail của anh/chị → **Save**.
2. **Create new release** → lần đầu Play hỏi về **Play App Signing** → chọn để Google quản lý khoá ký (mặc định) → tải `app-release.aab` lên.
3. **Release name** tự điền `1 (0.1.0)`. **Release notes** (tiếng Việt):
   ```
   <vi-VN>
   Bản đầu tiên: dịch hội thoại offline Việt–Anh–Trung–Hàn–Nhật.
   </vi-VN>
   ```
4. **Next** → **Save and publish** → tab **Testers** → **Copy link** → mở link trên điện thoại → *Accept invite* → cài từ Play → thử tải 1 gói và nói vài câu.

## Bước 5 — 👤 Điền "App content" (Nội dung ứng dụng)

Menu **Policy and programs → App content** (hoặc mục *Set up your app* trên Dashboard). Từng mục:

| Mục | Chọn / điền |
|---|---|
| **Privacy policy** | `https://github.com/thucnguyen79/AI_Translator/blob/main/docs/privacy-policy.md` |
| **App access** | *All functionality in my app is available without any access restrictions* |
| **Ads** | *No, my app does not contain ads* |
| **Content rating** | *Start questionnaire* → email liên hệ → category **All Other App Types** (hoặc *Utility/Productivity* nếu có) → trả lời **No** cho bạo lực, tình dục, ngôn ngữ thô tục, ma tuý, cờ bạc; *Does the app allow users to interact or exchange content?* → **No** (chia sẻ chữ sang app khác không tính); *Share location?* → **No**; *Digital purchases?* → **No** → **Save → Submit** |
| **Target audience and content** | Độ tuổi: chỉ chọn **18 and over** · *Could your app unintentionally appeal to children?* → **No** |
| **News apps** | *No* |
| **Data safety** | *Does your app collect or share any of the required user data types?* → **No** → Next → Submit. (Micro chỉ xử lý trên máy, không gửi đi nên không tính là thu thập; app không có tài khoản) |
| **Government apps** | *No* |
| **Financial features** | *My app doesn't provide any financial features* |
| **Health** | *My app does not have any health features* |
| **Advertising ID** | *No* (app không dùng mã quảng cáo) |
| **Foreground service permissions** | Hiện ra sau khi đã tải AAB (Bước 4). Chọn loại **Data sync** → mô tả: *"Tải gói ngôn ngữ (mô hình nhận dạng, dịch, giọng đọc) khi người dùng bấm Tải; việc tải tiếp tục khi người dùng rời app và hiển thị thông báo tiến độ. Người dùng có thể huỷ bất cứ lúc nào."* → **Video**: link YouTube (xem bên dưới) |

**Quay video khai báo dịch vụ nền** (~20 giây): trên điện thoại vuốt mở *Quay màn hình* → mở app → tab ⬇ → bấm **Tải** một gói (ví dụ 日本語) → về màn hình chính → kéo thanh thông báo xuống cho thấy "Đang tải gói ngôn ngữ" với tiến độ → dừng quay. Tải lên YouTube chế độ **Không công khai (Unlisted)**, dán link.

## Bước 6 — 👤 Trang Store (Store listing)

1. **Grow users → Store presence → Main store listing** (Trang thông tin chính):
   - **App name**: `Dịch Du Lịch Offline`
   - **Short description**, **Full description**: copy từ [store/listing.md](../store/listing.md) (phần Tiếng Việt)
   - **App icon**: [store/icon-512.png](../store/icon-512.png)
   - **Feature graphic**: [store/feature-1024x500.png](../store/feature-1024x500.png)
   - **Phone screenshots**: 5 ảnh trong [store/screenshots/](../store/screenshots/) (theo thứ tự 01 → 05)
   - Ảnh máy tính bảng: bỏ trống được → **Save**
2. Thêm bản tiếng Anh (khuyên làm, khách nước ngoài thấy mô tả tiếng Anh): trên trang đó **Manage translations → Add your own translations → English (United States) – en-US** → dán phần English của `store/listing.md`; tên tiếng Anh có thể để `Offline Travel Translator`.
3. **Grow users → Store presence → Store settings** (Cài đặt cửa hàng):
   - **App category**: *Travel & Local* (Du lịch & Địa phương)
   - **Tags**: Translator, Travel (tuỳ chọn)
   - **Contact details**: email (bắt buộc, hiện công khai trên Play), website: `https://github.com/thucnguyen79/AI_Translator` (tuỳ chọn) → **Save**

## Bước 7 — 👤 Kiểm thử kín (Closed testing) — nếu Play yêu cầu

Tài khoản cá nhân tạo sau 13/11/2023 phải có **≥ 12 người thử, tham gia liên tục ≥ 14 ngày** trước khi được phát hành chính thức.
Cách biết: trên **Dashboard** của app mới, nếu có thẻ *"Apply for production / Đăng ký quyền truy cập phiên bản chính thức"* kèm điều kiện 12 người · 14 ngày thì phải làm bước này; nếu mục **Production** đã mở sẵn thì sang thẳng Bước 8.

1. **Test and release → Testing → Closed testing** → track *Closed testing - Alpha* → **Manage track**.
2. **Countries / regions** → **Add countries** → Vietnam (và các nước muốn thử) → Save.
3. **Testers** → **Create email list** → thêm **≥ 12 địa chỉ Gmail** (nên mời 15–20 để dư) → Save. Hoặc dùng một Google Group.
4. **Create new release** → **Add from library** (chọn bản đã tải ở Bước 4) → release notes → **Next → Save → Send for review** (Play duyệt bản thử kín, thường vài giờ đến 2 ngày).
5. Khi duyệt xong: tab **Testers** → **Copy link** → gửi cho người thử. Mỗi người: mở link bằng đúng Gmail đã thêm → **Become a tester** → cài app từ Play.
6. Giữ đủ 12 người **không rời nhóm trong 14 ngày**. Nhờ họ dùng thật (tải gói, nói vài câu) — khi xin phát hành Google hỏi về phản hồi của người thử.
7. Trong 14 ngày vẫn sửa lỗi và tải bản mới được (tăng `versionCode`, bản mới không làm mất số ngày đã tính).

## Bước 8 — 👤 Phát hành chính thức (Production)

1. Sau 14 ngày: **Dashboard → Apply for production** → trả lời: đã thử với bao nhiêu người, họ phản hồi gì, đã sửa gì, app sẵn sàng chưa → Submit. Google trả lời trong khoảng 7 ngày.
2. Được duyệt: **Test and release → Production → Countries / regions** → chọn nước (Vietnam, hoặc *Add all*) → **Create new release** → **Add from library** (hoặc tải AAB mới) → release notes → **Next → Save → Send for review**.
3. Bản đầu tiên thường được duyệt trong 1–7 ngày; Play gửi email khi app lên Store.

## Cập nhật về sau

Tăng `versionCode` (+1) và `versionName` → `.\gradlew.bat :app:bundleRelease` → **Production → Create new release** → tải AAB → ghi chú → gửi duyệt.
Mỗi năm Google nâng yêu cầu `targetSdk` (thường hạn 31/8): nâng `targetSdk` trong `gradle/libs.versions.toml`.

## Ghi chú

- Mã gói `io.github.thucnguyen79.aitranslator` cố định vĩnh viễn sau lần tải AAB đầu tiên (app khác của anh/chị dùng dạng `com.thucnguyen79.…`; muốn đồng bộ thì phải đổi **trước** Bước 4).
- Gói ngôn ngữ tải từ GitHub, Hugging Face, Mozilla khi người dùng bấm — là dữ liệu mô hình, không phải mã chạy, nên không vi phạm chính sách tải mã của Play.
- Supertonic dùng giấy phép OpenRAIL-M (có điều khoản hạn chế sử dụng); giấy phép các mô hình được liệt kê trong tab Thông tin của app.
- R8 (thu gọn mã) đang tắt vì sherpa-onnx và Bergamot gọi lớp Kotlin từ mã native.
