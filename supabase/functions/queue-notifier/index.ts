// =============================================================================
// NGANTRIIN: Edge Function queue-notifier
//
// Sisi server. Trigger `queues_notify` (0012) memanggil ini
// di setiap perubahan pada `queues`. Fungsi ini mencari milestone yang
// dicapai tiap tiket yang terdampak, mencatatnya, lalu mengirimnya lewat FCM.
//
// Model kepercayaan:
// * Pemanggil harus menyertakan QUEUE_NOTIFIER_SECRET di `x-ngantriin-secret`.
// Fungsi ini di-deploy dengan --no-verify-jwt, jadi header ini satu-satunya
// penghalang antara internet publik dan push ke pengguna mana pun.
// * Hanya id tiket yang diambil dari request. Status, penerima, dan
// semua lainnya dibaca ulang dari database dengan service role, jadi
// pemanggil yang memegang secret pun tidak bisa mengarang notifikasi.
//
// Deduplikasi: public.notifications punya unique (queue_id, type). Webhook
// yang berulang, atau antrean yang naik turun di sekitar ambang, bentrok saat
// insert dan tidak ada yang dikirim.
//
// Deploy:
// supabase functions deploy queue-notifier --no-verify-jwt
// supabase secrets set QUEUE_NOTIFIER_SECRET=<random> \
// FCM_PROJECT_ID=... FCM_SERVICE_ACCOUNT_JSON='{...}'
// -- dan di SQL (lihat 0012):
// alter database postgres set app.settings.functions_url = 'https://<ref>.supabase.co/functions/v1';
// alter database postgres set app.settings.queue_notifier_secret = '<same random>';
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type QueueStatus =
  | "WAITING"
  | "ALMOST_THERE"
  | "CALLED"
  | "CHECKED_IN"
  | "TABLE_READY"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED";

type NotificationType =
  | "QUEUE_JOINED"
  | "ALMOST_THERE"
  | "RETURN_NOW"
  | "CALLED"
  | "CHECKED_IN"
  | "TABLE_READY"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED";

type Locale = "id" | "en";

interface QueueRow {
  id: string;
  restaurant_id: string;
  user_id: string;
  queue_number: string;
  ticket_sequence: number;
  status: QueueStatus;
  called_at: string | null;
  check_in_deadline: string | null;
  cancelled_by: string | null;
}

interface RequestBody {
  type?: string; // "INSERT" | "UPDATE", dari trigger queues_notify
  queue_id?: string;
  restaurant_id?: string;
  // Bentuk yang dikirim "Database Webhook" Supabase yang diatur di dashboard.
  record?: { id?: string; restaurant_id?: string } | null;
  old_record?: { id?: string; restaurant_id?: string } | null;
}

interface MilestoneContext {
  peopleAhead: number;
  estimatedWait: number;
  queueNumber: string;
  checkInMinutes: number;
}

// Meniru QueueMath.ALMOST_THERE_THRESHOLD / RETURN_NOW_THRESHOLD.
const ALMOST_THERE_THRESHOLD = 3;
const RETURN_NOW_THRESHOLD = 1;

const IN_LINE: ReadonlySet<QueueStatus> = new Set([
  "WAITING",
  "ALMOST_THERE",
  "CALLED",
  "CHECKED_IN",
]);
const TERMINAL: ReadonlySet<QueueStatus> = new Set([
  "COMPLETED",
  "CANCELLED",
  "EXPIRED",
]);

