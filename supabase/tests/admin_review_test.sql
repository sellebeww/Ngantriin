-- =============================================================================
-- NGANTRIIN — admin review of restaurant applications (0020 + 0021).
-- One transaction, ends in rollback.
--   psql "$DATABASE_URL" -f supabase/tests/admin_review_test.sql
--
-- Every permission here is checked against the database as the role a real
-- client would have, never through the app.
--   a0 evil     signs up claiming role=ADMIN in the sign-up metadata
--   a1 admin    made admin with grant_admin()
--   a2 owner    applies for a restaurant
--   a3 staff    runs a different, approved venue
--   a4 customer
-- =============================================================================
begin;

-- ---- Case 1: sign-up metadata cannot create an admin --------------------------
insert into auth.users (id, email, raw_user_meta_data) values
    ('a0000000-0000-4000-8000-0000000000a0', 'rev-evil@example.com', '{"role":"ADMIN","name":"Evil"}'::jsonb)
on conflict (id) do nothing;

do $$
begin
    if (select role::text from public.users where id = 'a0000000-0000-4000-8000-0000000000a0')
        is distinct from 'CUSTOMER' then
        raise exception 'FAILED case 1: sign-up metadata produced role %',
            (select role from public.users where id = 'a0000000-0000-4000-8000-0000000000a0');
    end if;
    raise notice 'PASSED case 1: sign-up metadata cannot make an admin';
end;
$$;

insert into auth.users (id, email) values
    ('a1000000-0000-4000-8000-0000000000a1', 'rev-admin@example.com'),
    ('a2000000-0000-4000-8000-0000000000a2', 'rev-owner@example.com'),
    ('a3000000-0000-4000-8000-0000000000a3', 'rev-staff@example.com'),
    ('a4000000-0000-4000-8000-0000000000a4', 'rev-customer@example.com')
on conflict (id) do nothing;

insert into public.users (id, email, name, role) values
    ('a1000000-0000-4000-8000-0000000000a1', 'rev-admin@example.com', 'Admin', 'CUSTOMER'),
    ('a2000000-0000-4000-8000-0000000000a2', 'rev-owner@example.com', 'Owner', 'CUSTOMER'),
    ('a3000000-0000-4000-8000-0000000000a3', 'rev-staff@example.com', 'Staff', 'CUSTOMER'),
    ('a4000000-0000-4000-8000-0000000000a4', 'rev-customer@example.com', 'Customer', 'CUSTOMER')
on conflict (id) do nothing;

select public.grant_admin('rev-admin@example.com');
select public.grant_staff('rev-staff@example.com', (select id from public.restaurants order by id limit 1));

-- Files the app would have uploaded before calling the RPC.
insert into storage.objects (bucket_id, name, owner) values
    ('restaurant-proofs', 'a2000000-0000-4000-8000-0000000000a2/lic1.pdf', 'a2000000-0000-4000-8000-0000000000a2'),
    ('restaurant-proofs', 'a2000000-0000-4000-8000-0000000000a2/lic2.pdf', 'a2000000-0000-4000-8000-0000000000a2'),
    ('restaurant-proofs', 'a2000000-0000-4000-8000-0000000000a2/id2.jpg',  'a2000000-0000-4000-8000-0000000000a2'),
    ('restaurant-proofs', 'a3000000-0000-4000-8000-0000000000a3/other.pdf', 'a3000000-0000-4000-8000-0000000000a3'),
    ('restaurant-proofs', 'a1000000-0000-4000-8000-0000000000a1/adm.pdf',  'a1000000-0000-4000-8000-0000000000a1');

create function pg_temp.docs(p_uid text, p_file text) returns jsonb language sql as $f$
    select jsonb_build_array(jsonb_build_object('type', 'BUSINESS_LICENSE', 'path', p_uid || '/' || p_file))
$f$;
grant execute on function pg_temp.docs(text, text) to authenticated, anon;

-- True when the statement is refused with insufficient_privilege (42501).
create function pg_temp.denied(p_sql text) returns boolean language plpgsql as $f$
begin
    execute p_sql;
    return false;
exception when insufficient_privilege then
    return true;
end;
$f$;
grant execute on function pg_temp.denied(text) to authenticated, anon;

-- True when the statement fails and the error message contains p_msg.
create function pg_temp.fails_with(p_sql text, p_msg text) returns boolean language plpgsql as $f$
begin
    execute p_sql;
    return false;
exception when others then
    return sqlerrm like '%' || p_msg || '%';
