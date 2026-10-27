-- =============================================================================
-- NGANTRIIN: helper administrasi staf
-- =============================================================================
-- Sebelumnya menjadikan seseorang staf butuh dua statement manual (insert ke
-- restaurant_staff, update users.role), dan lupa yang kedua membuat akun
-- tetap di layar pelanggan. Dua fungsi ini melakukan keduanya sekali panggil
-- dan menjaga role tetap selaras saat restoran terakhir dihapus.
--
-- Jalankan dari SQL editor Supabase (postgres) atau dengan service-role key.
-- Sengaja tidak bisa dipanggil pengguna aplikasi: inti 0012 adalah
-- tidak ada yang bisa mempromosikan dirinya sendiri.
--
-- select grant_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
-- select revoke_staff('owner@tacolibre.id', '11111111-1111-4111-8111-111111111111');
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

    -- Kembali jadi pelanggan begitu tidak mengelola apa-apa, supaya aplikasi tidak
    -- mengarahkannya ke dashboard staf yang kosong.
    if not exists (select 1 from public.restaurant_staff where user_id = v_user_id) then
        update public.users set role = 'CUSTOMER' where id = v_user_id;
    end if;
end;
$$;

revoke all on function public.grant_staff(text, uuid) from public, anon, authenticated;
revoke all on function public.revoke_staff(text, uuid) from public, anon, authenticated;
