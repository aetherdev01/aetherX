require("dotenv").config();
const path = require("path");
const TelegramBot = require("node-telegram-bot-api");

const { FirestoreClient } = require("./lib/firestoreClient");
const {
  createLicense,
  getLicense,
  updateLicense,
  deleteLicense,
  unbindDevice,
  findLicensesByDevice,
  listAllTokens,
} = require("./lib/licenseStore");
const { getMaintenanceStatus, setMaintenanceStatus } = require("./lib/maintenanceStore");
const { getUpdateInfo, setUpdateInfo } = require("./lib/updateStore");
const { generateUniqueToken } = require("./lib/tokenGenerator");
const {
  formatLicenseCard,
  formatMaintenanceCard,
  formatUpdateCard,
  isValidToken,
  isValidDeviceId,
  escapeMd,
} = require("./lib/format");

const TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const ADMIN_ID = String(process.env.ADMIN_TELEGRAM_ID || "");
const SERVICE_ACCOUNT_PATH =
  process.env.SERVICE_ACCOUNT_PATH || path.join(__dirname, "serviceAccountKey.json");

if (!TOKEN) {
  console.error("TELEGRAM_BOT_TOKEN belum diisi di .env");
  process.exit(1);
}
if (!ADMIN_ID) {
  console.error("ADMIN_TELEGRAM_ID belum diisi di .env");
  process.exit(1);
}

let firestore;
try {
  firestore = new FirestoreClient(SERVICE_ACCOUNT_PATH);
} catch (err) {
  console.error(err.message);
  process.exit(1);
}

// ── WAJIB: pastikan serviceAccountKey.json ini benar-benar milik project
// Firebase yang SAMA dengan yang dipakai aplikasi Android (app/google-services.json),
// bukan project lain / key dari akun lain.
//
// Kalau ini beda project, bot akan tetap bisa "generate lisensi" TANPA error
// HTTP sama sekali (REST call ke Firestore-nya sukses 200 OK) — cuma dokumennya
// nyasar ke project yang salah, jadi app tidak akan pernah menemukan kode itu.
// Ini persis gejala "bot bilang berhasil tapi kode 'tidak ditemukan' di app"
// yang sebelumnya sulit dilacak karena tidak ada pesan error sama sekali.
//
// EXPECTED_PROJECT_ID WAJIB diisi di .env (ambil dari project_id di
// app/google-services.json project Android-mu). Bot menolak start kalau ini
// kosong atau tidak cocok — supaya kesalahan ini tidak bisa lolos diam-diam lagi.
const EXPECTED_PROJECT_ID = process.env.EXPECTED_PROJECT_ID;
if (!EXPECTED_PROJECT_ID) {
  console.error(
    `❌ EXPECTED_PROJECT_ID belum diisi di .env.\n` +
      `   Bot ini akan menulis ke project "${firestore.projectId}" (dari serviceAccountKey.json),\n` +
      `   tapi tidak ada cara memverifikasi ini project yang benar tanpa EXPECTED_PROJECT_ID.\n` +
      `   Isi EXPECTED_PROJECT_ID di .env dengan project_id dari app/google-services.json\n` +
      `   aplikasi Android-mu, lalu jalankan ulang bot ini.`
  );
  process.exit(1);
}
if (firestore.projectId !== EXPECTED_PROJECT_ID) {
  console.error(
    `❌ serviceAccountKey.json ini untuk project Firebase "${firestore.projectId}", ` +
      `tapi EXPECTED_PROJECT_ID di .env diisi "${EXPECTED_PROJECT_ID}".\n` +
      `   Lisensi yang dibuat bot ini TIDAK akan terlihat di aplikasi Android kalau project-nya beda.\n` +
      `   Ambil ulang serviceAccountKey.json dari Firebase Console -> project "${EXPECTED_PROJECT_ID}" ` +
      `-> Project Settings -> Service accounts -> Generate new private key.`
  );
  process.exit(1);
}
console.log(`✅ Project Firestore terverifikasi: "${firestore.projectId}" (sama dengan app Android).`);

const bot = new TelegramBot(TOKEN, { polling: true });

// ── State percakapan sederhana per chat ──
const sessions = new Map();

function isAdmin(msg) {
  return String(msg.from.id) === ADMIN_ID;
}

function requireAdmin(msg) {
  if (!isAdmin(msg)) {
    bot.sendMessage(msg.chat.id, "⛔ Kamu tidak punya akses ke perintah ini.");
    return false;
  }
  return true;
}

