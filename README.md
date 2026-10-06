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
- **Notifikasi bertahap**: pelanggan diberi tahu saat giliran mendekat dan saat
  dipanggil. Isi notifikasi tidak pernah menyebut nama restoran.
- **Check-in dengan foto**: setelah dipanggil, pelanggan memotret dirinya di
  restoran sebagai bukti kehadiran.
- **Riwayat dan ulasan**: lihat antrean sebelumnya dan beri ulasan restoran.
- **Tahan offline**: tanpa internet, aplikasi tetap menampilkan data terakhir,
  dan aksi yang tertunda dikirim otomatis begitu online lagi.
- **Dua bahasa** yang bisa dipilih per aplikasi, terpisah dari bahasa sistem.

**Untuk staf restoran**

- Dashboard per restoran: nomor yang dilayani, nomor berikutnya, dan jumlah
  menunggu / dipanggil / sedang dilayani.
- **Call next** untuk memanggil antrean berikutnya dengan satu ketukan.
- Kelola tiap tiket: **Call**, **Remove**, atau **Complete**.
- Tinjau foto check-in sebelum tiket dianggap sudah duduk.

**Pencegahan penyalahgunaan**

- Satu pelanggan hanya boleh punya satu antrean aktif (dijaga unique index di
  database, bukan hanya cek di aplikasi).
- Jeda 60 detik setelah membatalkan sebelum boleh antre lagi di restoran yang
  sama, untuk mencegah spam join → cancel → join.

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
   **Call next**, lalu lihat antrean bergerak.

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

Dua berkas tes SQL di `supabase/tests/` (`rls_test.sql`,
`concurrency_test.sql`) menguji isolasi RLS dan perilaku locking/cooldown.
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
policy INSERT/UPDATE/DELETE sama sekali, jadi tidak ada pengguna yang bisa
menjadikan dirinya staf lewat aplikasi.

### 2. Push notification

```bash
# Taruh file dari Firebase di app/ (plugin Gradle hanya aktif jika file ada)
cp ~/Downloads/google-services.json app/

supabase functions deploy queue-notifier --no-verify-jwt
supabase secrets set FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON='{...}'
```

Sebelum FCM disambungkan, `QueueNotificationScheduler` menghitung milestone
yang sama secara lokal selama aplikasi berjalan. Begitu FCM aktif, push yang
mengambil alih.

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
  langsung. Check-in ditolak kalau belum ada foto.
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
check-in). Jadi demo tidak bisa diam-diam menyimpang dari backend asli. Di demo
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
├── notification/    Layanan FCM, channel, scheduler milestone
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
./gradlew :app:testDebugUnitTest          # 41 unit test
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