end;
$f$;
grant execute on function pg_temp.fails_with(text, text) to authenticated, anon;

create temp table t_ctx (restaurant_id uuid);
grant all on t_ctx to authenticated, anon;

-- ---- Case 2: an application is pending and unlisted --------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
declare v_row public.restaurants;
begin
    v_row := public.register_restaurant(
        'Warung Review', 'Jl. Pengujian No. 9, Tangerang', 'Indonesian', 'Tempat uji',
        null, -6.2, 106.6, '09:00', '21:00', 4, 30,
        '081234567890', 'OWNER', pg_temp.docs('a2000000-0000-4000-8000-0000000000a2', 'lic1.pdf'));
    insert into t_ctx values (v_row.id);
    if v_row.verification_status <> 'PENDING_VERIFICATION' or v_row.is_active then
        raise exception 'FAILED case 2: expected pending and unlisted, got % / %', v_row.verification_status, v_row.is_active;
    end if;
    if public.is_restaurant_staff(v_row.id) then
        raise exception 'FAILED case 2: pending applicant counts as staff';
    end if;
    raise notice 'PASSED case 2: application is PENDING_VERIFICATION, unlisted, no staff rights yet';
end;
$$;

set local request.jwt.claims = '{"sub":"a4000000-0000-4000-8000-0000000000a4","role":"authenticated"}';
do $$
begin
    if exists (select 1 from public.restaurants where id = (select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 2b: a customer can see a pending venue';
    end if;
    raise notice 'PASSED case 2b: pending venue is invisible to customers';
end;
$$;

-- ---- Case 3: nobody but an admin gets through the admin API -------------------
do $$
declare
    v_id  uuid := (select restaurant_id from t_ctx);
    v_who text;
begin
    -- owner, staff of another venue, customer
    foreach v_who in array array[
        'a2000000-0000-4000-8000-0000000000a2',
        'a3000000-0000-4000-8000-0000000000a3',
        'a4000000-0000-4000-8000-0000000000a4']
    loop
        perform set_config('request.jwt.claims',
            json_build_object('sub', v_who, 'role', 'authenticated')::text, true);

        if not pg_temp.denied('select * from public.admin_list_applications()') then
            raise exception 'FAILED case 3: % listed applications', v_who;
        end if;
        if not pg_temp.denied(format('select * from public.admin_application_documents(%L)', v_id)) then
            raise exception 'FAILED case 3: % read application documents', v_who;
        end if;
        if not pg_temp.denied(format('select public.admin_review_restaurant(%L, true, null)', v_id)) then
            raise exception 'FAILED case 3: % approved an application', v_who;
        end if;
        if public.is_admin() then
            raise exception 'FAILED case 3: is_admin() is true for %', v_who;
        end if;
    end loop;
    raise notice 'PASSED case 3: owner, other staff and customer are refused by every admin function';
end;
$$;

-- Not even a session without a login.
reset request.jwt.claims;
set local role anon;
do $$
declare v_id uuid := (select restaurant_id from t_ctx);
begin
    if not pg_temp.denied('select * from public.admin_list_applications()')
       or not pg_temp.denied(format('select public.admin_review_restaurant(%L, true, null)', v_id))
       or not pg_temp.denied('select public.is_admin()') then
        raise exception 'FAILED case 3b: anon reached the admin API';
    end if;
    raise notice 'PASSED case 3b: anonymous callers cannot use the admin API';
end;
$$;

-- ---- Case 4: nobody can make themselves (or anyone) an admin -------------------
set local role authenticated;
set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
begin
    if not pg_temp.denied($q$update public.users set role = 'ADMIN' where id = 'a2000000-0000-4000-8000-0000000000a2'$q$) then
        raise exception 'FAILED case 4: a user rewrote their own role';
    end if;
    if not pg_temp.denied($q$select public.grant_admin('rev-owner@example.com')$q$) then
        raise exception 'FAILED case 4: an app user called grant_admin';
    end if;
    if not pg_temp.denied($q$select public.revoke_admin('rev-admin@example.com')$q$) then
        raise exception 'FAILED case 4: an app user called revoke_admin';
    end if;
    raise notice 'PASSED case 4: role is not self-assignable and grant/revoke_admin are closed to app users';
end;
$$;

reset role;
do $$
begin
    if (select role::text from public.users where id = 'a2000000-0000-4000-8000-0000000000a2') <> 'STAFF' then
        raise exception 'FAILED case 4b: owner role is %, expected STAFF from the application',
            (select role from public.users where id = 'a2000000-0000-4000-8000-0000000000a2');
    end if;
    raise notice 'PASSED case 4b: role unchanged by the attempts above';
end;
$$;

-- ---- Case 5: the admin sees the application and its documents -----------------
set local role authenticated;
set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
declare
    v_id  uuid := (select restaurant_id from t_ctx);
    v_app record;
    v_n   int;
begin
    select * into v_app from public.admin_list_applications() where restaurant_id = v_id;
    if not found then raise exception 'FAILED case 5: pending application not listed'; end if;
    if v_app.applicant_email <> 'rev-owner@example.com' or v_app.contact_phone <> '081234567890'
       or v_app.applicant_role <> 'OWNER' or v_app.document_count <> 1 then
        raise exception 'FAILED case 5: unexpected application row %', to_jsonb(v_app);
    end if;

    select count(*) into v_n from public.admin_application_documents(v_id);
    if v_n <> 1 then raise exception 'FAILED case 5: expected 1 document, got %', v_n; end if;

    if exists (select 1 from public.admin_list_applications('APPROVED') where restaurant_id = v_id) then
        raise exception 'FAILED case 5: pending application shows under APPROVED';
    end if;

    if not pg_temp.fails_with($q$select * from public.admin_list_applications('BOGUS')$q$, 'INVALID_STATUS') then
        raise exception 'FAILED case 5: unknown status accepted';
    end if;
    raise notice 'PASSED case 5: admin lists the pending application with applicant and documents';
end;
$$;

-- ---- Case 6: proof files are private; only the owner and an admin can read -----
do $$
declare v_n int;
begin
    select count(*) into v_n from storage.objects
    where bucket_id = 'restaurant-proofs' and name like 'a2000000-%';
    if v_n <> 3 then raise exception 'FAILED case 6: admin sees % of the owner''s 3 files', v_n; end if;
    select count(*) into v_n from storage.objects
    where bucket_id = 'restaurant-proofs' and name like 'a3000000-%';
    if v_n <> 1 then raise exception 'FAILED case 6: admin cannot read other applicants'' files'; end if;
    raise notice 'PASSED case 6: admin can read proof files';
end;
$$;

set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
declare v_n int;
begin
    select count(*) into v_n from storage.objects where bucket_id = 'restaurant-proofs';
    if v_n <> 3 then raise exception 'FAILED case 6b: owner sees % files, expected only their own 3', v_n; end if;
    raise notice 'PASSED case 6b: an applicant reads only their own proof files';
end;
$$;

set local request.jwt.claims = '{"sub":"a3000000-0000-4000-8000-0000000000a3","role":"authenticated"}';
do $$
declare v_n int;
begin
    -- a3 is staff of an approved venue: still nobody else's files.
    select count(*) into v_n from storage.objects
    where bucket_id = 'restaurant-proofs' and name like 'a2000000-%';
    if v_n <> 0 then raise exception 'FAILED case 6c: other staff read the applicant''s files'; end if;
    raise notice 'PASSED case 6c: other staff cannot read proof files';
end;
$$;

set local request.jwt.claims = '{"sub":"a4000000-0000-4000-8000-0000000000a4","role":"authenticated"}';
do $$
declare v_n int;
begin
    select count(*) into v_n from storage.objects where bucket_id = 'restaurant-proofs';
    if v_n <> 0 then raise exception 'FAILED case 6d: a customer sees % proof files', v_n; end if;
    raise notice 'PASSED case 6d: customers cannot read proof files';
end;
$$;

-- ---- Case 6e: upload rules ------------------------------------------------------
set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
begin
    if not pg_temp.denied($q$insert into storage.objects (bucket_id, name, owner)
        values ('restaurant-proofs', 'a2000000-0000-4000-8000-0000000000a2/payload.exe', 'a2000000-0000-4000-8000-0000000000a2')$q$) then
        raise exception 'FAILED case 6e: an .exe was accepted as proof';
    end if;
    if not pg_temp.denied($q$insert into storage.objects (bucket_id, name, owner)
        values ('restaurant-proofs', 'a3000000-0000-4000-8000-0000000000a3/mine.pdf', 'a2000000-0000-4000-8000-0000000000a2')$q$) then
        raise exception 'FAILED case 6e: upload into another user''s folder accepted';
    end if;
    if not pg_temp.denied($q$insert into storage.objects (bucket_id, name, owner)
        values ('restaurant-photos', 'a2000000-0000-4000-8000-0000000000a2/shell.php', 'a2000000-0000-4000-8000-0000000000a2')$q$) then
        raise exception 'FAILED case 6e: a non-image was accepted as a restaurant photo';
    end if;
    insert into storage.objects (bucket_id, name, owner)
    values ('restaurant-proofs', 'a2000000-0000-4000-8000-0000000000a2/ok.pdf', 'a2000000-0000-4000-8000-0000000000a2');
    insert into storage.objects (bucket_id, name, owner)
    values ('restaurant-photos', 'a2000000-0000-4000-8000-0000000000a2/ok.jpg', 'a2000000-0000-4000-8000-0000000000a2');
    raise notice 'PASSED case 6e: only .jpg/.pdf proofs and .jpg photos, only in the uploader''s own folder';
end;
$$;

-- ---- Case 7: reject needs a reason, the owner sees it, resubmit, approve --------
set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
declare v_id uuid := (select restaurant_id from t_ctx);
begin
    if not pg_temp.fails_with(format('select public.admin_review_restaurant(%L, false, %L)', v_id, '  '),
                              'REJECTION_NOTE_REQUIRED') then
        raise exception 'FAILED case 7: rejection without a reason accepted';
    end if;
    perform public.admin_review_restaurant(v_id, false, 'Dokumen tidak terbaca');
    raise notice 'PASSED case 7: rejection requires a reason';
end;
$$;

reset role;
do $$
declare v_r public.restaurants;
begin
    select * into v_r from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_r.verification_status <> 'REJECTED' or v_r.is_active
       or v_r.verified_by is distinct from 'a1000000-0000-4000-8000-0000000000a1'::uuid
       or v_r.verified_at is null then
        raise exception 'FAILED case 7b: unexpected state after rejection %', to_jsonb(v_r);
    end if;
    raise notice 'PASSED case 7b: rejected, still unlisted, reviewer recorded';
end;
$$;

set local role authenticated;
set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
declare v_id uuid := (select restaurant_id from t_ctx);
begin
    if not pg_temp.fails_with(format('select public.admin_review_restaurant(%L, true, null)', v_id), 'NOT_PENDING') then
        raise exception 'FAILED case 7c: a rejected application was approved without a resubmission';
    end if;
    raise notice 'PASSED case 7c: a rejected application cannot be approved until the owner resubmits';
end;
$$;

set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
declare v_r public.restaurants; v_id uuid := (select restaurant_id from t_ctx);
begin
    select * into v_r from public.restaurants where id = v_id;
    if v_r.verification_note <> 'Dokumen tidak terbaca' then
        raise exception 'FAILED case 7d: owner cannot read the rejection reason';
    end if;

    if not pg_temp.denied(format('select public.admin_review_restaurant(%L, true, null)', v_id)) then
        raise exception 'FAILED case 7d: owner approved their own application';
    end if;

    v_r := public.resubmit_restaurant(v_id, '081234567890', 'MANAGER', jsonb_build_array(
        jsonb_build_object('type', 'BUSINESS_LICENSE', 'path', 'a2000000-0000-4000-8000-0000000000a2/lic2.pdf'),
        jsonb_build_object('type', 'OWNERSHIP_PROOF', 'path', 'a2000000-0000-4000-8000-0000000000a2/id2.jpg')));
    if v_r.verification_status <> 'PENDING_VERIFICATION' or v_r.verification_note is not null then
        raise exception 'FAILED case 7d: resubmit did not reopen the application';
    end if;
    raise notice 'PASSED case 7d: owner reads the reason and resubmits';
end;
$$;

set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
declare
    v_id  uuid := (select restaurant_id from t_ctx);
    v_app record;
    v_first record;
    v_n int;
begin
    select * into v_app from public.admin_list_applications() where restaurant_id = v_id;
    if not found then raise exception 'FAILED case 7e: resubmitted application is not in the pending list'; end if;
    -- Only the newest submission counts: 2 files in round 2, 1 file in round 1.
    if v_app.document_count <> 2 then
        raise exception 'FAILED case 7e: document_count % counts old rounds', v_app.document_count;
    end if;

    select count(*) into v_n from public.admin_application_documents(v_id);
    select * into v_first from public.admin_application_documents(v_id) limit 1;
    if v_n <> 3 or v_first.submission_round <> 2 then
        raise exception 'FAILED case 7e: documents % / first round %', v_n, v_first.submission_round;
    end if;
    raise notice 'PASSED case 7e: admin sees the newest submission first, older rounds kept';
end;
$$;

-- ---- Case 8: approval lists the venue for customers -----------------------------
do $$
begin
    perform public.admin_review_restaurant((select restaurant_id from t_ctx), true, null);
    raise notice 'PASSED case 8: admin approved the resubmitted application';
end;
$$;

reset role;
do $$
declare v_r public.restaurants;
begin
    select * into v_r from public.restaurants where id = (select restaurant_id from t_ctx);
    if v_r.verification_status <> 'APPROVED' or not v_r.is_active
       or v_r.verified_by is distinct from 'a1000000-0000-4000-8000-0000000000a1'::uuid then
        raise exception 'FAILED case 8b: unexpected state after approval %', to_jsonb(v_r);
    end if;
    raise notice 'PASSED case 8b: approved, active, reviewer recorded';
end;
$$;

set local role authenticated;
set local request.jwt.claims = '{"sub":"a4000000-0000-4000-8000-0000000000a4","role":"authenticated"}';
do $$
declare v_id uuid := (select restaurant_id from t_ctx);
begin
    if not exists (select 1 from public.restaurants where id = v_id and is_active) then
        raise exception 'FAILED case 8c: approved venue is not visible to customers';
    end if;
    raise notice 'PASSED case 8c: customers see the venue once it is approved and active';
end;
$$;

set local request.jwt.claims = '{"sub":"a2000000-0000-4000-8000-0000000000a2","role":"authenticated"}';
do $$
begin
    if not public.is_restaurant_staff((select restaurant_id from t_ctx)) then
        raise exception 'FAILED case 8d: approved owner is not staff of their venue';
    end if;
    raise notice 'PASSED case 8d: staff rights start at approval';
end;
$$;

set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
declare v_id uuid := (select restaurant_id from t_ctx);
begin
    if not pg_temp.fails_with(format('select public.admin_review_restaurant(%L, false, %L)', v_id, 'terlambat'), 'NOT_PENDING') then
        raise exception 'FAILED case 8e: an approved venue was rejected after the fact';
    end if;
    if not pg_temp.fails_with(format('select public.admin_review_restaurant(%L, true, null)', 'ffffffff-ffff-4fff-8fff-ffffffffffff'),
                              'NO_SUCH_RESTAURANT') then
        raise exception 'FAILED case 8e: unknown restaurant not reported';
    end if;
    raise notice 'PASSED case 8e: decisions are final and unknown ids are reported';
end;
$$;

-- ---- Case 9: an admin is not a restaurant owner ---------------------------------
do $$
begin
    if not pg_temp.denied($q$select public.register_restaurant('Admin Resto', 'Jl. Admin No. 1', 'Cafe', '', null, 1, 1,
            '09:00', '21:00', 4, 30, '081234567890', 'OWNER', pg_temp.docs('a1000000-0000-4000-8000-0000000000a1', 'adm.pdf'))$q$) then
        raise exception 'FAILED case 9: an admin registered a restaurant';
    end if;
    raise notice 'PASSED case 9: an admin cannot register a restaurant';
end;
$$;

-- ---- Case 10: staff helpers never overwrite the ADMIN role ----------------------
reset role;
reset request.jwt.claims;
do $$
declare v_seed uuid := (select id from public.restaurants order by id limit 1);
begin
    if not pg_temp.fails_with(format('select public.grant_staff(%L, %L)', 'rev-admin@example.com', v_seed), 'USER_IS_ADMIN') then
        raise exception 'FAILED case 10: grant_staff accepted an admin';
    end if;
    perform public.revoke_staff('rev-admin@example.com', v_seed);
    if (select role::text from public.users where id = 'a1000000-0000-4000-8000-0000000000a1') <> 'ADMIN' then
        raise exception 'FAILED case 10: revoke_staff demoted an admin';
    end if;
    if not pg_temp.fails_with($q$select public.grant_admin('rev-staff@example.com')$q$, 'USER_IS_STAFF') then
        raise exception 'FAILED case 10: grant_admin accepted a restaurant staff member';
    end if;
    raise notice 'PASSED case 10: ADMIN survives the staff helpers; staff cannot be made admin';
end;
$$;

-- ---- Case 11: the role is checked on every call, not remembered ------------------
select public.revoke_admin('rev-admin@example.com');
set local role authenticated;
set local request.jwt.claims = '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}';
do $$
begin
    if not pg_temp.denied('select * from public.admin_list_applications()') then
        raise exception 'FAILED case 11: a revoked admin still lists applications';
    end if;
    raise notice 'PASSED case 11: revoking the role takes effect immediately';
end;
$$;

reset role;
rollback;
