# Architecture

A tour of the layers, and the reasoning behind the parts that are not obvious.

```
Compose screen  →  ViewModel  →  Repository  ┬→  RemoteDataSource  →  Supabase
                   (UiState)    (cache-first) └→  Room (the render source)
```

---

## The queue state machine

Everything else exists to serve this:

```
WAITING ──► ALMOST_THERE ──► CALLED ──► CHECKED_IN ──► COMPLETED
   │              │            │
   └──────────────┴────────────┴──────────────────► CANCELLED
```

`COMPLETED` and `CANCELLED` are terminal. A cancelled ticket can never return
to `WAITING` — a customer who left the queue and then saw themselves back in it
would never trust the app again.

The rule is written down twice, on purpose:

- `QueueStatus.canTransitionTo` rejects an illegal move before it leaves the
  phone, so the UI only ever offers legal actions.
- `is_valid_queue_transition` plus the `queues_transition_guard` trigger reject
  it at the database, so a stale client or a direct API call cannot get through.

`QueueStatusTest` checks all 36 state pairs. If the table changes, both halves
have to change with it.

---

## Where each number comes from

| Number | Source | Why there |
| --- | --- | --- |
| Queue number (`A-027`) | `join_queue()` reserves it under a row lock on `queue_stats` | Two phones tapping at the same instant cannot receive the same number |
| People ahead | `queue_position()`, a `SECURITY DEFINER` function | RLS hides other customers' rows, so the client genuinely cannot count the line itself |
| Estimated wait | `average_service_minutes × people ahead` | One formula, in `QueueMath`, called by both the client and the demo backend |
| Waiting count | `queue_stats`, maintained by an `AFTER` trigger on `queues` | Public and cheap, so the discovery list can show it for every venue at once |
| Check-in verification | `check_in_queue()` rejects the transition if `photo_url` is blank | A client cannot force `CHECKED_IN` without a photo reaching the server |

When the server-computed position is unavailable — offline, or a request in
flight — `QueueRepositoryImpl.fallbackPeopleAhead` degrades in two steps: count
the cached tickets ahead if any are visible (staff and demo mode can see them),
otherwise derive from the counters, bounded by the number still waiting. The
screen stays sensible rather than blank or wrong.

---

## Offline behaviour

Room is the render source. Repositories expose a `channelFlow` that does two
things at once:

```kotlin
channelFlow {
    launch { localFlow().collect { send(it) } }                      // render
    launch { remote.observeChanges().collect { syncIntoRoom() } }    // refresh
}
```

The UI therefore has data on the first frame, and a failed network call is a
non-event: nothing is deleted, the rows just get older. `ConnectivityObserver`
drives the offline banner so staleness is visible rather than silent.

### Writes that could not go out

`PendingAction` parks a write and `SyncWorker` replays it when WorkManager's
network constraint is satisfied. Only actions that stay correct when they
arrive late are queued:

- **Leaving a queue** — replayable. If the ticket has since been served, the
  transition guard rejects the replay, which makes a stale retry a harmless
  no-op rather than a wrong cancellation.
- **Submitting a review**, **marking a notification read** — replayable, both
  idempotent.
- **Joining a queue** — *not* replayable. A ticket issued twenty minutes after
  the tap is worse than no ticket, so a failed join is reported to the user
  immediately.

Leaving a queue offline is deliberately not applied optimistically. Showing a
ticket as cancelled and then having it reappear would break the one promise the
state machine makes.

---

## Realtime

`postgresChanges()` subscribes to a table and emits a bare `Unit` per change,
conflated. The repository re-reads the authoritative rows on every tick.

Deltas are discarded on purpose. Reconstructing queue state from a stream of
row diffs means a dropped, duplicated or out-of-order event leaves the screen
showing a position the database does not have. One small query per change is a
cheap price for not having that class of bug.

Two subscriptions drive the active-queue screen:

- `queues` filtered by `user_id` — the customer's own ticket.
- `queue_stats` filtered by `restaurant_id` — the line in front of it. This is
  the one that matters, because the position changes when *other* people are
  served, and those rows are invisible to this customer.

---

## Notifications

`QueueMath.notificationMilestone` decides what a ticket has reached:

| People ahead | Milestone |
| --- | --- |
| > 3 | nothing |
| ≤ 3 | `ALMOST_THERE` |
| ≤ 1 | `RETURN_NOW` |
| status = CALLED | `CALLED` |

De-duplication is structural, not a flag: every notification is a row with a
unique `(queue_id, type)` index. A ticket oscillating around a threshold cannot
send the same message twice, because the second insert conflicts.

Two delivery paths, one body of copy (`QueueNotificationCopy`):

- **Push** — the `queue-notifier` Edge Function runs on every queue change,
  recomputes positions, inserts the rows and sends data-only FCM messages.
  Data-only so the app renders them, picks the channel and persists the event
  before showing it.
- **Local** — `QueueNotificationScheduler` derives the same milestones from the
  same state while the app is in memory. It runs only when no
  `google-services.json` is present, and stands down entirely once push works.

Two channels, because "your turn is coming" and "your table is ready" are not
the same interruption: someone who mutes the first should still hear the second.

---

## Errors

`AppError` is a sealed class whose every case carries a string resource, so a
screen renders `error.message()` rather than exception text.

`ErrorMapper` is what turns a Postgres `raise exception 'QUEUE_FULL'` into
`AppError.QueueFull` and then into "This queue is full. Try again a little
later." The SQL tokens are load-bearing; `ErrorMapperTest` fails if either side
is renamed alone.

---

## Dependency injection

`AppContainer` holds the graph as `by lazy` properties and `LocalAppContainer`
hands it to composables. `containerViewModel { }` builds a ViewModel scoped to
the current nav entry.

The graph is small, fixed and known at startup, so this gives exactly what a DI
framework would without an annotation processor in the build or generated code
to step through. Everything below the container is constructor-injected, so
substituting an implementation in a test is a matter of calling a different
constructor.

---

## Navigation

One `NavHost` for the whole app. Which area you are in is derived from the
session and the onboarding flag in `NgantriinApp`:

```
session Loading or onboarding unknown  →  splash
onboarding not completed               →  onboarding
signed out                             →  login
signed in as STAFF                     →  staff dashboard
signed in                              →  home
```

Screens never navigate across that boundary themselves. Signing out anywhere
lands on login; signing in lands on the right home for the role. The splash is
a *state*, not a timed delay, so it disappears the moment the app knows where
to go — and it never flashes the login screen at someone already signed in.

---

## Build configuration

Two things about the toolchain are worth knowing before changing versions.

**AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android`
alongside it fails (`Cannot add extension with name 'kotlin'`), and the
built-in compiler is pinned to the version AGP bundles. Declaring the Kotlin
plugin at the root with `apply false` puts a newer KGP on the buildscript
classpath, which the built-in support then uses. That is how this project runs
Kotlin 2.3.21 under AGP 9.1.1.

The Kotlin version is not cosmetic: current releases of Coil and Supabase ship
class metadata a 2.2 compiler refuses to read.

**KSP registers its generated sources through `kotlin.sourceSets`**, which
built-in Kotlin rejects by default. `android.disallowKotlinSourceSets=false` in
`gradle.properties` is what lets Room's generated code reach the compiler.

`compileSdk` is 37 because Compose 1.12 and `core-ktx` 1.19 require it.
