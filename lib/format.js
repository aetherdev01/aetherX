function escapeMd(text) {
  return String(text).replace(/[_*[\]()~`>#+\-=|{}.!]/g, "\\$&");
}

function formatDate(value) {
  if (!value) return "—";
  const d = value instanceof Date ? value : new Date(value);
  return (
    d.toLocaleString("id-ID", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "Asia/Jakarta",
    }) + " WIB"
  );
}

function statusEmoji(status) {
  switch (status) {
    case "unused":
      return "⚪ Belum dipakai";
    case "active":
      return "🟢 Aktif";
    case "revoked":
      return "🔴 Dicabut";
    default:
      return status;
  }
}

function isExpired(license) {
  if (!license.expiresAt) return false;
  const expiresAt = license.expiresAt instanceof Date ? license.expiresAt : new Date(license.expiresAt);
  return expiresAt.getTime() < Date.now();
}

function formatLicenseCard(license) {
  const expired = isExpired(license);
  const lines = [
    `🔑 *Token:* \`${escapeMd(license.token)}\``,
    `📌 *Status:* ${statusEmoji(license.status)}${expired && license.status !== "revoked" ? " \\(KADALUARSA\\)" : ""}`,
    `📱 *Device terkunci:* ${license.deviceId ? "`" + escapeMd(license.deviceId) + "`" : "belum ada"}`,
    `🗓️ *Dibuat:* ${escapeMd(formatDate(license.createdAt))}`,
    `⏳ *Kadaluarsa:* ${escapeMd(formatDate(license.expiresAt))}`,
    `✅ *Diaktivasi:* ${license.activatedAt ? escapeMd(formatDate(license.activatedAt)) : "belum diaktivasi"}`,
  ];
  if (license.note) {
    lines.push(`📝 *Catatan:* ${escapeMd(license.note)}`);
  }
  return lines.join("\n");
}

function isValidToken(token) {
  return /^[a-zA-Z0-9]{7}$/.test(token);
}

function isValidDeviceId(deviceId) {
  return typeof deviceId === "string" && deviceId.length >= 4 && deviceId.length <= 128;
}

function formatMaintenanceCard(status) {
  const lines = [
    `🛠️ *Status Maintenance:* ${status.enabled ? "🔴 AKTIF \\(dialog blocking tampil di app\\)" : "🟢 Nonaktif"}`,
    `📌 *Judul:* ${escapeMd(status.title)}`,
    `📝 *Pesan:* ${escapeMd(status.message)}`,
    `🕒 *Terakhir diubah:* ${escapeMd(formatDate(status.updatedAt))}`,
  ];
  return lines.join("\n");
}

function formatUpdateCard(info) {
  const hasVersion = info.latestVersionCode > 0;
  const lines = [
    `🚀 *Versi terbaru terdaftar:* ${hasVersion ? escapeMd(String(info.latestVersionCode)) : "belum diatur"}`,
    `🏷️ *Nama versi:* ${info.latestVersionName ? escapeMd(info.latestVersionName) : "\\-"}`,
    `🔗 *Link download:* ${info.downloadUrl ? escapeMd(info.downloadUrl) : "belum diisi"}`,
    `📌 *Wajib update:* ${info.mandatory ? "🔴 Ya" : "🟢 Tidak \\(opsional\\)"}`,
    `🕒 *Terakhir dipublish:* ${escapeMd(formatDate(info.updatedAt))}`,
    "",
    `📝 *Deskripsi/changelog:*`,
    info.description ? escapeMd(info.description) : "_\\(kosong\\)_",
  ];
  return lines.join("\n");
}

module.exports = { escapeMd, formatDate, statusEmoji, isExpired, formatLicenseCard, formatMaintenanceCard, formatUpdateCard, isValidToken, isValidDeviceId };
