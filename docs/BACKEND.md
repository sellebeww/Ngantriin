# Backend contract

What the Android client assumes about Supabase. Every assumption here is
enforced by the SQL in `supabase/migrations/`, not by the client.

---

## The customer journey

```
WAITING ─► ALMOST_THERE ─► CALLED ─► CHECKED_IN ─► TABLE_READY ─► COMPLETED
   │             │            └── 10 min, no check-in ──► EXPIRED
   └─────────────┴── already at the restaurant ──► CHECKED_IN (early)

any live status ──► CANCELLED
```

| Status | Meaning | Who moves it here |
| --- | --- | --- |
| `WAITING` | In line, free to be elsewhere | `join_queue` |
| `ALMOST_THERE` | ≤ 3 groups ahead — head back | `promote_almost_there` (Edge Function) |
| `CALLED` | Your turn; `check_in_deadline` is running | `call_next`, staff |
| `CHECKED_IN` | At the restaurant, photo verified; table being prepared | `check_in_queue`, staff ("arrived") |
| `TABLE_READY` | Table set — go to the host | staff, or `call_next` for an early arrival |
| `COMPLETED` | Seated / served. Unlocks a review | staff |
| `CANCELLED` | Customer left, or staff removed / no-show | `cancel_queue`, staff |
| `EXPIRED` | Called, never checked in within the window | `expire_overdue_queues` |

The legal moves are `is_valid_queue_transition` in
`0012_check_in_window_and_table_ready.sql`, mirrored by
`QueueStatus.canTransitionTo` and pinned by `QueueStatusTest`. The
`queues_transition_guard` trigger rejects anything else.

### The check-in window (10 minutes)

When a ticket is `CALLED` it gets `check_in_deadline =
now() + restaurants.checkin_window_minutes` (default 10). Past it, the ticket
becomes `EXPIRED`. Expiry is enforced in three independent places, so it holds
even when one is missing:

1. **pg_cron** runs `expire_overdue_queues()` every minute (scheduled by 0012
   when the extension is available).
2. **Every RPC that reads or moves a line sweeps it first**: `join_queue`,
   `call_next`, `queue_position`.
3. **`check_in_queue` refuses** a check-in past the deadline
   (`CHECK_IN_EXPIRED`).

The app also calls `expire_overdue_queues(restaurant)` the moment an on-screen
countdown reaches zero, and the staff board sweeps every 30 seconds, so the
UI never waits on the cron tick.

### Early check-in

A customer who is already at the restaurant can check in from `WAITING` or
`ALMOST_THERE`. They keep their place; when their number comes up,
`call_next` moves them straight to `TABLE_READY` because there is nobody to
wait for.

---

## Tables

| Table | Purpose |
| --- | --- |
| `users` | App profile mirroring `auth.users`; holds `role`, `fcm_token`, `locale`. `fcm_token` is unique (0013): registering a token on one account clears it from any other |
| `restaurants` | Venue, coordinates, opening hours (`timezone`), queue configuration |
| `restaurant_staff` | Which accounts may manage which venues |
| `queues` | One row per customer group per visit, with a timestamp per stage |
| `queue_stats` | Denormalised counters, maintained by trigger |
| `reviews` | One per completed ticket |
| `notifications` | Every push, persisted so it can be tracked and de-duplicated; cleaned up daily by pg_cron (or `cleanup_old_notifications()` by hand) |
| `storage.objects` (`checkin-photos` bucket) | Check-in photos, path `{restaurant_id}/{queue_id}/{timestamp}.jpg`, private — readable only by the ticket's own customer and that restaurant's staff |

### Queue configuration lives on the restaurant

| Column | Drives |
| --- | --- |
| `average_service_minutes` | Every wait estimate (people ahead × this) |
| `queue_capacity` | `QUEUE_FULL` |
| `checkin_window_minutes` | The check-in countdown (1–60, default 10) |
| `opening_time`, `closing_time`, `timezone`, `is_open` | `RESTAURANT_CLOSED` — checked on the server since 0012 |
| `available_seats` | `SEATS_AVAILABLE` — walk in, don't queue |

Changing a venue's pace is a data change, not a release.

---

## Functions the client calls

All of them are `SECURITY DEFINER`, executable by `authenticated` only, and
validate their own preconditions. Nobody — staff included — can INSERT,
UPDATE or DELETE `queues` directly.

| Function | Raises | Becomes |
| --- | --- | --- |
| `join_queue(restaurant, party_size, note)` | `RESTAURANT_CLOSED` | "This restaurant is closed right now." |
| | `SEATS_AVAILABLE` | "…no need to queue, just walk in." |
| | `QUEUE_FULL` | "This queue is full. Try again a little later." |
| | `ALREADY_IN_QUEUE` | "You already have an active queue." |
| | `JOIN_COOLDOWN` | "Please wait a moment before rejoining this queue." |
| | `INVALID_PARTY_SIZE` | "Party size must be between 1 and 20 people." |
| `cancel_queue(queue)` | `QUEUE_NOT_CANCELLABLE` | "This queue can no longer be cancelled." |
| `check_in_queue(queue, photo_path)` | `CHECK_IN_EXPIRED` | "Your check-in window has closed…" |
| | `CHECK_IN_NOT_ALLOWED` | "This ticket can't be checked in anymore." |
| | `PHOTO_REQUIRED` | "Please take a photo to check in." |
| | `PHOTO_NOT_UPLOADED` | "Your photo didn't upload. Please try again." |
| `call_next(restaurant)` | `QUEUE_EMPTY` | "Nobody is waiting right now." |
| | `NOT_RESTAURANT_STAFF` | "You don't manage this restaurant." |
| `staff_update_queue_status(queue, status)` | `ILLEGAL_TRANSITION` | generic server error |
| `queue_position(queue)` | `QUEUE_NOT_FOUND` (not yours) | people ahead, position, estimate, now serving |
| `expire_overdue_queues(restaurant?)` | — | number of tickets expired |

