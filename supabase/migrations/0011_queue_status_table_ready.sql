-- =============================================================================
-- NGANTRIIN: status antrean baru TABLE_READY dan EXPIRED.
--
-- Sengaja di file sendiri: Postgres menolak *memakai* nilai enum di
-- transaksi yang sama dengan penambahannya, dan setiap file migrasi jalan di
-- satu transaksi. 0012 adalah tempat nilai-nilai ini benar-benar dipakai.
--
-- Perjalanan yang dimungkinkan:
--
-- WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
--
-- * CALLED       giliranmu, kamu punya jendela check-in restoran
-- (bawaan 10 menit) untuk datang dan check-in.
-- * CHECKED_IN   kamu sudah di restoran (foto terverifikasi).
-- Boleh lebih awal, langsung dari WAITING / ALMOST_THERE.
-- * TABLE_READY  staf sudah menyiapkan mejamu, menuju host.
-- * EXPIRED      dipanggil tapi tidak check-in dalam jendela. Status akhir.
-- =============================================================================

alter type public.queue_status add value if not exists 'TABLE_READY' after 'CHECKED_IN';
alter type public.queue_status add value if not exists 'EXPIRED' after 'CANCELLED';

alter type public.notification_type add value if not exists 'TABLE_READY' after 'CHECKED_IN';
alter type public.notification_type add value if not exists 'EXPIRED' after 'CANCELLED';
