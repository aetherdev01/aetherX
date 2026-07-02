const COLLECTION = "config";
const DOC_ID = "maintenance";

/**
 * Skema dokumen `config/maintenance` — HARUS sama persis dengan yang dibaca
 * MaintenanceRepository.kt di aplikasi Android:
 * {
 *   enabled: boolean,
 *   title: string,
 *   message: string,
 *   updatedAt: Date,
 * }
 */

const DEFAULT_TITLE = "Sedang Pemeliharaan";
const DEFAULT_MESSAGE =
  "Aplikasi sedang dalam pemeliharaan sementara. Silakan hubungi admin untuk informasi lebih lanjut.";

async function getMaintenanceStatus(firestore) {
  const data = await firestore.getDocument(COLLECTION, DOC_ID);
  if (!data) {
    return { enabled: false, title: DEFAULT_TITLE, message: DEFAULT_MESSAGE, updatedAt: null };
  }
  return {
    enabled: !!data.enabled,
    title: data.title || DEFAULT_TITLE,
    message: data.message || DEFAULT_MESSAGE,
    updatedAt: data.updatedAt || null,
  };
}

/**
 * Membuat ATAU meng-update dokumen `config/maintenance`. Dipakai satu fungsi
 * untuk keduanya (bukan createDocument/updateDocument terpisah seperti
 * lisensi) karena dokumen ini SATU-SATUNYA per aplikasi (bukan per-token),
 * jadi tidak ada risiko "sudah ada tapi coba create lagi" yang perlu ditolak
 * secara sengaja — kalau belum ada, buat; kalau sudah ada, timpa field yang
 * relevan.
 */
async function setMaintenanceStatus(firestore, { enabled, title, message }) {
  const existing = await firestore.getDocument(COLLECTION, DOC_ID);
  const data = {
    enabled: !!enabled,
    title: title !== undefined ? title : existing?.title || DEFAULT_TITLE,
    message: message !== undefined ? message : existing?.message || DEFAULT_MESSAGE,
    updatedAt: new Date(),
  };

  if (!existing) {
    await firestore.createDocument(COLLECTION, DOC_ID, data);
  } else {
    await firestore.updateDocument(COLLECTION, DOC_ID, data);
  }
  return getMaintenanceStatus(firestore);
}

module.exports = {
  DEFAULT_TITLE,
  DEFAULT_MESSAGE,
  getMaintenanceStatus,
  setMaintenanceStatus,
};
