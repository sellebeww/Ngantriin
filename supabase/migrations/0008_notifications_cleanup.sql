-- =============================================================================
-- NGANTRIIN — retention for public.notifications.
--
-- Every push is persisted (see 0001's comment on this table) so it can be
-- de-duplicated via the unique (queue_id, type) index. Nothing ever deleted
-- those rows: queues are never hard-deleted (cancellation/completion just
-- changes `status`), so notifications accumulated indefinitely.
--
-- pg_cron isn't enabled by default on every Supabase plan/project, so this
-- migration doesn't assume it's available — it only defines the cleanup
-- function itself, callable manually or wired to a scheduler once one exists.
-- If pg_cron IS available on your project, uncomment the block at the bottom
-- to run this automatically once a day.
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
-- Optional scheduling, if pg_cron is enabled on this project
-- (Database → Extensions → pg_cron in the Supabase dashboard):
--
-- create extension if not exists pg_cron;
--
-- select cron.schedule(
--     'cleanup-old-notifications',
--     '0 3 * * *',  -- daily at 03:00 UTC
--     $$select public.cleanup_old_notifications(30);$$
-- );
--
-- Without pg_cron, run `select public.cleanup_old_notifications();` by hand
-- from the SQL editor, or point a scheduled Edge Function / external cron at
-- an RPC call to it.
-- -----------------------------------------------------------------------------
