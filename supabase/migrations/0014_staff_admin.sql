-- =============================================================================
-- NGANTRIIN — staff administration helpers
-- =============================================================================
-- Until now making someone staff meant two hand-written statements (insert into
-- restaurant_staff, update users.role) and forgetting the second one left the
-- account on the customer screens. These two functions do both in one call and
-- keep the role in step when the last restaurant is removed.
--
-- Run from the Supabase SQL editor (postgres) or with the service-role key.
-- They are deliberately not callable by app users: the whole point of 0012 is
-- that nobody can promote themselves.
--
--   select grant_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
--   select revoke_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
-- =============================================================================

create or replace function public.grant_staff(p_email text, p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_user_id uuid;
begin
    select id into v_user_id
    from public.users
    where lower(email) = lower(trim(p_email));
    if v_user_id is null then
        raise exception 'NO_SUCH_USER: % has not signed up yet', p_email;
    end if;

    if not exists (select 1 from public.restaurants where id = p_restaurant_id) then
        raise exception 'NO_SUCH_RESTAURANT: %', p_restaurant_id;
    end if;

    insert into public.restaurant_staff (restaurant_id, user_id)
    values (p_restaurant_id, v_user_id)
    on conflict do nothing;

    update public.users set role = 'STAFF' where id = v_user_id;
end;
$$;

create or replace function public.revoke_staff(p_email text, p_restaurant_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    v_user_id uuid;
begin
    select id into v_user_id
    from public.users
    where lower(email) = lower(trim(p_email));
    if v_user_id is null then
        raise exception 'NO_SUCH_USER: %', p_email;
    end if;

    delete from public.restaurant_staff
    where restaurant_id = p_restaurant_id and user_id = v_user_id;

    -- Back to a customer once they manage nothing, so the app stops routing
    -- them to an empty staff dashboard.
    if not exists (select 1 from public.restaurant_staff where user_id = v_user_id) then
        update public.users set role = 'CUSTOMER' where id = v_user_id;
    end if;
end;
$$;

revoke all on function public.grant_staff(text, uuid) from public, anon, authenticated;
revoke all on function public.revoke_staff(text, uuid) from public, anon, authenticated;
