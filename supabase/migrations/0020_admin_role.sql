-- =============================================================================
-- NGANTRIIN — ADMIN role (enum value only)
--
-- Kept in its own file on purpose: Postgres will not let a new enum value be
-- used in the transaction that adds it, and 0021 uses it straight away.
-- Nobody becomes ADMIN from the app; see grant_admin() in 0021.
-- =============================================================================

alter type public.user_role add value if not exists 'ADMIN';
