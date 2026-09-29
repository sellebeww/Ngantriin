// =============================================================================
// NGANTRIIN — queue-notifier Edge Function
//
// Sections 15 and 37, server side. A Postgres webhook calls this on every
// change to `queues`; the function works out which milestone each affected
// ticket has reached, records it, and pushes it through FCM.
//
// Notifications are written to public.notifications first. That table has a
// unique (queue_id, type) constraint, so a duplicate webhook — or a queue that
// oscillates around a threshold — cannot send the same message twice: the
// insert simply conflicts and nothing is pushed.
//
// Deploy:
//   supabase functions deploy queue-notifier --no-verify-jwt
//   supabase secrets set FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON='{...}'
//
// Then create a database webhook on public.queues (insert + update) pointing
// at this function, or apply supabase/migrations/0005_notify_hook.sql.
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type QueueStatus =
  | "WAITING"
  | "ALMOST_THERE"
  | "CALLED"
  | "CHECKED_IN"
  | "COMPLETED"
  | "CANCELLED";

type NotificationType =
  | "ALMOST_THERE"
  | "RETURN_NOW"
  | "CALLED"
  | "CHECKED_IN"
  | "COMPLETED"
  | "CANCELLED";

interface QueueRow {
  id: string;
  restaurant_id: string;
  user_id: string;
  queue_number: string;
  ticket_sequence: number;
  status: QueueStatus;
}

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  record: QueueRow | null;
  old_record: QueueRow | null;
}

// Mirrors QueueMath.ALMOST_THERE_THRESHOLD / RETURN_NOW_THRESHOLD.
const ALMOST_THERE_THRESHOLD = 3;
const RETURN_NOW_THRESHOLD = 1;

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  // Service role: this runs behind the webhook, not on behalf of a user, and
  // has to read other customers' rows to count the line.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (request) => {
  let payload: WebhookPayload;
  try {
    payload = await request.json();
  } catch {
    return new Response("invalid payload", { status: 400 });
  }

  const changed = payload.record ?? payload.old_record;
  if (!changed) return new Response("ok");

  const restaurantId = changed.restaurant_id;

  // Promote anyone who is now near the front, then look at every live ticket
  // at this venue: one customer being served changes the position of all of
  // the people behind them.
  await supabase.rpc("promote_almost_there", { p_restaurant_id: restaurantId });

  const { data: restaurant } = await supabase
    .from("restaurants")
    .select("average_service_minutes")
    .eq("id", restaurantId)
    .single();

  const { data: live } = await supabase
    .from("queues")
    .select("id, user_id, queue_number, ticket_sequence, status")
    .eq("restaurant_id", restaurantId)
    .in("status", ["WAITING", "ALMOST_THERE", "CALLED", "CHECKED_IN"])
    .order("ticket_sequence", { ascending: true });

  const tickets = live ?? [];
  const serviceMinutes = restaurant?.average_service_minutes ?? 3;

  const occupying = new Set(["WAITING", "ALMOST_THERE", "CALLED"]);

  for (const ticket of tickets) {
    const peopleAhead = tickets.filter(
      (other) =>
        other.ticket_sequence < ticket.ticket_sequence &&
        occupying.has(other.status),
    ).length;

    const milestone = milestoneFor(ticket.status, peopleAhead);
    if (!milestone) continue;

    const copy = copyFor(milestone, {
      peopleAhead,
      estimatedWait: peopleAhead * serviceMinutes,
      queueNumber: ticket.queue_number,
    });

    // The unique (queue_id, type) index is the de-duplication; an existing row
    // means this milestone already went out.
    const { data: inserted, error } = await supabase
      .from("notifications")
      .insert({
        user_id: ticket.user_id,
        queue_id: ticket.id,
        title: copy.title,
        body: copy.body,
        type: milestone,
      })
      .select("id")
      .maybeSingle();

    if (error || !inserted) continue;

    await push(ticket.user_id, {
      id: inserted.id,
      queue_id: ticket.id,
      restaurant_id: restaurantId,
      type: milestone,
      status: ticket.status,
      title: copy.title,
      body: copy.body,
    });
  }

  // A ticket that just ended is no longer in `live`, so handle it directly.
  if (payload.record && isTerminal(payload.record.status)) {
    const ticket = payload.record;
    const milestone: NotificationType =
      ticket.status === "COMPLETED" ? "COMPLETED" : "CANCELLED";
    const copy = copyFor(milestone, {
      peopleAhead: 0,
      estimatedWait: 0,
      queueNumber: ticket.queue_number,
    });

    const { data: inserted } = await supabase
      .from("notifications")
      .insert({
        user_id: ticket.user_id,
        queue_id: ticket.id,
        title: copy.title,
        body: copy.body,
        type: milestone,
      })
      .select("id")
      .maybeSingle();

    if (inserted) {
      await push(ticket.user_id, {
        id: inserted.id,
        queue_id: ticket.id,
        restaurant_id: restaurantId,
        type: milestone,
        status: ticket.status,
        title: copy.title,
        body: copy.body,
      });
    }
  }

  return new Response("ok");
});

