-- =============================================================================
-- NGANTRIIN — wire queue changes to the notification Edge Function
--
-- Sections 15 and 37. Every change to `queues` calls queue-notifier, which
-- works out which tickets crossed a threshold and pushes to them.
--
-- The alternative is a Database Webhook configured in the Supabase dashboard
-- (Database -> Webhooks -> queues -> insert, update). Use one or the other,
-- not both, or every event fires twice.
-- =============================================================================

create extension if not exists pg_net with schema extensions;

-- Set these once per project:
--   alter database postgres set app.settings.functions_url =
--       'https://<project-ref>.supabase.co/functions/v1';
--   alter database postgres set app.settings.service_role_key = '<service role key>';

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
        -- Not configured yet: queue state still changes correctly, there is
        -- just nothing to push to.
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
