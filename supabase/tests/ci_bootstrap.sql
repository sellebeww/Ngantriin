-- Pengganti bagian proyek Supabase hosted yang tidak dibuat oleh image
-- supabase/postgres bawaan (bagian itu datang dari service terpisah:
-- storage-api membuat tabel storage, GoTrue membawa auth.uid() yang lebih baru).
-- Hanya dipakai scripts/sql-tests.sh dan CI, tidak pernah ke proyek asli.
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
