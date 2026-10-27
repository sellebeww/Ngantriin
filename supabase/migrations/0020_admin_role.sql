-- =============================================================================
-- NGANTRIIN: role ADMIN (hanya nilai enum)
--
-- Sengaja di file sendiri: Postgres tidak mengizinkan nilai enum baru dipakai
-- di transaksi yang menambahkannya, dan 0021 langsung memakainya.
-- Tidak ada yang jadi ADMIN dari aplikasi, lihat grant_admin() di 0021.
-- =============================================================================

alter type public.user_role add value if not exists 'ADMIN';