function isTerminal(status: QueueStatus) {
  return status === "COMPLETED" || status === "CANCELLED";
}

/** Mirrors QueueMath.notificationMilestone. */
function milestoneFor(
  status: QueueStatus,
  peopleAhead: number,
): NotificationType | null {
  if (status === "CALLED") return "CALLED";
  if (status === "CHECKED_IN") return "CHECKED_IN";
  if (peopleAhead <= RETURN_NOW_THRESHOLD) return "RETURN_NOW";
  if (peopleAhead <= ALMOST_THERE_THRESHOLD) return "ALMOST_THERE";
  return null;
}

/**
 * Mirrors QueueNotificationCopy so both channels read identically.
 *
 * Section 28.13 (privacy): nothing here names the restaurant. A lock screen
 * is a public surface, and which venue someone is waiting at is exactly the
 * kind of detail that shouldn't be readable over their shoulder — the name
 * is available the moment they tap in and open the app.
 */
function copyFor(
  type: NotificationType,
  context: {
    peopleAhead: number;
    estimatedWait: number;
    queueNumber: string;
  },
) {
  switch (type) {
    case "ALMOST_THERE":
      return {
        title: "Your turn is coming",
        body:
          `Only ${context.peopleAhead} ` +
          `${context.peopleAhead === 1 ? "group" : "groups"} ahead of you. ` +
          `Estimated wait: ~${context.estimatedWait} min.`,
      };
    case "RETURN_NOW":
      return {
        title: "Get ready",
        body: "Your turn is almost here. Please return to the restaurant.",
      };
    case "CALLED":
      return {
        title: "It's your turn!",
        body:
          `Queue ${context.queueNumber} is now being called. ` +
          `Please check in at the restaurant.`,
      };
    case "CHECKED_IN":
      return {
        title: "Check-in successful",
        body: "You're checked in. Enjoy!",
      };
    case "COMPLETED":
      return {
        title: "Thanks for visiting",
        body: "Tap to rate your experience.",
      };
    case "CANCELLED":
      return {
        title: "Queue cancelled",
        body: "Your ticket is no longer active.",
      };
  }
}

/**
 * Sends a data-only FCM message. Data-only (rather than `notification`) is
 * deliberate: NgantriinMessagingService renders it, so the app controls the
 * channel, the icon and whether it persists the event first.
 */
async function push(userId: string, data: Record<string, string>) {
  const { data: profile } = await supabase
    .from("users")
    .select("fcm_token")
    .eq("id", userId)
    .maybeSingle();

  const token = profile?.fcm_token;
  if (!token) return;

  const accessToken = await googleAccessToken();
  if (!accessToken) return;

  const projectId = Deno.env.get("FCM_PROJECT_ID");
  await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: { ...data, user_id: userId },
          android: { priority: "HIGH" },
        },
      }),
    },
  );
}

/** Service-account JWT exchanged for an OAuth token, cached for its lifetime. */
let cachedToken: { value: string; expiresAt: number } | null = null;

async function googleAccessToken(): Promise<string | null> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) {
    return cachedToken.value;
  }

  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
  if (!raw) return null;
  const account = JSON.parse(raw);

  const now = Math.floor(Date.now() / 1000);
  const claims = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encoder = new TextEncoder();
  const base64url = (input: string) =>
    btoa(input).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = base64url(JSON.stringify(claims));
  const unsigned = `${header}.${body}`;

  const pem = account.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));

  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(unsigned),
  );
  const signed = `${unsigned}.${
    base64url(String.fromCharCode(...new Uint8Array(signature)))
  }`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: signed,
    }),
  });

  if (!response.ok) return null;
  const json = await response.json();
  cachedToken = {
    value: json.access_token,
    expiresAt: Date.now() + json.expires_in * 1000,
  };
  return cachedToken.value;
}
