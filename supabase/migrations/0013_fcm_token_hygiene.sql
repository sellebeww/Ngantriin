-- =============================================================================
-- NGANTRIIN: 0013: satu token perangkat milik satu akun.
--
-- Sebelumnya users.fcm_token hanya kolom biasa. Saat pelanggan A keluar dan
-- pelanggan B masuk di HP yang sama, baris B menerima token HP itu tapi
-- baris A tetap memilikinya juga, jadi push antrean A terus mendarat di HP
-- yang sekarang dipegang B. Dua pengaman:
-- * trigger BEFORE yang mengambil token dari akun lain
-- begitu seseorang mengklaimnya;
-- * partial unique index supaya tabel tidak pernah bisa menyimpan duplikat lagi.
-- Aplikasi juga mengosongkan token saat logout (AuthRepositoryImpl).
-- =============================================================================

-- Duplikat yang sudah ada akan membuat index di bawah gagal: sisakan satu pemilik.
update public.users u
set fcm_token = null
where u.fcm_token is not null
  and exists (
      select 1 from public.users o
      where o.fcm_token = u.fcm_token and o.ctid < u.ctid
  );

create or replace function public.users_claim_fcm_token()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    if new.fcm_token is not null
       and (tg_op = 'INSERT' or new.fcm_token is distinct from old.fcm_token) then
        update public.users
        set fcm_token = null
        where fcm_token = new.fcm_token and id <> new.id;
    end if;
    return new;
end;
$$;

revoke execute on function public.users_claim_fcm_token() from public, anon, authenticated;

drop trigger if exists users_claim_fcm_token on public.users;
create trigger users_claim_fcm_token
    before insert or update of fcm_token on public.users
    for each row execute function public.users_claim_fcm_token();

create unique index if not exists users_fcm_token_key
    on public.users (fcm_token) where fcm_token is not null;
