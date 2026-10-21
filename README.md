# Ngantriin

**Good Food • Less Wait • Happier You**

Aplikasi Android untuk antrean restoran dan F&B secara digital. Pelanggan cari
restoran, lihat panjang antrean yang sebenarnya, ambil nomor antrean dari HP,
lalu bebas pergi dulu. Aplikasi akan memberi tahu saat giliran sudah dekat, dan
saat kembali pelanggan melakukan check-in dengan foto di restoran. Staf
mengelola antrean yang sama lewat dashboard dan bisa meninjau foto tersebut.

Dibuat dengan Kotlin, Jetpack Compose, MVVM, Supabase, Room, WorkManager, dan
Firebase Cloud Messaging.

---

## Tampilan Aplikasi

Bahasa desainnya ("Karcis": tiket antrean sebagai satu-satunya objek fisik di
aplikasi) dijelaskan di [DESIGN.md](DESIGN.md); aset dan sumbernya di
[ASSETS.md](ASSETS.md).

### Pelanggan

<table width="100%">
<tr>
<th width="33%">Onboarding</th>
<th width="33%">Login</th>
<th width="33%">Jelajah</th>
</tr>
<tr align="center">
<td><img src="docs/screenshots/02-onboarding.png" width="220" height="489"></td>
<td><img src="docs/screenshots/04-login.png" width="220" height="489"></td>
<td><img src="docs/screenshots/05-home.png" width="220" height="489"></td>
</tr>
<tr align="center">
<td>Pengenalan singkat cara kerja aplikasi</td>
<td>Masuk atau buat akun</td>
<td>Restoran terdekat dan panjang antrean langsung</td>
</tr>
</table>

| Pencarian | Detail Restoran | Berhasil Antre |
| :---: | :---: | :---: |
| <img src="docs/screenshots/06-search.png" width="220" height="489"> | <img src="docs/screenshots/07-detail.png" width="220" height="489"> | <img src="docs/screenshots/08-join.png" width="220" height="489"> |
| Cari dan urutkan restoran | Info antrean, jam buka, jumlah orang | Nomor antrean dan estimasi tunggu |

| Antrean Saya | Notifikasi | Profil |
| :---: | :---: | :---: |
| <img src="docs/screenshots/09-my-queue.png" width="220" height="489"> | <img src="docs/screenshots/10-notifications.png" width="220" height="489"> | <img src="docs/screenshots/11-profile.png" width="220" height="489"> |
| Posisi dan progres antrean real-time | Pemberitahuan giliran sudah dekat | Statistik dan pengaturan akun |

<div align="center">

| Favorit di Jelajah | Cari Favorit |
| :---: | :---: |
| <img src="docs/screenshots/14-favorit.png" width="220" height="489"> | <img src="docs/screenshots/15-cari-favorit.png" width="220" height="489"> |
| Restoran yang ditandai muncul sendiri di Jelajah | Satu ketukan untuk menyaring favorit |

</div>

### Staf Restoran

<div align="center">

| Dashboard | Kelola Antrean |
| :---: | :---: |
| <img src="docs/screenshots/12-staff-dashboard.png" width="220" height="489"> | <img src="docs/screenshots/13-staff-kelola.png" width="220" height="489"> |
| Nomor yang dilayani, tombol **Panggil berikutnya**, dan pratinjau antrean | Cari nomor tiket, lalu panggil, tandai, atau keluarkan tiap tiket |

</div>

---

## Fitur

**Untuk pelanggan**

- **Temukan restoran**: daftar terdekat berdasarkan lokasi, pencarian, dan
  urutan berdasarkan jarak, antrean tersingkat, atau rating tertinggi.
- **Favorit**: ketuk ikon hati pada restoran mana pun. Restoran favorit muncul
  di bagian **Favoritmu** pada Jelajah, dan bisa disaring lewat chip
  **Favorit** di halaman Cari. Tersimpan di perangkat, tidak perlu akun.
- **Antrean transparan**: jumlah grup yang menunggu, estimasi waktu tunggu,
  status buka/tutup, dan nomor yang sedang dilayani.
- **Ambil antrean dari HP**: pilih jumlah orang, nomor antrean dibuat oleh
  server (bukan klien), jadi tidak bisa bentrok.
