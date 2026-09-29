# Backend contract

What the Android client assumes about Supabase. Every assumption here is
enforced by the SQL in `supabase/migrations/`, not by the client.

---

## Tables

| Table | Purpose |
| --- | --- |
| `users` | App profile mirroring `auth.users`; holds `role` and `fcm_token` |
| `restaurants` | Venue, coordinates, opening hours, queue configuration |
| `restaurant_staff` | Which accounts may manage which venues |
| `queues` | One row per customer group per visit |
| `queue_stats` | Denormalised counters, maintained by trigger |
| `reviews` | One per completed ticket |
| `notifications` | Every push, persisted so it can be tracked and de-duplicated; `cleanup_old_notifications()` (0008) deletes rows past a retention window |
| `storage.objects` (`checkin-photos` bucket) | Check-in verification photos, path `{restaurant_id}/{queue_id}/{timestamp}.jpg`, private — readable only by the ticket's own customer and that restaurant's staff |

### Queue configuration lives on the restaurant

`average_service_minutes` is the multiplier behind every wait estimate.
`queue_capacity` is what `QUEUE_FULL` compares against. Changing a venue's
pace is a data change, not a release.

`checkin_radius_meters` is no longer read by check-in (that's photo-verified
now — see `0006_checkin_photo.sql`); the column stays on `restaurants` since
dropping it isn't necessary and someone may still want it for analytics.

---

## Functions the client calls

All of them are `SECURITY DEFINER` and validate their own preconditions, so the
`queues` table itself stays write-locked (`queues_insert_none`).

| Function | Raises | Becomes |
| --- | --- | --- |
| `join_queue(restaurant, party_size, note)` | `RESTAURANT_CLOSED` | "This restaurant is closed right now." |
| | `QUEUE_FULL` | "This queue is full. Try again a little later." |
| | `ALREADY_IN_QUEUE` | "You already have an active queue." |
| | `JOIN_COOLDOWN` | "Please wait a moment before rejoining this queue." |
| `cancel_queue(queue)` | `QUEUE_NOT_CANCELLABLE` | "This queue can no longer be cancelled." |
| `check_in_queue(queue, photo_url)` | `QUEUE_NOT_CALLED` | "You can check in once the restaurant calls your number." |
| | `PHOTO_REQUIRED` | "Please take a photo to check in." |
| `call_next(restaurant)` | `QUEUE_EMPTY` | "Nobody is waiting right now." |
| | `NOT_RESTAURANT_STAFF` | "You don't manage this restaurant." |
| `staff_update_queue_status(queue, status)` | `ILLEGAL_TRANSITION` | generic server error |
| `queue_position(queue)` | — | people ahead, position, estimate, now serving |
| `promote_almost_there(restaurant)` | — | moves near-front tickets to `ALMOST_THERE` |

These tokens are part of the contract. `ErrorMapper` matches on them and
`ErrorMapperTest` fails if either side is renamed alone.

---

## Invariants the database guarantees

**One active queue per customer.** A partial unique index on `queues (user_id)`
where the status is active. Not a client-side check — two phones signed into
the same account still cannot both hold a ticket.

**Ticket numbers are sequential and unique per venue.** `join_queue` increments
`queue_stats.last_issued_seq` inside the transaction that inserts the row, so
concurrent joins serialise on that venue and nothing else.

**Only legal transitions.** `queues_transition_guard` runs before every update.

**State-changing functions serialise on the row they touch.** `call_next` uses
`for update skip locked` on the ticket it's about to call; `check_in_queue`
takes `for update` on the ticket row (0006); `join_queue` takes `for update`
on the restaurant row for the duration of its capacity/duplicate checks and
the insert (0007) — two staff calling "next" at once, or a check-in racing a
staff-side status change, can't produce an inconsistent result.

**Reviews require a completed visit.** `reviews_insert_completed_only` re-checks
that the ticket belongs to the author and is `COMPLETED`. `reviews.queue_id` is
unique, so a visit can be reviewed once.

**Counters cannot drift.** `queues_stats_sync` recomputes `queue_stats` after
every insert, update and delete on `queues`. Nothing writes those numbers by
hand.

---

## Row Level Security

| Who | Can read |
| --- | --- |
| Anyone | `restaurants`, `queue_stats`, `reviews` |
| A customer | their own `users` row, their own `queues`, their own `notifications` |
| Staff | every `queues` row for venues they are listed against |

A customer cannot see another customer's ticket, which is why position has to
be computed server side. Nobody can see another customer's identity at all.

`restaurant_staff` has no INSERT/UPDATE/DELETE policy at all, so no
authenticated user — staff or otherwise — can add themselves to it via the
client. Staff are provisioned by hand (SQL editor or a service-role script),
documented in the main README's "Connecting a real backend" section.
`supabase/tests/rls_test.sql` asserts this and the two isolation rules above
explicitly.

---

## Realtime

`queues` and `queue_stats` are in the `supabase_realtime` publication with
`replica identity full`. RLS still applies to realtime payloads, so a customer
receives events only for their own tickets — and for the public counters, which
is what tells them the line moved.

---

## Notifications

`0005_notify_hook.sql` calls the `queue-notifier` Edge Function on every change
to `queues`. Configure it once per project:

```sql
alter database postgres set app.settings.functions_url =
    'https://<project-ref>.supabase.co/functions/v1';
alter database postgres set app.settings.service_role_key = '<service role key>';
```

A Supabase Database Webhook does the same job from the dashboard. Use one or
the other — both together fire every event twice.

The function is idempotent by construction: it inserts into `notifications`
first, and the unique `(queue_id, type)` index means a repeated webhook
conflicts instead of sending a second push.
