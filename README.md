# Ngantriin

**Good Food • Less Wait • Happier You**

A digital queue-management app for restaurants and F&B. A customer finds a
venue, sees how long the line actually is, takes a ticket from their phone,
walks away, gets told when their turn is close, comes back, and checks in with
a photo of themselves at the restaurant. Staff run the same queue from a
dashboard, and can review that photo.

Native Android — Kotlin, Jetpack Compose, MVVM, Supabase, Room, WorkManager,
Firebase Cloud Messaging.

---

## Running it

```bash
./gradlew :app:installDebug
```

That is the whole setup. With no credentials configured the app starts in
**demo mode**: an in-process backend seeded with six Gading Serpong restaurants
and a live queue at each. Every screen, every state transition and the whole
notification ladder work against it.

Demo mode does not authenticate. Any email signs you in, and no password is
stored or checked — the real path is Supabase Auth, and faking credential
verification locally would only look like security. An email starting with
`staff` (for example `staff@tacolibre.id`) signs in with the STAFF role and
opens the restaurant dashboard.

**Requirements:** JDK 17+, Android SDK with API 37 and build-tools 37, an
emulator or device on API 27+.

### Try the core loop

1. Sign in as `you@example.com`.
2. Open **Taco Libre** → **Join Queue**. The backend issues `T-008`;
   the confirmation screen shows 2 groups ahead and a ~6 minute estimate
   (2 groups × 3 minutes average service time).
3. Open **My Queue**. The ticket is already `ALMOST_THERE` and a notification
   has fired, because fewer than four groups are in front of it.
4. Sign out, sign back in as `staff@tacolibre.id`, pick **Taco Libre**, press
   **Call next**. Watch the queue move.

---

## Connecting a real backend

### 1. Supabase

Create a project, then run the migrations in order:

```bash
supabase db push          # or paste supabase/migrations/*.sql into the SQL editor
```

| File | What it sets up |
| --- | --- |
| `0001_schema.sql` | Tables, enums, the profile trigger, `queue_stats` maintenance |
| `0002_queue_functions.sql` | The queue state machine: ticket allocation, transitions |
| `0003_rls.sql` | Row Level Security and the realtime publication |
| `0004_seed.sql` | The six sample restaurants |
| `0005_notify_hook.sql` | Calls the notification Edge Function on every queue change |
| `0006_checkin_photo.sql` | Photo-verified check-in: `checkin-photos` Storage bucket + policies, rewrites `check_in_queue` to require a photo instead of GPS |
| `0007_join_queue_locking_and_cooldown.sql` | Row-locks `join_queue`'s capacity/duplicate checks; adds a 60s cooldown after cancelling before rejoining the same restaurant |
| `0008_notifications_cleanup.sql` | `cleanup_old_notifications()` — deletes notification rows older than N days (manual call or `pg_cron`, see the file) |

Two SQL test files under `supabase/tests/` (`rls_test.sql`, `concurrency_test.sql`)
exercise RLS isolation and the locking/cooldown behaviour above — run them
against a real project's SQL editor or `psql`, they aren't part of the Android
test suite below.

Then put the credentials in `local.properties` (git-ignored, never committed):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

`CI` can pass the same two values as environment variables instead. Rebuild and
the app switches off demo mode automatically.

To make an account staff, add a row to `restaurant_staff`:

```sql
insert into restaurant_staff (restaurant_id, user_id)
values ('11111111-1111-4111-8111-111111111111', '<auth user id>');

update users set role = 'STAFF' where id = '<auth user id>';
```

This is the *only* sanctioned way to provision staff — `restaurant_staff` has
no INSERT/UPDATE/DELETE policy at all (0003), so no authenticated user can add
themselves via the client or a direct PostgREST call. Assign it by hand from
the SQL editor (as above) or from a service-role script; there is deliberately
no self-service staff signup in the app.

### Rate limiting and abuse prevention

