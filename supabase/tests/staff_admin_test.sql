-- =============================================================================
-- NGANTRIIN: grant_staff / revoke_staff (0014). Satu transaksi, berakhir dengan rollback.
-- psql "$DATABASE_URL" -f supabase/tests/staff_admin_test.sql
-- =============================================================================
begin;

insert into auth.users (id, email) values
    ('e1000000-0000-4000-8000-0000000000e1', 'Staff-Admin@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('e1000000-0000-4000-8000-0000000000e1', 'Staff-Admin@example.com', 'Admin Test', 'CUSTOMER')
on conflict (id) do nothing;

do $$
declare
    v_r1 uuid; v_r2 uuid; v_role text; v_n int; v_failed boolean;
    v_uid constant uuid := 'e1000000-0000-4000-8000-0000000000e1';
begin
    select id into v_r1 from public.restaurants order by id limit 1;
    select id into v_r2 from public.restaurants order by id offset 1 limit 1;

    -- Kasus 1: grant tidak peka huruf besar-kecil, menautkan restoran dan mengisi role.
    perform public.grant_staff('staff-admin@EXAMPLE.com', v_r1);
    select role into v_role from public.users where id = v_uid;
    select count(*) into v_n from public.restaurant_staff where user_id = v_uid;
    if v_role <> 'STAFF' or v_n <> 1 then
        raise exception 'FAILED case 1: role=%, memberships=%', v_role, v_n;
    end if;
    raise notice 'PASSED case 1: grant links the restaurant and promotes the account';

    -- Kasus 2: grant dua kali tidak berbahaya.
    perform public.grant_staff('staff-admin@example.com', v_r1);
    select count(*) into v_n from public.restaurant_staff where user_id = v_uid;
    if v_n <> 1 then raise exception 'FAILED case 2: duplicate membership (%)', v_n; end if;
    raise notice 'PASSED case 2: grant is idempotent';

    -- Kasus 3: menghapus satu dari dua restoran mempertahankan role staf.
    perform public.grant_staff('staff-admin@example.com', v_r2);
    perform public.revoke_staff('staff-admin@example.com', v_r1);
    select role into v_role from public.users where id = v_uid;
    if v_role <> 'STAFF' then raise exception 'FAILED case 3: demoted too early (%)', v_role; end if;
    raise notice 'PASSED case 3: role kept while a restaurant remains';

    -- Kasus 4: menghapus restoran terakhir mengembalikan akun ke CUSTOMER.
    perform public.revoke_staff('staff-admin@example.com', v_r2);
    select role into v_role from public.users where id = v_uid;
    if v_role <> 'CUSTOMER' then raise exception 'FAILED case 4: still % ', v_role; end if;
    raise notice 'PASSED case 4: last revoke demotes the account';

    -- Kasus 5: pengguna yang tidak dikenal adalah error, bukan no-op diam-diam.
    v_failed := false;
    begin
        perform public.grant_staff('nobody@example.com', v_r1);
    exception when others then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 5: unknown email accepted'; end if;
    raise notice 'PASSED case 5: unknown email is rejected';
end;
$$;

-- Kasus 6: pengguna aplikasi tidak bisa memanggil fungsi ini (tidak ada kenaikan sendiri).
set local role authenticated;
set local request.jwt.claims = '{"sub": "e1000000-0000-4000-8000-0000000000e1"}';
do $$
declare v_failed boolean := false;
begin
    begin
        perform public.grant_staff('staff-admin@example.com', (select id from public.restaurants limit 1));
    exception when insufficient_privilege then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 6: authenticated user could call grant_staff'; end if;
    raise notice 'PASSED case 6: app users cannot call grant_staff';
end;
$$;
reset request.jwt.claims;
reset role;

rollback;