- **Notifikasi bertahap**: pelanggan diberi tahu saat giliran mendekat, saat
  dipanggil, saat check-in berhasil, saat meja siap, dan saat kunjungan selesai.
  Isi notifikasi tidak pernah menyebut nama restoran.
- **Antrean live di bilah notifikasi**: selama punya tiket aktif, posisi dan
  hitung mundur check-in tetap terlihat walau aplikasi ditutup.
- **Check-in dengan foto, batas 10 menit**: setelah dipanggil, pelanggan punya
  10 menit untuk check-in (memotret dirinya di restoran). Lewat dari itu, tiket
  **hangus** otomatis. Yang sudah sampai lebih awal boleh check-in kapan saja
  tanpa kehilangan urutan.
- **Meja siap**: setelah check-in, pelanggan menunggu sampai staf menandai
  mejanya siap, lalu diberi tahu untuk menemui staf.
- **Riwayat dan ulasan**: daftar kunjungan dengan filter (selesai, batal &
  hangus), halaman detail berisi linimasa lengkap dan ulasan yang pernah
  diberikan. Setelah kunjungan selesai, aplikasi langsung menawarkan ulasan.
- **Bagikan antrean**: kirim nomor tiket dan jumlah grup di depanmu ke teman
  lewat menu bagikan Android.
- **Animasi halus**: transisi antarlayar yang singkat, hati favorit yang
  "berdenyut" saat diketuk, angka antrean yang bergulir saat berubah, dan
  daftar yang bergeser lembut saat ada tiket atau restoran masuk dan keluar.
  Mengikuti pengaturan animasi sistem Android.
- **Tahan offline**: tanpa internet, aplikasi tetap menampilkan data terakhir,
  dan aksi yang tertunda dikirim otomatis begitu online lagi.
- **Lupa kata sandi**: tautan di email membuka aplikasi langsung ke layar kata
  sandi baru. Pengaturan juga memuat halaman bantuan, privasi, dan ketentuan.
- **Dua bahasa** yang bisa dipilih per aplikasi, terpisah dari bahasa sistem.

**Untuk staf restoran**

- Dashboard per restoran: nomor yang dilayani, nomor berikutnya, dan jumlah
  menunggu / dipanggil / di lokasi.
- **Call next** untuk memanggil antrean berikutnya dengan satu ketukan. Kalau
  pelanggan berikutnya sudah check-in lebih awal, mejanya langsung ditandai siap.
- Antrean dikelompokkan per tahap: **Meja siap**, **Sudah datang**,
  **Dipanggil** (dengan sisa waktu check-in), dan **Menunggu**.
- Aksi per tiket sesuai tahap: panggil, tandai sudah datang, meja siap, sudah
  duduk (selesai), tidak hadir, atau hapus.
- **Pratinjau antrean** di dashboard: tiga tiket berikutnya beserta ukuran
  rombongan dan berapa lama mereka menunggu, tanpa membuka halaman kelola.
- **Cari nomor tiket** di halaman kelola (muncul saat antrean cukup panjang).
- **Konfirmasi sebelum mengeluarkan tiket**: tombol *Hapus* dan *Tidak hadir*
  meminta persetujuan dulu, karena tiket yang dibatalkan tidak bisa kembali.
- Tinjau foto check-in pelanggan.

**Pencegahan penyalahgunaan**

- Satu pelanggan hanya boleh punya satu antrean aktif (dijaga unique index di
  database, bukan hanya cek di aplikasi).
- Jeda 60 detik setelah membatalkan sebelum boleh antre lagi di restoran yang
  sama, untuk mencegah spam join → cancel → join.
- Jam buka dicek di server, bukan hanya di aplikasi.
- Foto check-in harus benar-benar terunggah untuk tiket itu; role staf tidak
  bisa diubah sendiri; ulasan hanya bisa diubah rating dan isinya.

---

## Cara Menjalankan

```bash
./gradlew :app:installDebug
```

Tanpa kredensial apa pun, aplikasi otomatis berjalan di **mode contoh**:
backend lokal di dalam aplikasi dengan enam restoran di Gading Serpong, lengkap
dengan antrean yang hidup di masing-masing restoran. Semua layar, perpindahan
status, dan notifikasi bisa dicoba.

