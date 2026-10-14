-- Stand-ins for the parts of a hosted Supabase project that the stock
-- supabase/postgres image does not create (those come from separate services:
-- storage-api creates the storage tables, GoTrue ships a newer auth.uid()).
-- Used only by scripts/sql-tests.sh and CI, never against a real project.
create table if not exists storage.buckets (
    id text primary key, name text, public bool default false,
    file_size_limit bigint, allowed_mime_types text[]
);
create table if not exists storage.objects (
    id uuid default gen_random_uuid() primary key,
    bucket_id text, name text, owner uuid, created_at timestamptz default now()
);
alter table storage.objects enable row level security;
create or replace function storage.foldername(name text)
returns text[] language sql as $$ select string_to_array(name, '/') $$;
grant usage on schema storage to authenticated, anon;
grant select, insert, update, delete on storage.objects to authenticated;

create or replace function auth.uid() returns uuid language sql stable as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
    )::uuid
$$;

create extension if not exists pg_cron;