const QUEUE_COLUMNS =
  "id, restaurant_id, user_id, queue_number, ticket_sequence, status, called_at, check_in_deadline, cancelled_by";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  // Service role: ini jalan di belakang trigger, bukan atas nama pengguna, dan
  // harus membaca baris pelanggan lain untuk menghitung antrean.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (request) => {
  const expected = Deno.env.get("QUEUE_NOTIFIER_SECRET");
  if (!expected) {
    return new Response("QUEUE_NOTIFIER_SECRET is not configured", {
      status: 500,
    });
  }
  if (!timingSafeEqual(request.headers.get("x-ngantriin-secret") ?? "", expected)) {
    return new Response("unauthorized", { status: 401 });
  }

  let body: RequestBody;
  try {
    body = await request.json();
  } catch {
    return new Response("invalid payload", { status: 400 });
  }

  const queueId = body.queue_id ?? body.record?.id ?? body.old_record?.id;
  if (!queueId) return new Response("ok");

  const { data: changed } = await supabase
    .from("queues")
    .select(QUEUE_COLUMNS)
    .eq("id", queueId)
    .maybeSingle<QueueRow>();
  if (!changed) return new Response("ok");

  const restaurantId = changed.restaurant_id;

  // Selesaikan check-in yang lewat batas dulu: tiket yang sudah kedaluwarsa tapi
  // belum disapu masih dihitung "di depan" dan membuat semua estimasi di bawah terlalu lama.
  await supabase.rpc("expire_overdue_queues", { p_restaurant_id: restaurantId });

  // Naikkan siapa pun yang sekarang dekat dengan depan, lalu lihat semua tiket
  // aktif di restoran ini: satu rombongan yang didudukkan menggeser semua di belakangnya.
  await supabase.rpc("promote_almost_there", { p_restaurant_id: restaurantId });

  const { data: restaurant } = await supabase
    .from("restaurants")
    .select("average_service_minutes, checkin_window_minutes")
    .eq("id", restaurantId)
    .single();

  // Kecepatan terukur (jatuh ke yang dikonfigurasi di dalam fungsi), supaya
  // estimasi yang dikirim sama dengan yang ditampilkan aplikasi.
  const { data: pace } = await supabase.rpc("effective_service_minutes", {
    p_restaurant_id: restaurantId,
  });
  const serviceMinutes: number = pace ?? restaurant?.average_service_minutes ?? 3;
  const checkInMinutes = restaurant?.checkin_window_minutes ?? 10;

  const { data: live } = await supabase
    .from("queues")
    .select(QUEUE_COLUMNS)
    .eq("restaurant_id", restaurantId)
    .in("status", ["WAITING", "ALMOST_THERE", "CALLED", "CHECKED_IN", "TABLE_READY"])
    .order("ticket_sequence", { ascending: true })
    .returns<QueueRow[]>();

  const tickets = live ?? [];
  const locales = await localesFor(
    [...new Set([...tickets.map((t) => t.user_id), changed.user_id])],
  );

  for (const ticket of tickets) {
    const peopleAhead = tickets.filter(
      (other) =>
        other.ticket_sequence < ticket.ticket_sequence &&
        IN_LINE.has(other.status),
    ).length;

    const context: MilestoneContext = {
      peopleAhead,
      estimatedWait: peopleAhead * serviceMinutes,
      queueNumber: ticket.queue_number,
      checkInMinutes,
    };
    const locale = locales.get(ticket.user_id) ?? "id";

    // Konfirmasi bahwa tiket ada, dengan estimasi pertama. Hanya untuk
    // baris yang baru di-insert, tidak pernah untuk update berikutnya.
    const justJoined = body.type === "INSERT" && ticket.id === changed.id;
    if (justJoined) {
      await deliver(ticket, "QUEUE_JOINED", locale, context);
    }

    const milestone = milestoneFor(ticket, peopleAhead);
    if (!milestone) continue;

    // Join ke antrean pendek sudah memberi tahu berapa yang di depan.
    // Catat milestone sebagai terbaca supaya tidak terkirim lagi nanti, tapi
    // jangan bunyi dua kali dalam detik yang sama.
    await deliver(ticket, milestone, locale, context, justJoined);
  }

  // Tiket yang baru berakhir tidak lagi aktif, jadi ditangani langsung.
  // Pelanggan yang membatalkan di HP-nya sendiri tidak perlu diberi tahu.
  const cancelledByCustomer = changed.status === "CANCELLED" &&
    changed.cancelled_by === changed.user_id;
  if (TERMINAL.has(changed.status) && !cancelledByCustomer) {
    await deliver(
      changed,
      changed.status as NotificationType,
      locales.get(changed.user_id) ?? "id",
      {
        peopleAhead: 0,
        estimatedWait: 0,
        queueNumber: changed.queue_number,
        checkInMinutes,
      },
    );
  }

  return new Response("ok");
});

/** Meniru QueueMath.notificationMilestone. */
function milestoneFor(
  ticket: QueueRow,
  peopleAhead: number,
): NotificationType | null {
  switch (ticket.status) {
    case "CALLED":
      return "CALLED";
    case "CHECKED_IN":
      return "CHECKED_IN";
    case "TABLE_READY":
      return "TABLE_READY";
    case "WAITING":
    case "ALMOST_THERE":
      if (peopleAhead <= RETURN_NOW_THRESHOLD) return "RETURN_NOW";
      if (peopleAhead <= ALMOST_THERE_THRESHOLD) return "ALMOST_THERE";
      return null;
    default:
      return null;
  }
}