These tokens are part of the contract. `ErrorMapper` matches on them and
`ErrorMapperTest` fails if either side is renamed alone.

Internal helpers — `refresh_queue_stats`, `promote_almost_there`,
`cleanup_old_notifications`, the trigger functions — are not executable by
clients.

---

## Invariants the database guarantees

**One active queue per customer.** A partial unique index on `queues (user_id)`
where the status is live (`WAITING` … `TABLE_READY`). Two phones signed into
the same account still cannot both hold a ticket.

**Ticket numbers are sequential and unique per venue.** `join_queue` increments
`queue_stats.last_issued_seq` inside the transaction that inserts the row, so
concurrent joins serialise on that venue and nothing else.

**Only legal transitions.** `queues_transition_guard` runs before every update.
A visit cannot jump from `CALLED` to `COMPLETED`; it has to be checked in and
have its table ready first.

**State-changing functions serialise on the row they touch.** `call_next` uses
`for update skip locked`; `check_in_queue` takes `for update` on the ticket;
`join_queue` takes `for update` on the restaurant row.

**A check-in photo is a real upload for that ticket.** `check_in_queue`
requires the path to start with `{restaurant_id}/{queue_id}/` and to exist in
`storage.objects` — a client cannot pass an invented string.

**Reviews require a completed visit.** `reviews_insert_completed_only` re-checks
that the ticket belongs to the author and is `COMPLETED`. `reviews.queue_id` is
unique; a second submit updates `rating` and `review` (the only columns a
client may UPDATE).

**Counters cannot drift.** `queues_stats_sync` recomputes `queue_stats` after
every insert, update and delete on `queues`.

---

## Row Level Security and column grants

| Who | Can read |
| --- | --- |
| Anyone | `restaurants`, `queue_stats`, `reviews` |
| A customer | their own `users` row, their own `queues`, their own `notifications` |
| Staff | every `queues` row for venues they are listed against |

| Table | Columns a client may UPDATE (RLS still limits the rows) |
| --- | --- |
| `users` | `name`, `phone`, `avatar_url`, `fcm_token`, `locale` — **not** `role` |
| `reviews` | `rating`, `review` — not the restaurant, user or visit |
| `notifications` | `is_read` |
| `restaurants` (staff only) | `is_open`, `available_seats`, `description`, `image_url`, hours, `average_service_minutes`, `queue_capacity`, `checkin_window_minutes` |

Staff access comes from `restaurant_staff`, which has no client write policy:
staff are provisioned by hand (SQL editor or a service-role script). Since
0012 sign-up always creates a `CUSTOMER`; the role in sign-up metadata is
ignored.

`supabase/tests/rls_test.sql` and `supabase/tests/journey_test.sql` assert
these rules.

---

## Realtime

`queues` and `queue_stats` are in the `supabase_realtime` publication with
`replica identity full`. RLS still applies to realtime payloads, so a customer
receives events only for their own tickets — and for the public counters, which
is what tells them the line moved.

---

## Notifications

Two channels, used together:

1. **Push (FCM)** — the `queue-notifier` Edge Function, called by the
   `queues_notify` trigger on every change to `queues`. Reaches the phone even
   when the app is closed or the device restarted.
2. **Live tracking (on device)** — `QueueTrackingService`, a foreground service
   that runs while a ticket is live, keeps the queue in an ongoing notification
   (with a countdown once called) and lets the local milestone scheduler fire
   while the app is in the background. This is what makes the app usable before
   FCM is configured.

### Setting up push

1. **Firebase**: create a project, add the Android app `org.umn.ngantriin`,
   download `google-services.json` into `app/`. The build picks it up
   automatically (see `app/build.gradle.kts`).
2. **Edge Function**:
   ```sh
   supabase functions deploy queue-notifier --no-verify-jwt
   supabase secrets set QUEUE_NOTIFIER_SECRET=<random string> \
       FCM_PROJECT_ID=<firebase project id> \
       FCM_SERVICE_ACCOUNT_JSON='<service account json>'
   ```
3. **Database** (once per project, same secret):
   ```sql
   alter database postgres set app.settings.functions_url =
       'https://<project-ref>.supabase.co/functions/v1';
   alter database postgres set app.settings.queue_notifier_secret = '<random string>';
   ```

The function rejects any call without the secret, and takes only the ticket id
from the request — status and recipient are re-read from the database, so a
forged call cannot push to an arbitrary user. It is idempotent: it inserts into
`notifications` first, and the unique `(queue_id, type)` index makes a repeat a
no-op. Pushes are written in the user's `users.locale` (kept in sync by the
app) and also carry the raw numbers, so the app renders them in its current
language.

Earlier versions sent the service-role key in `app.settings.service_role_key`.
That setting is no longer read; remove it:

```sql
alter database postgres reset app.settings.service_role_key;
```

### Scheduled jobs

0012 schedules, when `pg_cron` is available:

| Job | Schedule | Does |
| --- | --- | --- |
| `ngantriin-expire-overdue-queues` | every minute | `expire_overdue_queues()` |
| `ngantriin-cleanup-old-notifications` | 03:00 daily | `cleanup_old_notifications(30)` |

Enable it under Database → Extensions → `pg_cron` before applying 0012, or
re-run the scheduling block at the bottom of 0012 afterwards.
