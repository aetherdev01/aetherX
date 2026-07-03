const COLLECTION = "config";
const DOC_ID = "update";

/**
 * Skema dokumen `config/update` — HARUS sama persis dengan yang dibaca
 * UpdateRepository.kt di aplikasi Android:
 * {
 *   latestVersionCode: number (integer),
 *   latestVersionName: string,
 *   description: string,       // changelog, ditampilkan apa adanya di dialog
 *   downloadUrl: string,       // link GitHub Release (atau APK langsung)
 *   mandatory: boolean,        // disiapkan untuk masa depan, saat ini app selalu opsional
 *   updatedAt: Date,
 * }
 *
 * Dokumen ini SATU-SATUNYA per aplikasi (bukan per-versi/riwayat) — tiap
 * kali admin publish rilis baru lewat bot, dokumen ini ditimpa dengan info
 * versi terbaru. App Android membandingkan `latestVersionCode` dengan
 * versionCode lokalnya sendiri; kalau lebih besar, dialog update muncul.
 */

const DEFAULT_DESCRIPTION = "";
const DEFAULT_DOWNLOAD_URL = "";

async function getUpdateInfo(firestore) {
  const data = await firestore.getDocument(COLLECTION, DOC_ID);
  if (!data) {
    return {
      latestVersionCode: 0,
      latestVersionName: "",
      description: DEFAULT_DESCRIPTION,
      downloadUrl: DEFAULT_DOWNLOAD_URL,
      mandatory: false,
      updatedAt: null,
    };
  }
  return {
    latestVersionCode: Number.isFinite(data.latestVersionCode) ? data.latestVersionCode : 0,
    latestVersionName: data.latestVersionName || "",
    description: data.description || DEFAULT_DESCRIPTION,
    downloadUrl: data.downloadUrl || DEFAULT_DOWNLOAD_URL,
    mandatory: !!data.mandatory,
    updatedAt: data.updatedAt || null,
  };
}

/**
 * Membuat ATAU meng-update dokumen `config/update`. Sama seperti
 * maintenanceStore — satu fungsi untuk keduanya karena dokumennya tunggal.
 * Field yang tidak disebutkan di `fields` akan tetap memakai nilai lama
 * (atau default kalau dokumen belum pernah ada).
 */
async function setUpdateInfo(firestore, fields) {
  const existing = await firestore.getDocument(COLLECTION, DOC_ID);
  const data = {
    latestVersionCode:
      fields.latestVersionCode !== undefined ? fields.latestVersionCode : existing?.latestVersionCode || 0,
    latestVersionName:
      fields.latestVersionName !== undefined ? fields.latestVersionName : existing?.latestVersionName || "",
    description: fields.description !== undefined ? fields.description : existing?.description || DEFAULT_DESCRIPTION,
    downloadUrl: fields.downloadUrl !== undefined ? fields.downloadUrl : existing?.downloadUrl || DEFAULT_DOWNLOAD_URL,
    mandatory: fields.mandatory !== undefined ? !!fields.mandatory : !!existing?.mandatory,
    updatedAt: new Date(),
  };

  if (!existing) {
    await firestore.createDocument(COLLECTION, DOC_ID, data);
  } else {
    await firestore.updateDocument(COLLECTION, DOC_ID, data);
  }
  return getUpdateInfo(firestore);
}

module.exports = {
  getUpdateInfo,
  setUpdateInfo,
};
