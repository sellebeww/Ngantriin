-- =============================================================================
-- NGANTRIIN: retensi untuk public.notifications.
--
-- Setiap push disimpan (lihat komentar 0001 untuk tabel ini) supaya bisa
-- dideduplikasi lewat index unique (queue_id, type). Tidak ada yang pernah
-- menghapus baris itu: antrean tidak pernah dihapus permanen (pembatalan/selesai
-- hanya mengubah `status`), jadi notifikasi menumpuk tanpa batas.
--
-- pg_cron tidak aktif secara default di setiap plan/proyek Supabase, jadi
-- migrasi ini tidak mengandalkannya. Ia hanya mendefinisikan fungsi pembersihnya,
-- yang bisa dipanggil manual atau dipasang ke scheduler kalau sudah ada.
-- Kalau pg_cron ADA di proyekmu, hapus komentar blok di bawah
-- supaya jalan otomatis sekali sehari.
-- =============================================================================

create or replace function public.cleanup_old_notifications(
    p_older_than_days integer default 30
) returns integer
language plpgsql
security definer set search_path = public
as $$
declare
    v_deleted integer;
begin
    delete from public.notifications
    where created_at < now() - make_interval(days => p_older_than_days);

    get diagnostics v_deleted = row_count;
    return v_deleted;
end;
$$;

comment on function public.cleanup_old_notifications(integer) is
    'Deletes notifications older than p_older_than_days (default 30). '
    'Call manually (select public.cleanup_old_notifications();) or schedule it '
    '— see the pg_cron block commented out in 0008_notifications_cleanup.sql.';

-- -----------------------------------------------------------------------------
-- Penjadwalan opsional, kalau pg_cron aktif di proyek ini
-- (Database -> Extensions -> pg_cron di dashboard Supabase):
--
-- create extension if not exists pg_cron;
--
-- select cron.schedule(
-- 'cleanup-old-notifications',
-- '0 3 * * *',  -- setiap hari pukul 03:00 UTC
-- $$select public.cleanup_old_notifications(30);$$
-- );
--
-- Tanpa pg_cron, jalankan `select public.cleanup_old_notifications();` manual
-- dari SQL editor, atau arahkan Edge Function terjadwal / cron eksternal ke
-- panggilan RPC-nya.
-- -----------------------------------------------------------------------------