function clearSession(chatId) {
  sessions.delete(chatId);
}

function mainMenuKeyboard() {
  return {
    reply_markup: {
      inline_keyboard: [
        [
          { text: "🎫 Generate Lisensi", callback_data: "menu:generate" },
          { text: "🔍 Cek Lisensi", callback_data: "menu:check" },
        ],
        [
          { text: "✏️ Edit Lisensi", callback_data: "menu:edit" },
          { text: "🗑️ Hapus Lisensi", callback_data: "menu:delete" },
        ],
        [
          { text: "📱 Cek Device ID", callback_data: "menu:device" },
          { text: "🔓 Reset Device", callback_data: "menu:unbind" },
        ],
        [{ text: "📋 Daftar Lisensi", callback_data: "menu:list" }],
        [
          { text: "🛠️ Maintenance", callback_data: "menu:maintenance" },
          { text: "🚀 Update Versi", callback_data: "menu:update" },
        ],
      ],
    },
  };
}

// ─────────────────────────────────────────────────────────
// /start & /help
// ─────────────────────────────────────────────────────────
bot.onText(/^\/start$/, (msg) => {
  const chatId = msg.chat.id;
  clearSession(chatId);
  const name = escapeMd(msg.from.first_name || "");
  bot.sendMessage(
    chatId,
    `👋 Halo, ${name}\\!\n\n` +
      `Bot manajemen lisensi *AetherX* siap dipakai \\(terhubung ke Firestore\\)\\.\n` +
      `Pilih menu di bawah, atau ketik /help untuk daftar perintah lengkap\\.`,
    { parse_mode: "MarkdownV2", ...mainMenuKeyboard() }
  );
});

bot.onText(/^\/help$/, (msg) => {
  // Dikirim sebagai PLAIN TEXT (tanpa parse_mode) — bukan MarkdownV2 — karena
  // teks bantuan ini penuh karakter yang di MarkdownV2 wajib di-escape
  // (`<`, `>`, `-`, `[`, `]`, `.`, `'`, dst, mis. "/generate <hari> [catatan]").
  // Menulis escape manual untuk teks statis sebanyak ini gampang meleset dan
  // pernah menyebabkan "ETELEGRAM: 400 Bad Request: can't parse entities"
  // saat ada karakter yang terlewat — plain text tidak butuh escaping sama
  // sekali dan tidak mungkin salah parse.
  const helpText = [
    "Perintah tersedia:",
    "",
    "/generate <hari> [catatan] — Buat lisensi baru. Contoh: /generate 30 Promo Juli",
    "/check <token> — Lihat detail lisensi",
    "/edit <token> — Edit status/expiry/device/catatan lisensi",
    "/delete <token> — Hapus lisensi permanen",
    "/device <deviceId> — Cari lisensi yang terpasang di device ID tsb",
    "/unbind <token> — Lepas device dari lisensi (reset ke status 'unused')",
    "/list — Daftar semua token lisensi",
    "/maintenance — Lihat/atur mode maintenance (dialog blocking di app)",
    "/update — Lihat/publish info versi terbaru (dialog update opsional di app)",
    "/cancel — Batalkan proses yang sedang berjalan",
    "",
    "Semua perintah admin hanya bisa dipakai oleh admin yang terdaftar.",
  ].join("\n");
  bot.sendMessage(msg.chat.id, helpText);
});

bot.onText(/^\/cancel$/, (msg) => {
  clearSession(msg.chat.id);
  bot.sendMessage(msg.chat.id, "❎ Proses dibatalkan.", mainMenuKeyboard());
});

// ─────────────────────────────────────────────────────────
// GENERATE LICENSE
// /generate <hari> [catatan]
// ─────────────────────────────────────────────────────────
bot.onText(/^\/generate(?:\s+(.*))?$/, async (msg, match) => {
  if (!requireAdmin(msg)) return;
  const chatId = msg.chat.id;
  const raw = (match[1] || "").trim();

  if (!raw) {
    sessions.set(chatId, { action: "generate", step: "ask_days" });
    return bot.sendMessage(chatId, "Berapa hari masa berlaku lisensi ini? (mis. `30`)", {
      parse_mode: "Markdown",
    });
  }

  const parts = raw.split(/\s+/);
  const days = parseInt(parts[0], 10);
  const note = parts.slice(1).join(" ");

  if (!Number.isFinite(days) || days <= 0) {
    return bot.sendMessage(chatId, "⚠️ Format: /generate <jumlah_hari> [catatan]. Contoh: /generate 30 Promo Juli");
  }

  await doGenerate(chatId, days, note);
});