async function deliver(
  ticket: QueueRow,
  type: NotificationType,
  locale: Locale,
  context: MilestoneContext,
  quiet = false,
) {
  const copy = copyFor(type, locale, context);

  // Index unique (queue_id, type) yang menjadi deduplikasi, konflik berarti
  // milestone ini sudah terkirim.
  const { data: inserted, error } = await supabase
    .from("notifications")
    .insert({
      user_id: ticket.user_id,
      queue_id: ticket.id,
      title: copy.title,
      body: copy.body,
      type,
      is_read: quiet,
    })
    .select("id")
    .maybeSingle();

  if (error || !inserted || quiet) return;

  // Nilai mentah ikut dikirim, supaya aplikasi bisa menulis teksnya dengan
  // bahasa yang sedang dipilih sekarang.
  await push(ticket.user_id, {
    id: inserted.id,
    queue_id: ticket.id,
    restaurant_id: ticket.restaurant_id,
    type,
    status: ticket.status,
    title: copy.title,
    body: copy.body,
    queue_number: ticket.queue_number,
    people_ahead: String(context.peopleAhead),
    estimated_wait: String(context.estimatedWait),
    check_in_minutes: String(context.checkInMinutes),
    check_in_deadline: ticket.check_in_deadline ?? "",
  });
}

async function localesFor(userIds: string[]): Promise<Map<string, Locale>> {
  const result = new Map<string, Locale>();
  if (userIds.length === 0) return result;
  const { data } = await supabase
    .from("users")
    .select("id, locale")
    .in("id", userIds);
  for (const row of data ?? []) {
    result.set(row.id, row.locale === "en" ? "en" : "id");
  }
  return result;
}

/**
 * Meniru QueueNotificationCopy supaya kedua saluran terbaca sama.
 *
 * Privasi: tidak ada yang di sini menyebut nama restoran. Lock screen
 * bisa dilihat siapa saja, dan di restoran mana seseorang mengantre adalah
 * detail yang tidak seharusnya terbaca dari balik bahu.
 */