> Mode contoh **tidak memverifikasi login**. Email apa pun bisa masuk dan
> password tidak disimpan maupun dicek. Email berawalan `staff` (contoh
> `staff@tacolibre.id`) masuk sebagai **STAFF** dan membuka dashboard
> restoran.

**Kebutuhan:** JDK 17+, Android SDK API 37 dengan build-tools 37, emulator atau
perangkat dengan Android API 27+.

### Coba alur utamanya

1. Login dengan `you@example.com`.
2. Buka **Taco Libre** → **Join Queue**. Kamu dapat nomor `T-008`, dengan 2
   grup di depan dan estimasi ~6 menit (2 grup × 3 menit).
3. Buka **My Queue**. Status sudah `ALMOST_THERE` dan notifikasi terkirim,
   karena grup di depan kurang dari empat.
4. Keluar, login sebagai `staff@tacolibre.id`, pilih **Taco Libre**, tekan
   **Call next** sampai giliranmu dipanggil.
5. Login lagi sebagai `you@example.com`. Tiket berstatus **Giliranmu** dengan
   hitung mundur 10 menit. Tekan **Check in now** dan ambil foto.
6. Sebagai staf, tandai tiketmu **Meja siap**, lalu **Sudah duduk**. Aplikasi
   pelanggan menampilkan "Mejamu sudah siap", lalu layar terima kasih dan
   ajakan memberi ulasan.

---

## Menyambungkan Backend Sungguhan

### 1. Supabase

Buat project Supabase, lalu jalankan migrasi berurutan:

```bash
supabase db push   # atau tempel isi supabase/migrations/*.sql ke SQL editor
```

| Berkas | Isinya |
| --- | --- |
| `0001_schema.sql` | Tabel, enum, trigger profil, `queue_stats` |
| `0002_queue_functions.sql` | State machine antrean: alokasi nomor, transisi status |
| `0003_rls.sql` | Row Level Security dan realtime publication |
| `0004_seed.sql` | Enam restoran contoh |
| `0005_notify_hook.sql` | Memanggil Edge Function notifikasi tiap antrean berubah |
| `0006_checkin_photo.sql` | Check-in wajib foto: bucket Storage `checkin-photos` dan policy-nya |
| `0007_join_queue_locking_and_cooldown.sql` | Row-lock pada `join_queue` dan jeda 60 detik setelah batal |
| `0008_notifications_cleanup.sql` | `cleanup_old_notifications()` untuk menghapus notifikasi lama |
| `0009_available_seats.sql` | Kursi kosong: walk-in tanpa antre |
| `0010_more_restaurants.sql` | Restoran contoh tambahan |
| `0011_queue_status_table_ready.sql` | Status baru `TABLE_READY` dan `EXPIRED` |
| `0012_check_in_window_and_table_ready.sql` | Batas check-in 10 menit, check-in lebih awal, meja siap, jam buka di server, verifikasi foto, pengetatan RLS, webhook ber-secret, jadwal pg_cron |
| `0013_fcm_token_hygiene.sql` | Satu token perangkat hanya milik satu akun: trigger + unique index, agar push akun lama tidak jatuh ke HP yang sudah dipakai akun lain |
| `0014_staff_admin.sql` | `grant_staff(email, restaurant_id)` dan `revoke_staff(...)`: jadikan atau cabut staf dalam satu panggilan, hanya bisa dijalankan dari SQL editor / service role |

> Aktifkan ekstensi **pg_cron** (Database → Extensions) sebelum menjalankan
> 0012 agar tiket yang tidak check-in hangus tepat waktu. Tanpa pg_cron aturan
> tetap berlaku, hanya dieksekusi saat ada aktivitas di antrean restoran itu.

Lima berkas tes SQL di `supabase/tests/` (`rls_test.sql`,
`concurrency_test.sql`, `journey_test.sql`, `fcm_token_test.sql`, `staff_admin_test.sql`) menguji
isolasi RLS, locking/cooldown, seluruh alur dari antre sampai meja siap /
hangus, dan kepemilikan token FCM. Cara termudah menjalankannya (butuh Docker):

```bash
./scripts/sql-tests.sh   # Postgres sekali pakai, tidak menyentuh project Supabase
```