async function doGenerate(chatId, days, note) {
  try {
    const token = await generateUniqueToken(firestore);
    const license = await createLicense(firestore, { token, days, note });
    await bot.sendMessage(
      chatId,
      `✅ *Lisensi baru berhasil dibuat\\!*\n\n${formatLicenseCard(license)}`,
      { parse_mode: "MarkdownV2" }
    );
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Gagal generate lisensi: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// CHECK LICENSE
// ─────────────────────────────────────────────────────────
bot.onText(/^\/check(?:\s+(.*))?$/, async (msg, match) => {
  const chatId = msg.chat.id;
  const token = (match[1] || "").trim();

  if (!token) {
    sessions.set(chatId, { action: "check", step: "waiting_token" });
    return bot.sendMessage(chatId, "Kirim token lisensi yang mau dicek (7 karakter):");
  }
  await handleCheck(chatId, token);
});

async function handleCheck(chatId, token) {
  if (!isValidToken(token)) {
    return bot.sendMessage(chatId, "⚠️ Format token tidak valid. Token harus 7 karakter huruf/angka.");
  }
  try {
    const license = await getLicense(firestore, token);
    if (!license) {
      return bot.sendMessage(chatId, `❌ Token \`${token}\` tidak ditemukan.`, { parse_mode: "Markdown" });
    }
    bot.sendMessage(chatId, formatLicenseCard(license), { parse_mode: "MarkdownV2" });
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// DEVICE ID LOOKUP
// ─────────────────────────────────────────────────────────
bot.onText(/^\/device(?:\s+(.*))?$/, async (msg, match) => {
  const chatId = msg.chat.id;
  const deviceId = (match[1] || "").trim();

  if (!deviceId) {
    sessions.set(chatId, { action: "device", step: "waiting_device" });
    return bot.sendMessage(chatId, "Kirim Device ID yang mau dicek:");
  }
  await handleDeviceCheck(chatId, deviceId);
});

async function handleDeviceCheck(chatId, deviceId) {
  if (!isValidDeviceId(deviceId)) {
    return bot.sendMessage(chatId, "⚠️ Device ID tidak valid (minimal 4 karakter).");
  }
  try {
    const licenses = await findLicensesByDevice(firestore, deviceId);
    if (licenses.length === 0) {
      return bot.sendMessage(chatId, `📱 Device ID \`${deviceId}\` belum terdaftar di lisensi manapun.`, {
        parse_mode: "Markdown",
      });
    }
    const cards = licenses.map(formatLicenseCard).join("\n\n───\n\n");
    bot.sendMessage(chatId, `📱 Lisensi yang terkait Device ID ini:\n\n${cards}`, { parse_mode: "MarkdownV2" });
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// UNBIND DEVICE
// ─────────────────────────────────────────────────────────
bot.onText(/^\/unbind(?:\s+(.*))?$/, async (msg, match) => {
  if (!requireAdmin(msg)) return;
  const chatId = msg.chat.id;
  const token = (match[1] || "").trim();

  if (!token) {
    sessions.set(chatId, { action: "unbind", step: "waiting_token" });
    return bot.sendMessage(chatId, "Kirim token lisensi yang device-nya mau direset:");
  }
  await handleUnbind(chatId, token);
});

async function handleUnbind(chatId, token) {
  if (!isValidToken(token)) {
    return bot.sendMessage(chatId, "⚠️ Format token tidak valid.");
  }
  try {
    const license = await getLicense(firestore, token);
    if (!license) {
      return bot.sendMessage(chatId, `❌ Token \`${token}\` tidak ditemukan.`, { parse_mode: "Markdown" });
    }
    await unbindDevice(firestore, token);
    bot.sendMessage(
      chatId,
      `🔓 Device berhasil dilepas dari token \`${token}\`. Status dikembalikan ke 'unused', lisensi bisa dipakai di device baru.`,
      { parse_mode: "Markdown" }
    );
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// DELETE LICENSE
// ─────────────────────────────────────────────────────────
bot.onText(/^\/delete(?:\s+(.*))?$/, async (msg, match) => {
  if (!requireAdmin(msg)) return;
  const chatId = msg.chat.id;
  const token = (match[1] || "").trim();

  if (!token) {
    sessions.set(chatId, { action: "delete", step: "waiting_token" });
    return bot.sendMessage(chatId, "Kirim token lisensi yang mau dihapus:");
  }
  await confirmDelete(chatId, token);
});

async function confirmDelete(chatId, token) {
  if (!isValidToken(token)) {
    return bot.sendMessage(chatId, "⚠️ Format token tidak valid.");
  }
  const license = await getLicense(firestore, token);
  if (!license) {
    return bot.sendMessage(chatId, `❌ Token \`${token}\` tidak ditemukan.`, { parse_mode: "Markdown" });
  }
  sessions.set(chatId, { action: "delete", step: "confirm", data: { token } });
  bot.sendMessage(
    chatId,
    `⚠️ Yakin mau hapus lisensi ini secara permanen?\n\n${formatLicenseCard(license)}`,
    {
      parse_mode: "MarkdownV2",
      reply_markup: {
        inline_keyboard: [
          [
            { text: "✅ Ya, hapus", callback_data: `delete_confirm:${token}` },
            { text: "❌ Batal", callback_data: "delete_cancel" },
          ],
        ],
      },
    }
  );
}

// ─────────────────────────────────────────────────────────
// LIST
// ─────────────────────────────────────────────────────────
bot.onText(/^\/list$/, async (msg) => {
  if (!requireAdmin(msg)) return;
  const chatId = msg.chat.id;
  try {
    const tokens = await listAllTokens(firestore);
    if (tokens.length === 0) {
      return bot.sendMessage(chatId, "Belum ada lisensi yang dibuat.");
    }
    const preview = tokens.slice(0, 50);
    const text =
      `📋 *Total ${tokens.length} lisensi*${tokens.length > 50 ? " (menampilkan 50 pertama)" : ""}:\n\n` +
      preview.map((t) => `\`${t}\``).join("\n");
    bot.sendMessage(chatId, text, { parse_mode: "Markdown" });
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
});

// ─────────────────────────────────────────────────────────
// MAINTENANCE MODE
// /maintenance — tampilkan status & menu on/off/edit
// ─────────────────────────────────────────────────────────
function maintenanceMenuKeyboard(status) {
  return {
    reply_markup: {
      inline_keyboard: [
        [
          status.enabled
            ? { text: "🟢 Matikan Maintenance", callback_data: "maint:disable" }
            : { text: "🔴 Aktifkan Maintenance", callback_data: "maint:enable" },
        ],
        [
          { text: "✏️ Edit Judul", callback_data: "maint:edit_title" },
          { text: "✏️ Edit Pesan", callback_data: "maint:edit_message" },
        ],
        [{ text: "❌ Tutup", callback_data: "maint:close" }],
      ],
    },
  };
}

bot.onText(/^\/maintenance$/, async (msg) => {
  if (!requireAdmin(msg)) return;
  await showMaintenanceMenu(msg.chat.id);
});

async function showMaintenanceMenu(chatId) {
  try {
    const status = await getMaintenanceStatus(firestore);
    await bot.sendMessage(chatId, formatMaintenanceCard(status), {
      parse_mode: "MarkdownV2",
      ...maintenanceMenuKeyboard(status),
    });
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// UPDATE VERSI APLIKASI
// /update — tampilkan info versi terbaru & menu publish/edit
//
// Alur "Publish Versi Baru" adalah conversation 4 langkah berurutan:
// versionCode (angka, wajib > yang tersimpan) -> versionName (teks bebas,
// mis. "1.2.0") -> downloadUrl (link GitHub Release) -> description
// (changelog, teks bebas, boleh multi-baris — kirim `-` untuk kosongkan).
// mandatory TIDAK ditanya di alur cepat ini (selalu tersimpan `false` kalau
// belum pernah diisi sebelumnya); admin bisa mengubahnya lewat tombol
// terpisah di menu kalau perlu.
// ─────────────────────────────────────────────────────────
function updateMenuKeyboard(info) {
  return {
    reply_markup: {
      inline_keyboard: [
        [{ text: "🚀 Publish Versi Baru", callback_data: "upd:publish" }],
        [
          { text: "✏️ Edit Link Download", callback_data: "upd:edit_url" },
          { text: "✏️ Edit Deskripsi", callback_data: "upd:edit_desc" },
        ],
        [
          info.mandatory
            ? { text: "🟢 Jadikan Opsional", callback_data: "upd:optional" }
            : { text: "🔴 Jadikan Wajib", callback_data: "upd:mandatory" },
        ],
        [{ text: "❌ Tutup", callback_data: "upd:close" }],
      ],
    },
  };
}

bot.onText(/^\/update$/, async (msg) => {
  if (!requireAdmin(msg)) return;
  await showUpdateMenu(msg.chat.id);
});

async function showUpdateMenu(chatId) {
  try {
    const info = await getUpdateInfo(firestore);
    await bot.sendMessage(chatId, formatUpdateCard(info), {
      parse_mode: "MarkdownV2",
      ...updateMenuKeyboard(info),
    });
  } catch (err) {
    console.error(err);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
}

// ─────────────────────────────────────────────────────────
// EDIT LICENSE (conversation flow)
// ─────────────────────────────────────────────────────────
bot.onText(/^\/edit(?:\s+(.*))?$/, async (msg, match) => {
  if (!requireAdmin(msg)) return;
  const chatId = msg.chat.id;
  const token = (match[1] || "").trim();

  if (!token) {
    sessions.set(chatId, { action: "edit", step: "waiting_token" });
    return bot.sendMessage(chatId, "Kirim token lisensi yang mau diedit:");
  }
  await startEditFlow(chatId, token);
});

async function startEditFlow(chatId, token) {
  if (!isValidToken(token)) {
    return bot.sendMessage(chatId, "⚠️ Format token tidak valid.");
  }
  const license = await getLicense(firestore, token);
  if (!license) {
    return bot.sendMessage(chatId, `❌ Token \`${token}\` tidak ditemukan.`, { parse_mode: "Markdown" });
  }
  sessions.set(chatId, { action: "edit", step: "choose_field", data: { token } });
  bot.sendMessage(chatId, `✏️ Edit lisensi \`${token}\`. Pilih field yang mau diubah:`, {
    parse_mode: "Markdown",
    reply_markup: {
      inline_keyboard: [
        [
          { text: "Status", callback_data: "edit_field:status" },
          { text: "Expiry (hari)", callback_data: "edit_field:expiresAt" },
        ],
        [
          { text: "Device ID", callback_data: "edit_field:deviceId" },
          { text: "Catatan", callback_data: "edit_field:note" },
        ],
        [{ text: "❌ Batal", callback_data: "edit_cancel" }],
      ],
    },
  });
}

// ─────────────────────────────────────────────────────────
// CALLBACK QUERY HANDLER
// ─────────────────────────────────────────────────────────
bot.on("callback_query", async (query) => {
  const chatId = query.message.chat.id;
  const data = query.data;
  const msg = { chat: { id: chatId }, from: query.from };

  bot.answerCallbackQuery(query.id).catch(() => {});

  if (data.startsWith("menu:")) {
    const key = data.split(":")[1];
    if (key === "generate") {
      if (!requireAdmin(msg)) return;
      sessions.set(chatId, { action: "generate", step: "ask_days" });
      return bot.sendMessage(chatId, "Berapa hari masa berlaku lisensi ini? (mis. `30`)", {
        parse_mode: "Markdown",
      });
    }
    if (key === "check") {
      sessions.set(chatId, { action: "check", step: "waiting_token" });
      return bot.sendMessage(chatId, "Kirim token lisensi yang mau dicek (7 karakter):");
    }
    if (key === "edit") {
      if (!requireAdmin(msg)) return;
      sessions.set(chatId, { action: "edit", step: "waiting_token" });
      return bot.sendMessage(chatId, "Kirim token lisensi yang mau diedit:");
    }
    if (key === "delete") {
      if (!requireAdmin(msg)) return;
      sessions.set(chatId, { action: "delete", step: "waiting_token" });
      return bot.sendMessage(chatId, "Kirim token lisensi yang mau dihapus:");
    }
    if (key === "device") {
      sessions.set(chatId, { action: "device", step: "waiting_device" });
      return bot.sendMessage(chatId, "Kirim Device ID yang mau dicek:");
    }
    if (key === "unbind") {
      if (!requireAdmin(msg)) return;
      sessions.set(chatId, { action: "unbind", step: "waiting_token" });
      return bot.sendMessage(chatId, "Kirim token lisensi yang device-nya mau direset:");
    }
    if (key === "list") {
      if (!requireAdmin(msg)) return;
      const tokens = await listAllTokens(firestore);
      if (tokens.length === 0) return bot.sendMessage(chatId, "Belum ada lisensi yang dibuat.");
      const preview = tokens.slice(0, 50);
      return bot.sendMessage(
        chatId,
        `📋 *Total ${tokens.length} lisensi*:\n\n` + preview.map((t) => `\`${t}\``).join("\n"),
        { parse_mode: "Markdown" }
      );
    }
    if (key === "maintenance") {
      if (!requireAdmin(msg)) return;
      return showMaintenanceMenu(chatId);
    }
    if (key === "update") {
      if (!requireAdmin(msg)) return;
      return showUpdateMenu(chatId);
    }
    return;
  }

  if (data.startsWith("delete_confirm:")) {
    if (!requireAdmin(msg)) return;
    const token = data.split(":")[1];
    try {
      const ok = await deleteLicense(firestore, token);
      clearSession(chatId);
      return bot.sendMessage(
        chatId,
        ok ? `🗑️ Token \`${token}\` berhasil dihapus.` : `❌ Gagal menghapus token \`${token}\`.`,
        { parse_mode: "Markdown" }
      );
    } catch (err) {
      console.error(err);
      return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
    }
  }
  if (data === "delete_cancel") {
    clearSession(chatId);
    return bot.sendMessage(chatId, "❎ Penghapusan dibatalkan.");
  }

  if (data.startsWith("edit_field:")) {
    if (!requireAdmin(msg)) return;
    const field = data.split(":")[1];
    const session = sessions.get(chatId);
    if (!session || session.action !== "edit") {
      return bot.sendMessage(chatId, "Sesi edit tidak ditemukan, mulai lagi dengan /edit <token>");
    }
    session.step = "awaiting_value";
    session.data.field = field;
    sessions.set(chatId, session);

    const prompts = {
      status: "Kirim status baru: `unused`, `active`, atau `revoked`",
      expiresAt: "Kirim jumlah hari masa berlaku baru dihitung dari SEKARANG (angka):",
      deviceId: "Kirim Device ID baru untuk dikunci ke lisensi ini, atau `-` untuk mengosongkan (reset ke unused):",
      note: "Kirim catatan baru:",
    };
    return bot.sendMessage(chatId, prompts[field] || "Kirim nilai baru:", { parse_mode: "Markdown" });
  }
  if (data === "edit_cancel") {
    clearSession(chatId);
    return bot.sendMessage(chatId, "❎ Proses edit dibatalkan.");
  }

  if (data === "maint:enable" || data === "maint:disable") {
    if (!requireAdmin(msg)) return;
    try {
      const status = await setMaintenanceStatus(firestore, { enabled: data === "maint:enable" });
      await bot.sendMessage(
        chatId,
        status.enabled
          ? "🔴 *Mode maintenance DIAKTIFKAN\\.* Seluruh aplikasi Android yang sedang terbuka akan langsung menampilkan dialog blocking dalam beberapa detik\\."
          : "🟢 *Mode maintenance DIMATIKAN\\.* Dialog blocking di aplikasi akan hilang otomatis\\.",
        { parse_mode: "MarkdownV2" }
      );
      return showMaintenanceMenu(chatId);
    } catch (err) {
      console.error(err);
      return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
    }
  }

  if (data === "maint:edit_title" || data === "maint:edit_message") {
    if (!requireAdmin(msg)) return;
    const field = data === "maint:edit_title" ? "title" : "message";
    sessions.set(chatId, { action: "maintenance", step: "awaiting_value", data: { field } });
    return bot.sendMessage(
      chatId,
      field === "title" ? "Kirim judul dialog maintenance yang baru:" : "Kirim pesan/deskripsi dialog maintenance yang baru:"
    );
  }

  if (data === "maint:close") {
    clearSession(chatId);
    return;
  }

  if (data === "upd:publish") {
    if (!requireAdmin(msg)) return;
    sessions.set(chatId, { action: "update_publish", step: "ask_version_code", data: {} });
    return bot.sendMessage(
      chatId,
      "Kirim *versionCode* rilis baru (angka bulat, harus lebih besar dari versionCode yang sudah terdaftar):",
      { parse_mode: "Markdown" }
    );
  }

  if (data === "upd:edit_url" || data === "upd:edit_desc") {
    if (!requireAdmin(msg)) return;
    const field = data === "upd:edit_url" ? "downloadUrl" : "description";
    sessions.set(chatId, { action: "update_field", step: "awaiting_value", data: { field } });
    return bot.sendMessage(
      chatId,
      field === "downloadUrl"
        ? "Kirim link download rilis (URL GitHub Release):"
        : "Kirim deskripsi/changelog baru:"
    );
  }

  if (data === "upd:mandatory" || data === "upd:optional") {
    if (!requireAdmin(msg)) return;
    try {
      const info = await setUpdateInfo(firestore, { mandatory: data === "upd:mandatory" });
      await bot.sendMessage(
        chatId,
        info.mandatory
          ? "🔴 *Update dijadikan WAJIB\\.* \\(catatan: saat ini app Android masih menampilkan dialog opsional — field ini disiapkan untuk versi mendatang\\.\\)"
          : "🟢 *Update dijadikan opsional kembali\\.*",
        { parse_mode: "MarkdownV2" }
      );
      return showUpdateMenu(chatId);
    } catch (err) {
      console.error(err);
      return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
    }
  }

  if (data === "upd:close") {
    clearSession(chatId);
    return;
  }
});

// ─────────────────────────────────────────────────────────
// MESSAGE HANDLER — melanjutkan conversation flow
// ─────────────────────────────────────────────────────────
bot.on("message", async (msg) => {
  if (!msg.text || msg.text.startsWith("/")) return;
  const chatId = msg.chat.id;
  const session = sessions.get(chatId);
  if (!session) return;

  const text = msg.text.trim();

  try {
    if (session.action === "check" && session.step === "waiting_token") {
      clearSession(chatId);
      return handleCheck(chatId, text);
    }

    if (session.action === "device" && session.step === "waiting_device") {
      clearSession(chatId);
      return handleDeviceCheck(chatId, text);
    }

    if (session.action === "unbind" && session.step === "waiting_token") {
      clearSession(chatId);
      return handleUnbind(chatId, text);
    }

    if (session.action === "delete" && session.step === "waiting_token") {
      return confirmDelete(chatId, text);
    }

    if (session.action === "edit" && session.step === "waiting_token") {
      return startEditFlow(chatId, text);
    }

    if (session.action === "edit" && session.step === "awaiting_value") {
      if (!requireAdmin(msg)) return;
      const { token, field } = session.data;
      const updates = {};

      if (field === "status") {
        if (!["unused", "active", "revoked"].includes(text)) {
          return bot.sendMessage(chatId, "⚠️ Status harus salah satu dari: unused, active, revoked");
        }
        updates.status = text;
      } else if (field === "expiresAt") {
        const days = parseInt(text, 10);
        if (!Number.isFinite(days) || days <= 0) {
          return bot.sendMessage(chatId, "⚠️ Masukkan angka hari yang valid, lebih dari 0.");
        }
        updates.expiresAt = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
      } else if (field === "deviceId") {
        if (text === "-") {
          await unbindDevice(firestore, token);
          clearSession(chatId);
          const updated = await getLicense(firestore, token);
          return bot.sendMessage(chatId, `✅ Device dilepas\\!\n\n${formatLicenseCard(updated)}`, {
            parse_mode: "MarkdownV2",
          });
        }
        if (!isValidDeviceId(text)) {
          return bot.sendMessage(chatId, "⚠️ Device ID tidak valid (minimal 4 karakter).");
        }
        updates.deviceId = text;
        updates.status = "active";
        updates.activatedAt = new Date();
      } else if (field === "note") {
        updates.note = text;
      }

      if (Object.keys(updates).length > 0) {
        await updateLicense(firestore, token, updates);
      }

      clearSession(chatId);
      const updated = await getLicense(firestore, token);
      return bot.sendMessage(chatId, `✅ Lisensi berhasil diupdate\\!\n\n${formatLicenseCard(updated)}`, {
        parse_mode: "MarkdownV2",
      });
    }

    if (session.action === "maintenance" && session.step === "awaiting_value") {
      if (!requireAdmin(msg)) return;
      const { field } = session.data;
      clearSession(chatId);
      try {
        const status = await setMaintenanceStatus(firestore, { [field]: text });
        await bot.sendMessage(
          chatId,
          `✅ ${field === "title" ? "Judul" : "Pesan"} maintenance berhasil diupdate\\!`,
          { parse_mode: "MarkdownV2" }
        );
        return showMaintenanceMenu(chatId);
      } catch (err) {
        console.error(err);
        return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
      }
    }

    if (session.action === "update_field" && session.step === "awaiting_value") {
      if (!requireAdmin(msg)) return;
      const { field } = session.data;
      clearSession(chatId);
      try {
        await setUpdateInfo(firestore, { [field]: text });
        await bot.sendMessage(
          chatId,
          `✅ ${field === "downloadUrl" ? "Link download" : "Deskripsi"} berhasil diupdate\\!`,
          { parse_mode: "MarkdownV2" }
        );
        return showUpdateMenu(chatId);
      } catch (err) {
        console.error(err);
        return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
      }
    }

    if (session.action === "update_publish" && session.step === "ask_version_code") {
      if (!requireAdmin(msg)) return;
      const versionCode = parseInt(text, 10);
      if (!Number.isFinite(versionCode) || versionCode <= 0) {
        return bot.sendMessage(chatId, "⚠️ Masukkan versionCode berupa angka bulat positif.");
      }
      const current = await getUpdateInfo(firestore);
      if (versionCode <= current.latestVersionCode) {
        return bot.sendMessage(
          chatId,
          `⚠️ versionCode harus lebih besar dari yang sudah terdaftar saat ini (${current.latestVersionCode}). Kirim ulang versionCode yang valid, atau /cancel untuk batal.`
        );
      }
      session.data.latestVersionCode = versionCode;
      session.step = "ask_version_name";
      sessions.set(chatId, session);
      return bot.sendMessage(chatId, "Kirim *versionName* rilis ini (mis. `1.2.0` atau `1.2 Beta`):", {
        parse_mode: "Markdown",
      });
    }
    if (session.action === "update_publish" && session.step === "ask_version_name") {
      if (!requireAdmin(msg)) return;
      session.data.latestVersionName = text;
      session.step = "ask_download_url";
      sessions.set(chatId, session);
      return bot.sendMessage(chatId, "Kirim link download (URL GitHub Release) untuk rilis ini:");
    }
    if (session.action === "update_publish" && session.step === "ask_download_url") {
      if (!requireAdmin(msg)) return;
      session.data.downloadUrl = text;
      session.step = "ask_description";
      sessions.set(chatId, session);
      return bot.sendMessage(
        chatId,
        [
          "Kirim deskripsi/changelog rilis ini (boleh multi-baris), atau `-` untuk kosongkan.",
          "",
          "Format yang didukung di app:",
          "• `**tebal**` dan `*miring*`",
          "• `- item` di awal baris jadi bullet point",
          "• `[blue]teks[/blue]` teks berwarna — pilihan: blue, green, amber, red",
        ].join("\n"),
        { parse_mode: "Markdown" }
      );
    }
    if (session.action === "update_publish" && session.step === "ask_description") {
      if (!requireAdmin(msg)) return;
      const description = text === "-" ? "" : text;
      const { latestVersionCode, latestVersionName, downloadUrl } = session.data;
      clearSession(chatId);
      try {
        const info = await setUpdateInfo(firestore, {
          latestVersionCode,
          latestVersionName,
          downloadUrl,
          description,
        });
        await bot.sendMessage(
          chatId,
          `✅ Versi baru berhasil dipublish\\! Semua aplikasi Android yang terbuka akan menampilkan dialog update dalam beberapa saat\\.`,
          { parse_mode: "MarkdownV2" }
        );
        return bot.sendMessage(chatId, formatUpdateCard(info), {
          parse_mode: "MarkdownV2",
          ...updateMenuKeyboard(info),
        });
      } catch (err) {
        console.error(err);
        return bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
      }
    }

    if (session.action === "generate" && session.step === "ask_days") {
      const days = parseInt(text, 10);
      if (!Number.isFinite(days) || days <= 0) {
        return bot.sendMessage(chatId, "⚠️ Masukkan angka hari yang valid, lebih dari 0.");
      }
      session.data = { days };
      session.step = "ask_note";
      sessions.set(chatId, session);
      return bot.sendMessage(chatId, "Catatan untuk lisensi ini? (opsional, kirim `-` untuk kosongkan)", {
        parse_mode: "Markdown",
      });
    }
    if (session.action === "generate" && session.step === "ask_note") {
      const note = text === "-" ? "" : text;
      clearSession(chatId);
      return doGenerate(chatId, session.data.days, note);
    }
  } catch (err) {
    console.error(err);
    clearSession(chatId);
    bot.sendMessage(chatId, `❌ Terjadi error: ${err.message}`);
  }
});

bot.on("polling_error", (err) => {
  console.error("Polling error:", err.message);
});

console.log("🤖 Bot AetherX License Manager (Firestore) berjalan (polling mode)...");
