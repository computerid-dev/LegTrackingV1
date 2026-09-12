# LegTracking (Jejak Kaki)

Aplikasi Android pelacak lokasi + peta + navigasi, 100% data lokal (tanpa
akun, tanpa server, tanpa login).

## Fitur

- Live tracking pergerakan HP (foreground service + notifikasi)
- Simpan histori jejak ke database lokal (Room)
- Backup data: ekspor & impor ke file `.json`
- Cari lokasi (Nominatim/OpenStreetMap, gratis tanpa API key)
- Navigasi/rute jalan (OSRM, gratis tanpa API key)
- Deteksi offline: banner "Saat ini kamu offline", fitur pencarian &
  rute baru dinonaktifkan, tapi histori & rute tersimpan tetap bisa dibuka

## Stack teknis

- Kotlin native, minSdk 24, targetSdk/compileSdk 34
- Peta: osmdroid (OpenStreetMap)
- Database: Room (lokal, tanpa server)
- Networking: OkHttp (untuk Nominatim & OSRM)
- Build: Gradle 8.7 + Android Gradle Plugin 8.5.2

## Build lokal

Repo ini **tidak menyertakan Gradle wrapper** (`gradlew`), supaya tidak
tergantung file biner yang tidak bisa diverifikasi di lingkungan
pembuatan kode ini. Ada 2 opsi:

1. **Paling gampang:** buka folder ini di Android Studio (versi
   Koala/2024.1 ke atas) — Android Studio otomatis generate wrapper
   dan sinkronisasi Gradle.
2. **Manual:** install Gradle 8.7 di komputer, lalu jalankan:
   ```
   gradle wrapper --gradle-version 8.7
   ./gradlew assembleDebug
   ```

## Build otomatis (CI)

`.github/workflows/main.yml` akan build APK debug setiap push ke
branch `main`, pakai `gradle/actions/setup-gradle` (jadi tidak butuh
wrapper di repo). Hasil APK bisa diunduh dari tab **Actions** →
pilih run terakhir → bagian **Artifacts** → `legtracking-debug-apk`.

## Catatan penting

- Kode ini ditulis manual tanpa akses compiler Android di lingkungan
  pembuatannya, jadi belum pernah benar-benar di-build. Kalau ada
  error saat run `main.yml`, kirim log errornya, nanti diperbaiki.
- Field `applicationId`/`namespace` di `app/build.gradle.kts` masih
  `com.legtracking` — ganti kalau mau publish dengan ID sendiri.
- Rute navigasi pakai server publik OSRM (`router.project-osrm.org`).
  Untuk pemakaian ringan/testing ini aman, tapi kalau trafik user
  banyak, sebaiknya pindah ke server OSRM sendiri atau layanan
  berbayar.
