-- =============================================================================
-- NGANTRIIN — new queue states: TABLE_READY and EXPIRED.
--
-- Kept in a file of its own on purpose: Postgres refuses to *use* an enum
-- value in the same transaction that added it, and every migration file runs
-- in one transaction. 0012 is where these values are actually used.
--
-- The journey this enables:
--
--   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
--
--   * CALLED       — it's your turn; you have the restaurant's check-in window
--                    (10 minutes by default) to arrive and check in.
--   * CHECKED_IN   — you're physically at the restaurant (photo verified).
--                    May happen early, straight from WAITING / ALMOST_THERE.
--   * TABLE_READY  — staff have prepared your table; head to the host.
--   * EXPIRED      — called, but never checked in within the window. Terminal.
-- =============================================================================

alter type public.queue_status add value if not exists 'TABLE_READY' after 'CHECKED_IN';
alter type public.queue_status add value if not exists 'EXPIRED' after 'CANCELLED';

alter type public.notification_type add value if not exists 'TABLE_READY' after 'CHECKED_IN';
alter type public.notification_type add value if not exists 'EXPIRED' after 'CANCELLED';