Skrip itu menerapkan semua migrasi ke image `supabase/postgres` lokal lalu
menjalankan keempat tes, dan juga dipakai CI. Berkas `ci_bootstrap.sql` hanya
menambal bagian yang di Supabase hosted disediakan layanan lain (tabel Storage,
`auth.uid()` versi baru); jangan dijalankan ke project sungguhan.

**Reset password.** Tautan di email reset membuka aplikasi lewat `ngantriin://auth`.
Di Supabase Dashboard → Authentication → URL Configuration, tambahkan
`ngantriin://auth` ke **Redirect URLs**; tanpa itu tautan akan diarahkan ke Site
URL dan layar kata sandi baru tidak muncul. Buka tautan di HP yang sama dengan
aplikasinya. Alur ini belum teruji ujung ke ujung dengan project Supabase asli.

Lalu isi kredensial di `local.properties` (di-ignore git, tidak pernah
di-commit):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

CI bisa memakai environment variable dengan nama yang sama. Setelah rebuild,
mode contoh otomatis mati.

Untuk menjadikan akun sebagai staf, akun itu harus sudah mendaftar, lalu jalankan
di SQL editor:

```sql
select grant_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
select revoke_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
```

`grant_staff` menambah baris `restaurant_staff` sekaligus mengubah role;
`revoke_staff` mengembalikan akun menjadi pelanggan saat restoran terakhirnya
dicabut. Ini satu-satunya cara resmi mengelola staf; fungsinya tidak bisa
dipanggil dari aplikasi. Tabel `restaurant_staff` tidak punya
policy INSERT/UPDATE/DELETE sama sekali, dan kolom `role` tidak bisa diubah
dari aplikasi, jadi tidak ada pengguna yang bisa menjadikan dirinya staf.

### 2. Push notification

```bash
# Taruh file dari Firebase di app/ (plugin Gradle hanya aktif jika file ada)
cp ~/Downloads/google-services.json app/

supabase functions deploy queue-notifier --no-verify-jwt
supabase secrets set QUEUE_NOTIFIER_SECRET=<string acak> \
    FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON='{...}'
```

Lalu di SQL editor (secret yang sama):

```sql
alter database postgres set app.settings.functions_url =
    'https://<project-ref>.supabase.co/functions/v1';
alter database postgres set app.settings.queue_notifier_secret = '<string acak>';
```

Perilaku token: aplikasi mendaftarkan token saat login dan saat Firebase
menggantinya, serta menghapusnya dari profil saat logout. Edge Function mencoba
ulang pada 429/5xx dan error jaringan, membuang token yang dilaporkan
`UNREGISTERED`, dan mencatat error lain ke log fungsi. FCM belum teruji ujung
ke ujung tanpa `google-services.json` dan service account milik Anda sendiri.