What's covered: one active queue per customer (a partial unique index, not
just a client check — see `queues_one_active_per_user_idx`), and a 60-second
cooldown after cancelling before rejoining the same restaurant
(`0007_join_queue_locking_and_cooldown.sql`), which stops a join → cancel →
join spam loop. What's *not* covered, and is out of scope for this project:
per-IP rate limiting, CAPTCHA, and account-creation throttling — those live at
the infrastructure/Supabase-project level (e.g. Cloudflare in front of the
API, Supabase Auth's own rate limits), not in this schema.

### 2. Push notifications

```bash
# Drop the file Firebase gives you into app/ — the Gradle plugin is applied
# only when it exists, so the app still builds without it.
cp ~/Downloads/google-services.json app/

supabase functions deploy queue-notifier --no-verify-jwt
supabase secrets set FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON='{...}'
```

Until that is wired up, `QueueNotificationScheduler` derives the same
milestones locally while the app is running, so the notification ladder is
still demonstrable. Once FCM is configured it stands down and push takes over.

---

## How it fits together

```
Compose screen  →  ViewModel  →  Repository  ┬→  RemoteDataSource  →  Supabase
                   (UiState)    (cache-first) └→  Room (the render source)
```

Three decisions shape everything else:

**The database owns the queue.** Ticket numbers come from `join_queue()`, not
from the client. Every transition goes through a `SECURITY DEFINER` function
that re-checks the rule, so the tables themselves stay write-locked. Check-in
requires a photo the customer takes at the restaurant — `check_in_queue`
rejects the transition to `CHECKED_IN` outright if no photo was uploaded, and
staff can review that photo from the dashboard before treating the ticket as
seated.

**Room is what the UI renders; the network only writes into Room.** A failed
request leaves the previous rows exactly where they were, so going offline
degrades to stale data rather than an empty screen.

**Realtime carries a signal, not a delta.** A Postgres change publishes a bare
tick; the repository then re-reads the rows it cares about. That costs one
small query and buys immunity to dropped, duplicated or out-of-order events.

There is no DI framework. The graph is small and fixed, so `AppContainer` wires
it with `by lazy` properties and everything below is constructor-injected.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the layer-by-layer tour and
[docs/BACKEND.md](docs/BACKEND.md) for the SQL contract.

### Demo mode is not a shortcut

`DemoBackend` implements the same `RemoteDataSource` interfaces the Supabase
implementations do, enforces the same rules — server-allocated numbers, one
active queue per customer, the section 14 transition table, a non-blank photo
required to check in — and publishes changes through the same Flow contract.
It cannot quietly drift into a different state machine, because it has to
satisfy the same signatures. Its state is mirrored into Room, so a demo queue
survives a restart. (Demo mode has no real Supabase Storage, so the check-in
"photo" it records is a placeholder marker rather than an actual uploaded
image — the camera capture step itself still runs for real.)

---

## Project layout

```
org.umn.ngantriin
├── core/            Result types, typed errors, formatters, connectivity
├── domain/
│   ├── model/       Queue state machine, QueueMath, entities
│   └── repository/  Repository interfaces
├── data/
│   ├── local/       Room database, DAOs, entities, DataStore
│   ├── remote/      Supabase client, DTOs, data sources, demo backend
│   ├── mapper/      DTO ↔ domain ↔ entity
│   └── repository/  Repository implementations
├── location/        FusedLocationProvider wrapper, distance (nearby sort on
│                    Home/Search, distance display on Restaurant Detail —
│                    check-in itself is photo-verified, not GPS)
├── notification/    FCM service, channels, milestone scheduler
├── work/            WorkManager sync and the offline write queue
├── navigation/      Routes, NavHost, bottom bar
├── di/              AppContainer
└── ui/              theme, components, and one package per screen area

supabase/
├── migrations/      Schema, queue functions, RLS, seed, notification hook
└── functions/       queue-notifier Edge Function
```

---

## Tests

```bash
./gradlew :app:testDebugUnitTest          # 41 unit tests
./gradlew :app:connectedDebugAndroidTest  # 7 instrumented tests, needs a device
./gradlew :app:lintDebug
```

The unit tests cover the parts where a bug is invisible until it matters:

- **`QueueStatusTest`** — the transition table, checked exhaustively against
  every pair of states. The same rule lives in `is_valid_queue_transition`, so
  the two have to move together.
- **`QueueMathTest`** — position and wait estimates, using the worked example
  from the spec (A-020 seated, A-021…A-026 waiting, you hold A-027 → 6 ahead,
  ~18 minutes).
- **`GeoPointTest`** — haversine accuracy, still backing Home/Search's nearby
  sort and Restaurant Detail's distance display.
- **`RestaurantOpeningTest`** — opening hours, including past-midnight closing.
- **`ErrorMapperTest`** — the SQL error tokens that have to survive the trip to
  the user as real copy.
- **`FormattersTest` / `ValidatorsTest`** — presentation and form rules.

`NgantriinDatabaseTest` covers the DAO queries a unit test cannot reach: the
active-ticket filter, the history join (restaurant name plus "already
reviewed"), and the catalogue replace that drops venues the backend stopped
returning. `QueueNotificationCopyTest` asserts no notification, of any type,
ever includes the restaurant's name — both need a real `Context`, hence
instrumented rather than plain unit tests.
