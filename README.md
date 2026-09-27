
# ChatGPT Multi-Session - Android WebView Wrapper

Aplikasi Android WebView stabil untuk chatgpt.com dengan fokus **multi-account isolation**.

## Fitur Utama Sesuai Spec

1. **Multi-Account True Isolation**
   - Android 14+ (API 34) + WebView 115+ : menggunakan `android.webkit.ProfileStore` / `Profile`
   - Setiap `AccountProfile.profileName` -> `acc_<id>` punya `CookieManager`, `LocalStorage`, `IndexedDB`, `CacheStorage` terpisah otomatis oleh sistem.
   - Pre-14: fallback pakai AndroidX `ProfileStore` + `setDataDirectorySuffix` best-effort. CookieManager masih global di versi lama (keterbatasan WebView). Dokumentasi resmi mengakui ini.

2. **Session Persistence**
   - Daftar akun disimpan di DataStore (JSON). Profile data disimpan di direktori WebView sendiri (`app_webview/`). Tidak di-clear saat close.
   - Restart HP / Force Close: akun tetap ada, selama session ChatGPT masih valid.

3. **Swipe Switching**
   - Gesture horizontal threshold 120dp. Tidak mengganggu scroll vertikal karena pakai `detectHorizontalDragGestures` di container luar WebView.
   - Indikator "Switched to: X" auto-dismiss 1.5s.

4. **Account Manager BottomSheet**
   - Add, Rename, Delete, Clear cache, Clear session, reorder (drag).
   - Delete menghapus ProfileStore + file profile via `WebViewProfileManager.deleteProfileData()`.

5. **WebView Compatibility**
   - JS, DOM, cookies, third-party cookies enabled.
   - `setSupportMultipleWindows(true)` untuk popup OAuth.
   - `shouldOverrideUrlLoading` whitelist: chatgpt.com, openai.com, accounts.google.com, login.microsoftonline.com, appleid.apple.com
   - File upload: `onShowFileChooser` + `ActivityResultContracts` multi-file.
   - Camera/Mic: `onPermissionRequest` + runtime permission via `PermissionHandler`.
   - Download: `DownloadListener` -> `DownloadManager`.
   - Back: `canGoBack()` -> `goBack()` else exit.

6. **Crash Resistance**
   - `onRenderProcessGone` -> reload, return true (handle crash).
   - `configChanges` di Manifest agar WebView tidak recreate saat rotate.
   - Cache WebView LRU 3 instance untuk hemat RAM.

7. **Security & Privacy**
   - Tidak menyimpan password/token manual. Semua lewat WebView storage.
   - Tidak ada analytics, tidak ada JS injection pencuri cookie.
   - Logcat tidak mencatat cookie.

## Struktur
```
data/AccountProfile
storage/AccountStorage (DataStore)
webview/WebViewProfileManager (Profile API)
webview/ChatGPTWebViewClient
webview/ChatGPTChromeClient
ui/MainActivity (Compose)
ui/AccountManagerBottomSheet
utils/NetworkMonitor, PermissionHandler, DownloadHandler
```

## Build
- Android Studio Hedgehog+
- compileSdk 34, minSdk 24, targetSdk 34
- Dependency: androidx.webkit:webkit:1.9.0

Buka folder `ChatGPTMultiAccount` di Android Studio -> Sync -> Run.

## Testing sesuai spec
Test 1: Login A -> restart -> tetap login (Profile persist)
Test 2: Add B login beda -> A & B tetap login (isolasi)
Test 3: Swipe A<->B tidak logout
Test 4: Force close -> daftar & session valid tetap ada
Test 5: Restart HP -> sama
Test 6: Delete B tidak ganggu A
Test 7: Google OAuth redirect kembali ke WebView
Test 8: Upload file -> picker muncul
Test 9: Download -> DownloadManager notifikasi
Test 10: Offline -> banner offline, tidak crash

## Keterbatasan Android WebView
Pre-Android 14: CookieManager adalah singleton. Isolasi sempurna hanya bisa via multi-process + `setDataDirectorySuffix` per proses. Untuk menjaga stabilitas dan ringan, kami pakai best-effort single-process + dokumentasi. Untuk isolasi 100% di semua OS, targetkan minSdk 34 atau gunakan multi-process (tradeoff RAM).

Jika butuh multi-process versi: tiap akun di Service dengan proses `:acc_<id>` dan WebView di dalamnya, plus AIDL untuk komunikasi. Bisa ditambahkan iterasi berikutnya.

## Tidak melakukan
- Tidak clone UI ChatGPT, hanya WebView ke https://chatgpt.com/
- Tidak pakai User-Agent palsu
- Tidak pakai CSS injection dark mode
