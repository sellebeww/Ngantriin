-- =============================================================================
-- NGANTRIIN — restaurant registration (0016). One transaction, ends in rollback.
--   psql "$DATABASE_URL" -f supabase/tests/restaurant_registration_test.sql
--
-- Covers the whole chain: register -> staff link -> visible to customers ->
-- other staff blocked -> pause hides it and blocks joins.
-- =============================================================================
begin;

insert into auth.users (id, email) values
    ('f1000000-0000-4000-8000-0000000000f1', 'reg-owner@example.com'),
    ('f2000000-0000-4000-8000-0000000000f2', 'reg-other-staff@example.com'),
    ('f3000000-0000-4000-8000-0000000000f3', 'reg-customer@example.com'),
    ('f4000000-0000-4000-8000-0000000000f4', 'reg-late@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('f1000000-0000-4000-8000-0000000000f1', 'reg-owner@example.com', 'Owner', 'CUSTOMER'),
    ('f2000000-0000-4000-8000-0000000000f2', 'reg-other-staff@example.com', 'Other', 'CUSTOMER'),
    ('f3000000-0000-4000-8000-0000000000f3', 'reg-customer@example.com', 'Customer', 'CUSTOMER'),
    ('f4000000-0000-4000-8000-0000000000f4', 'reg-late@example.com', 'Late', 'CUSTOMER')
on conflict (id) do nothing;

-- A second venue's staff, who must NOT be able to touch the new one.
select public.grant_staff('reg-other-staff@example.com', (select id from public.restaurants order by id limit 1));

-- Uploaded proof files (the app uploads these before calling the RPC).
insert into storage.objects (bucket_id, name, owner)
select 'restaurant-proofs', 'f1000000-0000-4000-8000-0000000000f1/p' || n || '.pdf', 'f1000000-0000-4000-8000-0000000000f1'
from generate_series(1, 12) n;
insert into storage.objects (bucket_id, name, owner)
values ('restaurant-proofs', 'f2000000-0000-4000-8000-0000000000f2/other.pdf', 'f2000000-0000-4000-8000-0000000000f2');

create function pg_temp.docs(n int) returns jsonb language sql as $f$
    select jsonb_build_array(jsonb_build_object('type', 'BUSINESS_LICENSE',
        'path', 'f1000000-0000-4000-8000-0000000000f1/p' || n || '.pdf'))
$f$;
grant execute on function pg_temp.docs(int) to authenticated, anon;

create temp table t_ctx (restaurant_id uuid);
grant all on t_ctx to authenticated, anon;

-- ---- Case 1: registering makes the caller staff of exactly that venue -------
set local role authenticated;
set local request.jwt.claims = '{"sub":"f1000000-0000-4000-8000-0000000000f1","role":"authenticated"}';

do $$
declare
    v_row public.restaurants; v_n int;
begin
    v_row := public.register_restaurant(
        'Warung Uji', 'Jl. Pengujian No. 1, Tangerang', 'Indonesian', 'Tempat uji',
        'https://example.com/foto.jpg', -6.2, 106.6, '00:00', '00:00', 4, 30,
        '+62 812-3456-7890', 'OWNER', pg_temp.docs(1));  -- 24h: join works at any hour
    insert into t_ctx values (v_row.id);

    select count(*) into v_n from public.restaurant_staff
        where user_id = 'f1000000-0000-4000-8000-0000000000f1' and restaurant_id = v_row.id;
    if v_n <> 1 then raise exception 'FAILED case 1: staff link missing'; end if;
    if v_row.is_active or v_row.verification_status <> 'PENDING_VERIFICATION' then
        raise exception 'FAILED case 1: expected an unlisted PENDING_VERIFICATION venue, got % / active %',
            v_row.verification_status, v_row.is_active;
    end if;
    if v_row.contact_phone <> '+6281234567890' then raise exception 'FAILED case 1: phone %', v_row.contact_phone; end if;
    if v_row.queue_prefix <> 'W' then raise exception 'FAILED case 1: prefix %', v_row.queue_prefix; end if;
    raise notice 'PASSED case 1: registration creates a pending, unlisted venue with a staff link and proof';
end;
$$;

reset role;
do $$
declare v_role text;
begin
    select role into v_role from public.users where id = 'f1000000-0000-4000-8000-0000000000f1';
    if v_role <> 'STAFF' then raise exception 'FAILED case 1b: role is %', v_role; end if;
    if not exists (select 1 from public.queue_stats where restaurant_id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 1b: queue_stats row missing';
    end if;
    raise notice 'PASSED case 1b: role promoted, stats row created';
end;
$$;

-- ---- Case 1c: while pending, nothing is manageable and nothing is listed ----
set local role authenticated;
set local request.jwt.claims = '{"sub":"f1000000-0000-4000-8000-0000000000f1","role":"authenticated"}';
do $$
declare v_failed boolean := false; v_n int;
begin
    begin
        perform public.call_next((select restaurant_id from t_ctx));
    exception when others then v_failed := sqlerrm like '%NOT_RESTAURANT_STAFF%';
    end;
    if not v_failed then raise exception 'FAILED case 1c: pending applicant could call the queue'; end if;

    select count(*) into v_n from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_n <> 1 then raise exception 'FAILED case 1c: applicant cannot read own application'; end if;

    v_failed := false;
    begin
        update public.restaurants set is_active = true where id = (select restaurant_id from t_ctx);
        get diagnostics v_n = row_count;
        v_failed := v_n = 0;
    exception when others then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 1c: applicant listed their own unreviewed venue'; end if;

    v_failed := false;
    begin
        update public.restaurants set verification_status = 'APPROVED' where id = (select restaurant_id from t_ctx);
        get diagnostics v_n = row_count;
        v_failed := v_n = 0;
    exception when others then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 1c: applicant approved themself'; end if;

    v_failed := false;
    begin
        perform public.review_restaurant((select restaurant_id from t_ctx), true, null);
    exception when insufficient_privilege then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 1c: app user can call review_restaurant'; end if;

    -- a second application while one is pending
    v_failed := false;
    begin
        perform public.register_restaurant('Dua', 'Jl. Dua No. 2', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30,
            '081234567890', 'OWNER', pg_temp.docs(2));
    exception when others then v_failed := sqlerrm like '%APPLICATION_PENDING%';
    end;
    if not v_failed then raise exception 'FAILED case 1c: second pending application accepted'; end if;
    raise notice 'PASSED case 1c: pending applicant has no staff access and cannot self-approve or self-list';
end;
$$;

set local request.jwt.claims = '{"sub":"f3000000-0000-4000-8000-0000000000f3","role":"authenticated"}';
do $$
begin
    if exists (select 1 from public.restaurants where id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 1d: customers can see a pending venue';
    end if;
    raise notice 'PASSED case 1d: pending venue is invisible to customers';
end;
$$;

-- ---- Case 1e: rejection (note required), resubmission, then approval --------
reset role;
reset request.jwt.claims;
do $$
declare v_failed boolean := false;
begin
    begin
        perform public.review_restaurant((select restaurant_id from t_ctx), false, '  ');
    exception when others then v_failed := sqlerrm like '%REJECTION_NOTE_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 1e: rejection without a note accepted'; end if;
    perform public.review_restaurant((select restaurant_id from t_ctx), false, 'Dokumen tidak terbaca');
    if (select verification_status from public.restaurants where id = (select restaurant_id from t_ctx)) <> 'REJECTED' then
        raise exception 'FAILED case 1e: not rejected';
    end if;
end;
$$;

set local role authenticated;
set local request.jwt.claims = '{"sub":"f1000000-0000-4000-8000-0000000000f1","role":"authenticated"}';
do $$
declare v_row public.restaurants; v_failed boolean := false;
begin
    select * into v_row from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_row.verification_note <> 'Dokumen tidak terbaca' then
        raise exception 'FAILED case 1e: applicant cannot read the rejection reason';
    end if;

    -- cannot resubmit someone else's proof file
    begin
        perform public.resubmit_restaurant(v_row.id, '081234567890', 'OWNER', jsonb_build_array(
            jsonb_build_object('type', 'BUSINESS_LICENSE', 'path', 'f2000000-0000-4000-8000-0000000000f2/other.pdf')));
    exception when others then v_failed := sqlerrm like '%PROOF_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 1e: resubmitted with another user''s file'; end if;

    v_row := public.resubmit_restaurant(v_row.id, '081234567890', 'MANAGER', pg_temp.docs(3));
    if v_row.verification_status <> 'PENDING_VERIFICATION' or v_row.verification_note is not null then
        raise exception 'FAILED case 1e: resubmit did not reopen the application';
    end if;
    raise notice 'PASSED case 1e: rejected -> reason visible -> resubmit -> pending again';
end;
$$;

reset role;
reset request.jwt.claims;
select public.review_restaurant((select restaurant_id from t_ctx), true, null);
do $$
begin
    if not (select is_active from public.restaurants where id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 1f: approval did not list the venue';
    end if;
    raise notice 'PASSED case 1f: approval lists the venue';
end;
$$;

-- ---- Case 2: a customer sees the new venue ----------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub":"f3000000-0000-4000-8000-0000000000f3","role":"authenticated"}';
do $$
begin
    if not exists (select 1 from public.restaurants where id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 2: active venue not visible to customers';
    end if;
    raise notice 'PASSED case 2: active venue is listed to customers';
end;
$$;

-- ---- Case 3: a customer can join it; other venues' staff cannot manage it ---
do $$
declare v_q public.queues;
begin
    v_q := public.join_queue((select restaurant_id from t_ctx), 2, null);
    if v_q.queue_number <> 'W-001' then raise exception 'FAILED case 3: number %', v_q.queue_number; end if;
    raise notice 'PASSED case 3: customer took W-001 at the new venue';
end;
$$;

set local request.jwt.claims = '{"sub":"f2000000-0000-4000-8000-0000000000f2","role":"authenticated"}';
do $$
declare v_failed boolean := false; v_n int;
begin
    begin
        perform public.call_next((select restaurant_id from t_ctx));
    exception when others then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 3b: other venue''s staff called a foreign queue'; end if;

    select count(*) into v_n from public.queues where restaurant_id = (select restaurant_id from t_ctx);
    if v_n <> 0 then raise exception 'FAILED case 3b: other staff can read foreign tickets (%)', v_n; end if;

    update public.restaurants set is_active = false where id = (select restaurant_id from t_ctx);
    get diagnostics v_n = row_count;
    if v_n <> 0 then raise exception 'FAILED case 3b: other staff paused a foreign venue'; end if;
    raise notice 'PASSED case 3b: other staff cannot call, read or pause this venue';
end;
$$;

-- ---- Case 4: owner pauses; customers without a ticket stop seeing it --------
set local request.jwt.claims = '{"sub":"f1000000-0000-4000-8000-0000000000f1","role":"authenticated"}';
do $$
declare v_n int; v_count int;
begin
    update public.restaurants set is_active = false where id = (select restaurant_id from t_ctx);
    get diagnostics v_n = row_count;
    if v_n <> 1 then raise exception 'FAILED case 4: owner could not pause'; end if;
    select count(*) into v_count from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_count <> 1 then raise exception 'FAILED case 4: owner lost sight of own venue'; end if;
    raise notice 'PASSED case 4: owner can pause and still sees the venue';
end;
$$;

set local request.jwt.claims = '{"sub":"f4000000-0000-4000-8000-0000000000f4","role":"authenticated"}';
do $$
declare v_n int; v_failed boolean := false;
begin
    select count(*) into v_n from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_n <> 0 then raise exception 'FAILED case 5: paused venue still listed'; end if;
    begin
        perform public.join_queue((select restaurant_id from t_ctx), 1, null);
    exception when others then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 5: joined a paused venue'; end if;
    raise notice 'PASSED case 5: paused venue is hidden and takes no tickets';
end;
$$;

-- The customer who already holds a ticket keeps seeing it (history stays whole).
set local request.jwt.claims = '{"sub":"f3000000-0000-4000-8000-0000000000f3","role":"authenticated"}';
do $$
begin
    if not exists (select 1 from public.restaurants where id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 5b: ticket holder lost the venue';
    end if;
    raise notice 'PASSED case 5b: ticket holders still see a paused venue';
end;
$$;

-- ---- Case 6: validation and abuse limits ------------------------------------
set local request.jwt.claims = '{"sub":"f1000000-0000-4000-8000-0000000000f1","role":"authenticated"}';
do $$
declare v_failed boolean; i int;
begin
    v_failed := false;
    begin
        perform public.register_restaurant('X', 'Jl. A', '', '', null, 95, 0, '09:00', '21:00', 4, 30, '081234567890', 'OWNER', pg_temp.docs(4));
    exception when others then v_failed := sqlerrm like '%INVALID_RESTAURANT%';
    end;
    if not v_failed then raise exception 'FAILED case 6: invalid input accepted'; end if;

    v_failed := false;
    begin
        perform public.register_restaurant('Foto Http', 'Jl. Pengujian 2', 'Cafe', '', 'http://x/y.jpg', 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER', pg_temp.docs(4));
    exception when others then v_failed := sqlerrm like '%INVALID_RESTAURANT%';
    end;
    if not v_failed then raise exception 'FAILED case 6: non-https photo accepted'; end if;

    -- bad phone, no proof, license missing, foreign folder
    v_failed := false;
    begin
        perform public.register_restaurant('Telp', 'Jl. Telepon 1', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '12ab', 'OWNER', pg_temp.docs(4));
    exception when others then v_failed := sqlerrm like '%INVALID_RESTAURANT%';
    end;
    if not v_failed then raise exception 'FAILED case 6: bad phone accepted'; end if;

    v_failed := false;
    begin
        perform public.register_restaurant('NoDoc', 'Jl. Tanpa Bukti', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER', '[]'::jsonb);
    exception when others then v_failed := sqlerrm like '%PROOF_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 6: registration without proof accepted'; end if;

    v_failed := false;
    begin
        perform public.register_restaurant('Ghost', 'Jl. Hantu Raya', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER',
            jsonb_build_array(jsonb_build_object('type', 'BUSINESS_LICENSE', 'path', 'f1000000-0000-4000-8000-0000000000f1/never-uploaded.pdf')));
    exception when others then v_failed := sqlerrm like '%PROOF_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 6: a file that was never uploaded was accepted'; end if;

    v_failed := false;
    begin
        perform public.register_restaurant('Foreign', 'Jl. Orang Lain', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER',
            jsonb_build_array(jsonb_build_object('type', 'BUSINESS_LICENSE', 'path', 'f2000000-0000-4000-8000-0000000000f2/other.pdf')));
    exception when others then v_failed := sqlerrm like '%PROOF_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 6: another user''s file accepted as proof'; end if;

    v_failed := false;
    begin
        perform public.register_restaurant('OnlyId', 'Jl. Hanya Kuasa', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER',
            jsonb_build_array(jsonb_build_object('type', 'OWNERSHIP_PROOF', 'path', 'f1000000-0000-4000-8000-0000000000f1/p4.pdf')));
    exception when others then v_failed := sqlerrm like '%PROOF_REQUIRED%';
    end;
    if not v_failed then raise exception 'FAILED case 6: registration without a business document accepted'; end if;

    for i in 2..5 loop
        perform public.register_restaurant('Cabang ' || i, 'Jl. Cabang ' || i, 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30,
            '081234567890', 'OWNER', pg_temp.docs(4 + i));
        -- A reviewer clears each one so the next application is allowed.
        perform set_config('role', 'postgres', true);
        perform public.review_restaurant(
            (select id from public.restaurants where name = 'Cabang ' || i), true, null);
        perform set_config('role', 'authenticated', true);
    end loop;
    v_failed := false;
    begin
        perform public.register_restaurant('Cabang 6', 'Jl. Cabang 6', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER', pg_temp.docs(11));
    exception when others then v_failed := sqlerrm like '%TOO_MANY_RESTAURANTS%';
    end;
    if not v_failed then raise exception 'FAILED case 6: registration cap not enforced'; end if;
    raise notice 'PASSED case 6: bad input and the 5-venue cap are rejected';
end;
$$;

-- ---- Case 7: anonymous callers cannot register ------------------------------
reset request.jwt.claims;
set local role anon;
do $$
declare v_failed boolean := false;
begin
    begin
        perform public.register_restaurant('Anon', 'Jl. Anon 1', 'Cafe', '', null, 1, 1, '09:00', '21:00', 4, 30, '081234567890', 'OWNER', '[]'::jsonb);
    exception when insufficient_privilege then v_failed := true;
    end;
    if not v_failed then raise exception 'FAILED case 7: anon could register'; end if;
    raise notice 'PASSED case 7: anonymous users cannot register';
end;
$$;

reset role;
rollback;