function copyFor(
  type: NotificationType,
  locale: Locale,
  c: MilestoneContext,
): { title: string; body: string } {
  const wait = waitText(c.estimatedWait, locale);
  if (locale === "en") {
    switch (type) {
      case "QUEUE_JOINED":
        return {
          title: "You're in the queue",
          body: `Your number is ${c.queueNumber}. ${c.peopleAhead} ${c.peopleAhead === 1 ? "group" : "groups"} ahead of you. Estimated wait: ${wait}.`,
        };
      case "ALMOST_THERE":
        return {
          title: "Your turn is coming",
          body: `Only ${c.peopleAhead} ${c.peopleAhead === 1 ? "group" : "groups"} ahead of you. Estimated wait: ${wait}.`,
        };
      case "RETURN_NOW":
        return {
          title: "Head back now",
          body: "You're almost up. Start heading back to the restaurant.",
        };
      case "CALLED":
        return {
          title: "It's your turn!",
          body: `Queue ${c.queueNumber} is being called. Check in within ${c.checkInMinutes} minutes or your ticket expires.`,
        };
      case "CHECKED_IN":
        return {
          title: "You're checked in",
          body: "We'll let you know the moment your table is ready.",
        };
      case "TABLE_READY":
        return {
          title: "Your table is ready",
          body: `Show queue ${c.queueNumber} to the staff and they'll seat you.`,
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
      case "EXPIRED":
        return {
          title: "Your ticket expired",
          body: `Queue ${c.queueNumber} wasn't checked in within ${c.checkInMinutes} minutes. You can join again.`,
        };
    }
  }

  switch (type) {
    case "QUEUE_JOINED":
      return {
        title: "Kamu masuk antrean",
        body: `Nomormu ${c.queueNumber}. Ada ${c.peopleAhead} rombongan di depanmu. Perkiraan tunggu: ${wait}.`,
      };
    case "ALMOST_THERE":
      return {
        title: "Giliranmu sudah dekat",
        body: `Tinggal ${c.peopleAhead} rombongan di depanmu. Perkiraan tunggu: ${wait}.`,
      };
    case "RETURN_NOW":
      return {
        title: "Saatnya kembali",
        body: "Sebentar lagi giliranmu. Mulai kembali ke restoran sekarang.",
      };
    case "CALLED":
      return {
        title: "Giliranmu!",
        body: `Antrean ${c.queueNumber} sedang dipanggil. Check-in dalam ${c.checkInMinutes} menit atau antreanmu hangus.`,
      };
    case "CHECKED_IN":
      return {
        title: "Check-in berhasil",
        body: "Kami kabari begitu mejamu siap.",
      };
    case "TABLE_READY":
      return {
        title: "Mejamu sudah siap",
        body: `Tunjukkan nomor ${c.queueNumber} ke staf, kamu akan diantar ke meja.`,
      };
    case "COMPLETED":
      return {
        title: "Terima kasih sudah berkunjung",
        body: "Ketuk untuk memberi ulasan.",
      };
    case "CANCELLED":
      return {
        title: "Antrean dibatalkan",
        body: "Tiket antreanmu sudah tidak aktif.",
      };
    case "EXPIRED":
      return {
        title: "Antreanmu hangus",
        body: `Antrean ${c.queueNumber} tidak check-in dalam ${c.checkInMinutes} menit. Kamu bisa antre lagi.`,
      };
  }
}

function waitText(minutes: number, locale: Locale): string {
  const min = locale === "en" ? "min" : "mnt";
  const hr = locale === "en" ? "hr" : "jam";
  if (minutes <= 0) return locale === "en" ? "no wait" : "tanpa antre";
  if (minutes < 60) return `~${minutes} ${min}`;
  if (minutes % 60 === 0) return `~${minutes / 60} ${hr}`;
  return `~${Math.floor(minutes / 60)} ${hr} ${minutes % 60} ${min}`;
}

function timingSafeEqual(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  let diff = left.length ^ right.length;
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return diff === 0;
}

/**
 * Mengirim pesan FCM data-only. Data-only (bukan `notification`) sengaja:
 * NgantriinMessagingService yang menampilkannya, jadi aplikasi mengatur
 * channel, ikon, bahasa, dan apakah event disimpan dulu.
 *
 * Penanganan kegagalan:
 * * 429 / 5xx / error jaringan: diulang dengan backoff (baris notifikasi
 * sudah tersimpan, jadi daftar di aplikasi tetap benar walau semua percobaan gagal).
 * * 401: token OAuth yang di-cache ditolak, buang dan coba sekali lagi.
 * * UNREGISTERED / NOT_FOUND: aplikasi di-uninstall atau datanya dihapus,
 * token dikosongkan supaya tidak ada push lagi yang terbuang.
 * * Selain itu (konfigurasi atau payload salah) dicatat dan tidak diulang.
 */
async function push(userId: string, data: Record<string, string>) {
  const { data: profile } = await supabase
    .from("users")
    .select("fcm_token")
    .eq("id", userId)
    .maybeSingle();

  const token = profile?.fcm_token;
  if (!token) return;

  const projectId = Deno.env.get("FCM_PROJECT_ID");
  if (!projectId) {
    console.error("FCM_PROJECT_ID is not configured; push skipped");
    return;
  }

  const body = JSON.stringify({
    message: {
      token,
      data: { ...data, user_id: userId },
      android: { priority: "HIGH" },
    },
  });

  const maxAttempts = 3;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const accessToken = await googleAccessToken();
    if (!accessToken) {
      console.error("FCM access token unavailable; push skipped");
      return;
    }

    let response: Response;
    try {
      response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body,
        },
      );
    } catch (error) {
      console.error(`FCM request failed (attempt ${attempt})`, error);
      if (attempt < maxAttempts) await sleep(backoffMs(attempt));
      continue;
    }

    if (response.ok) return;

    const detail = await response.text();

    if (response.status === 401) {
      cachedToken = null;
      continue;
    }

    if (isUnregistered(response.status, detail)) {
      await supabase
        .from("users")
        .update({ fcm_token: null })
        .eq("id", userId)
        .eq("fcm_token", token);
      return;
    }

    if (response.status === 429 || response.status >= 500) {
      console.error(`FCM ${response.status} (attempt ${attempt}): ${detail}`);
      if (attempt < maxAttempts) await sleep(backoffMs(attempt));
      continue;
    }

    console.error(`FCM rejected the message (${response.status}): ${detail}`);
    return;
  }
}

function isUnregistered(status: number, detail: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return detail.includes("UNREGISTERED") ||
    detail.includes("registration-token-not-registered");
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const backoffMs = (attempt: number) => 300 * 2 ** (attempt - 1);

/** JWT service account ditukar dengan token OAuth, di-cache selama masa berlakunya. */
let cachedToken: { value: string; expiresAt: number } | null = null;

async function googleAccessToken(): Promise<string | null> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) {
    return cachedToken.value;
  }

  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
  if (!raw) return null;
  let account;
  try {
    account = JSON.parse(raw);
  } catch {
    console.error("FCM_SERVICE_ACCOUNT_JSON is not valid JSON");
    return null;
  }

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

  if (!response.ok) {
    console.error(`Google token exchange failed (${response.status})`);
    return null;
  }
  const json = await response.json();
  cachedToken = {
    value: json.access_token,
    expiresAt: Date.now() + json.expires_in * 1000,
  };
  return cachedToken.value;
}
