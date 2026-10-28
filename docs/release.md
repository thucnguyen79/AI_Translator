# Phát hành bản release

## 1. Tạo khoá ký (một lần)

Khoá ký là "chữ ký" của app: mọi bản cập nhật về sau phải ký bằng đúng khoá này. **Mất khoá = không cập nhật được app trên Google Play**, nên hãy sao lưu file `.jks` và mật khẩu ở nơi an toàn (không đưa lên git).

```bash
keytool -genkeypair -v -keystore release.jks -alias aitranslator -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` có sẵn trong JDK (ví dụ `D:\Tools\jdk-21.0.11+10\bin\keytool.exe`).

## 2. Khai báo khoá cho Gradle

Tạo file `keystore.properties` ở thư mục gốc dự án (đã có trong `.gitignore`):

```properties
storeFile=release.jks
storePassword=...
keyAlias=aitranslator
keyPassword=...
```

Trên CI, thay bằng biến môi trường `RELEASE_STOREFILE`, `RELEASE_STOREPASSWORD`, `RELEASE_KEYALIAS`, `RELEASE_KEYPASSWORD`.
Không có các giá trị này thì bản release được ký bằng khoá debug (chỉ để thử, không đưa lên Play).

## 3. Build

```bash
.\gradlew.bat :app:assembleRelease   # APK
.\gradlew.bat :app:bundleRelease     # App Bundle cho Google Play
```

## 4. Trước khi đưa lên Google Play

- [ ] Tăng `versionCode` / `versionName` trong `app/build.gradle.kts`
- [ ] Đăng [chính sách quyền riêng tư](privacy-policy.md) ở một địa chỉ công khai (ví dụ GitHub Pages) và điền link vào Play Console
- [ ] Mục "An toàn dữ liệu" trên Play Console: không thu thập, không chia sẻ dữ liệu; micro chỉ dùng trên máy
- [ ] Khai báo quyền chạy nền `FOREGROUND_SERVICE_DATA_SYNC` (tải gói ngôn ngữ) kèm video ngắn
- [ ] Ảnh chụp màn hình (điện thoại), icon 512×512, ảnh bìa 1024×500
- [ ] Ghi nguồn & giấy phép mô hình đã có trong tab Thông tin (CC-BY-SA-4.0 cho vibert-capu, OpenRAIL-M cho Supertonic)
