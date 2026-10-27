-- =============================================================================
-- NGANTRIIN: sambungkan perubahan antrean ke Edge Function notifikasi
--
-- Setiap perubahan pada `queues` memanggil queue-notifier, yang
-- mencari tiket mana yang melewati ambang lalu mengirim push ke mereka.
--
-- Alternatifnya Database Webhook yang diatur di dashboard Supabase
-- (Database -> Webhooks -> queues -> insert, update). Pakai salah satu,
-- jangan keduanya, kalau tidak setiap event terkirim dua kali.
-- =============================================================================

create extension if not exists pg_net with schema extensions;

-- Atur ini sekali per proyek:
-- alter database postgres set app.settings.functions_url =
-- 'https://<project-ref>.supabase.co/functions/v1';
-- alter database postgres set app.settings.service_role_key = '<service role key>';

create or replace function public.notify_queue_change()
returns trigger
language plpgsql
security definer set search_path = public, extensions
as $$
declare
    v_url  text := current_setting('app.settings.functions_url', true);
    v_key  text := current_setting('app.settings.service_role_key', true);
begin
    if v_url is null then
        -- Belum dikonfigurasi: state antrean tetap berubah dengan benar,
        -- hanya tidak ada yang dikirimi push.
        return coalesce(new, old);
    end if;

    perform net.http_post(
        url := v_url || '/queue-notifier',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer ' || coalesce(v_key, '')
        ),
        body := jsonb_build_object(
            'type', tg_op,
            'record', to_jsonb(new),
            'old_record', to_jsonb(old)
        )
    );

    return coalesce(new, old);
end;
$$;

drop trigger if exists queues_notify on public.queues;
create trigger queues_notify
    after insert or update on public.queues
    for each row execute function public.notify_queue_change();