Sebelum FCM disambungkan, notifikasi tetap jalan lewat jalur lokal:
`QueueTrackingService` (foreground service selama ada tiket aktif) menjaga
aplikasi tetap memantau antrean walau ditutup dari layar, dan
`QueueNotificationScheduler` mengirim milestone yang sama. Jalur ini tidak
bertahan setelah *force stop* atau restart HP; untuk itu FCM yang diperlukan.
Detail lengkap ada di [docs/BACKEND.md](docs/BACKEND.md#notifications).

---

## Arsitektur Singkat

```
Compose screen  →  ViewModel  →  Repository  ┬→  RemoteDataSource  →  Supabase
                   (UiState)    (cache-first) └→  Room (sumber data UI)
```

Tiga keputusan utama:

- **Database yang memegang antrean.** Nomor antrean dibuat oleh `join_queue()`,
  bukan klien. Setiap perpindahan status lewat fungsi `SECURITY DEFINER` yang
  memeriksa aturan lagi, sehingga tabel tetap terkunci untuk penulisan
  langsung. Check-in ditolak kalau foto belum terunggah atau batas 10 menit
  sudah lewat.
- **UI hanya membaca dari Room.** Jaringan hanya menulis ke Room. Kalau request
  gagal, data lama tetap tampil, bukan layar kosong.
- **Realtime hanya mengirim sinyal, bukan data.** Perubahan di Postgres memicu
  satu "tick", lalu repository membaca ulang barisnya. Hasilnya kebal terhadap
  event yang hilang, ganda, atau tidak berurutan.

Tidak ada framework DI. Graph-nya kecil dan tetap, jadi `AppContainer` merangkai
semuanya dengan `by lazy`.

Detail lengkap ada di [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) dan kontrak
SQL di [docs/BACKEND.md](docs/BACKEND.md).

**Mode contoh bukan jalan pintas.** Backend lokalnya mengimplementasikan
interface `RemoteDataSource` yang sama dengan Supabase dan menerapkan aturan
yang sama (nomor dari server, satu antrean aktif, tabel transisi status, foto
wajib saat check-in, batas waktu check-in, meja siap). Jadi perilakunya tidak
bisa diam-diam menyimpang dari backend asli. Di mode contoh, "foto" check-in
hanya berupa penanda, tetapi langkah kameranya tetap berjalan sungguhan.

---

## Struktur Proyek

```
org.umn.ngantriin
├── core/            Tipe hasil, error, formatter, konektivitas, bahasa
├── domain/          Model (state machine antrean, QueueMath) dan interface repository
├── data/            Room, DataStore, Supabase, backend lokal, mapper, repository
├── location/        Lokasi dan jarak (urutan terdekat, jarak di detail restoran)
├── notification/    Layanan FCM, antrean live (foreground service), channel, scheduler milestone
├── work/            WorkManager: sinkronisasi dan antrean penulisan offline
├── navigation/      Rute, NavHost, tab bar (Jelajah, Tiket, Profil)
├── di/              AppContainer
└── ui/              Tema, komponen, dan satu paket per area layar

supabase/
├── migrations/      Skema, fungsi antrean, RLS, seed, hook notifikasi
└── functions/       Edge Function queue-notifier
```

---

## Build Rilis

```bash
./gradlew :app:assembleRelease -PversionCode=2 -PversionName=1.0.1
```

Build rilis memakai R8 (minify + shrink resource). Tanpa keystore hasilnya
`app-release-unsigned.apk`. Untuk menandatangani, isi empat nilai ini di
`local.properties` atau environment (jangan di-commit; `*.jks`, `*.keystore`
dan `local.properties` sudah di-ignore):

```properties
RELEASE_KEYSTORE_FILE=../keystore/ngantriin.jks   # relatif terhadap root repo
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

CI (`.github/workflows/ci.yml`) menjalankan unit test, lint, build debug,
build rilis, dan tes SQL pada setiap push dan pull request, tanpa kredensial
apa pun (mode contoh).

---

## Checklist Sebelum Rilis

Hal-hal berikut butuh akun atau perangkat milik Anda, jadi tidak ada di repo:

- [ ] Semua migrasi `0001`–`0014` sudah dijalankan; ekstensi **pg_cron** aktif.
- [ ] `google-services.json` ada di `app/`; Edge Function `queue-notifier`
      ter-deploy beserta secret `QUEUE_NOTIFIER_SECRET`, `FCM_PROJECT_ID`,
      `FCM_SERVICE_ACCOUNT_JSON`; `app.settings.functions_url` dan
      `app.settings.queue_notifier_secret` sudah diatur.
- [ ] `ngantriin://auth` terdaftar di Redirect URLs Supabase.
- [ ] Keystore rilis dan empat nilai `RELEASE_*` sudah diisi.
- [ ] Satu putaran manual di perangkat sungguhan: daftar, antre, dipanggil,
      check-in dengan foto, meja siap, selesai, ulasan, dan reset password.
- [ ] Teks privasi dan ketentuan di Pengaturan sudah ditinjau untuk restoran
      Anda (isinya draf yang menjelaskan perilaku aplikasi, bukan nasihat hukum).

---

## Tes

```bash
./gradlew :app:testDebugUnitTest          # unit test
./gradlew :app:connectedDebugAndroidTest  # 7 instrumented test, butuh perangkat
./gradlew :app:lintDebug
```

Unit test fokus ke bagian yang bug-nya baru terasa belakangan: tabel transisi
status (`QueueStatusTest`), perhitungan posisi dan estimasi (`QueueMathTest`),
jarak haversine (`GeoPointTest`), jam buka termasuk lewat tengah malam
(`RestaurantOpeningTest`), pemetaan error SQL (`ErrorMapperTest`), serta
formatter dan validator. Instrumented test mencakup query DAO
(`NgantriinDatabaseTest`) dan memastikan notifikasi tidak pernah memuat nama
restoran (`QueueNotificationCopyTest`).
