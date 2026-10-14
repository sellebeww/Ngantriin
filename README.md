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

### Pelanggan

| Onboarding | Login | Beranda |
| :---: | :---: | :---: |
| <img src="docs/screenshots/02-onboarding.png" width="220"> | <img src="docs/screenshots/04-login.png" width="220"> | <img src="docs/screenshots/05-home.png" width="220"> |
| Pengenalan singkat cara kerja aplikasi | Masuk atau buat akun | Restoran terdekat dan panjang antrean langsung |

| Pencarian | Detail Restoran | Berhasil Antre |
| :---: | :---: | :---: |
| <img src="docs/screenshots/06-search.png" width="220"> | <img src="docs/screenshots/07-detail.png" width="220"> | <img src="docs/screenshots/08-join.png" width="220"> |
| Cari dan urutkan restoran | Info antrean, jam buka, jumlah orang | Nomor antrean dan estimasi tunggu |

| Antrean Saya | Notifikasi | Profil |
| :---: | :---: | :---: |
| <img src="docs/screenshots/09-my-queue.png" width="220"> | <img src="docs/screenshots/10-notifications.png" width="220"> | <img src="docs/screenshots/11-profile.png" width="220"> |
| Posisi dan progres antrean real-time | Pemberitahuan giliran sudah dekat | Statistik dan pengaturan akun |

### Staf Restoran

| Dashboard | Kelola Antrean |
| :---: | :---: |
| <img src="docs/screenshots/12-staff-dashboard.png" width="220"> | <img src="docs/screenshots/13-staff-kelola.png" width="220"> |
| Nomor yang sedang dilayani, tombol **Call next** | Panggil, hapus, atau selesaikan tiap tiket |

---

## Fitur

**Untuk pelanggan**

- **Temukan restoran**: daftar terdekat berdasarkan lokasi, pencarian, dan
  urutan berdasarkan jarak, antrean tersingkat, atau rating tertinggi.
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
- **Tahan offline**: tanpa internet, aplikasi tetap menampilkan data terakhir,
  dan aksi yang tertunda dikirim otomatis begitu online lagi.
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

Tanpa kredensial apa pun, aplikasi otomatis berjalan di **demo mode**: backend
lokal di dalam aplikasi dengan enam restoran contoh di Gading Serpong, lengkap
dengan antrean yang hidup di masing-masing restoran. Semua layar, perpindahan
status, dan notifikasi bisa dicoba.

> Demo mode **tidak memverifikasi login**. Email apa pun bisa masuk dan
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

> Aktifkan ekstensi **pg_cron** (Database → Extensions) sebelum menjalankan
> 0012 agar tiket yang tidak check-in hangus tepat waktu. Tanpa pg_cron aturan
> tetap berlaku, hanya dieksekusi saat ada aktivitas di antrean restoran itu.

Tiga berkas tes SQL di `supabase/tests/` (`rls_test.sql`,
`concurrency_test.sql`, `journey_test.sql`) menguji isolasi RLS,
locking/cooldown, dan seluruh alur dari antre sampai meja siap / hangus.
Jalankan di SQL editor atau `psql`, bukan bagian dari tes Android.

Lalu isi kredensial di `local.properties` (di-ignore git, tidak pernah
di-commit):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

CI bisa memakai environment variable dengan nama yang sama. Setelah rebuild,
demo mode otomatis mati.

Untuk menjadikan akun sebagai staf, tambahkan baris di `restaurant_staff`:

```sql
insert into restaurant_staff (restaurant_id, user_id)
values ('11111111-1111-4111-8111-111111111111', '<auth user id>');

update users set role = 'STAFF' where id = '<auth user id>';
```

Ini satu-satunya cara resmi menambah staf. Tabel `restaurant_staff` tidak punya
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

**Demo mode bukan jalan pintas.** `DemoBackend` mengimplementasikan interface
`RemoteDataSource` yang sama dengan Supabase dan menerapkan aturan yang sama
(nomor dari server, satu antrean aktif, tabel transisi status, foto wajib saat
check-in, batas waktu check-in, meja siap). Jadi demo tidak bisa diam-diam menyimpang dari backend asli. Di demo
mode, "foto" check-in hanya berupa penanda, tetapi langkah kameranya tetap
berjalan sungguhan.

---

## Struktur Proyek

```
org.umn.ngantriin
├── core/            Tipe hasil, error, formatter, konektivitas, bahasa
├── domain/          Model (state machine antrean, QueueMath) dan interface repository
├── data/            Room, DataStore, Supabase, demo backend, mapper, repository
├── location/        Lokasi dan jarak (urutan terdekat, jarak di detail restoran)
├── notification/    Layanan FCM, antrean live (foreground service), channel, scheduler milestone
├── work/            WorkManager: sinkronisasi dan antrean penulisan offline
├── navigation/      Rute, NavHost, bottom bar
├── di/              AppContainer
└── ui/              Tema, komponen, dan satu paket per area layar

supabase/
├── migrations/      Skema, fungsi antrean, RLS, seed, hook notifikasi
└── functions/       Edge Function queue-notifier
```

---

## Tes

```bash
./gradlew :app:testDebugUnitTest          # 51 unit test
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
