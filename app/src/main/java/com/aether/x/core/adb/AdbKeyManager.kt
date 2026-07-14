package com.aether.x.core.adb

import android.content.Context
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * REWORK TOTAL PERMISSION — ADB tertanam (lihat perintah rework "buatkan
 * sistem seperti shizuku langsung tertanam dalam aplikasinya... hapus
 * semua yang bersangkutan dengan shizuku").
 *
 * KOREKSI ARSITEKTUR (setelah dua percobaan sebelumnya gagal): "dadb"
 * (dev.mobile:dadb) TIDAK punya wireless pairing sama sekali, dan menulis
 * protokol pairing (SPAKE2 + TLS) sendiri dari nol terlalu berisiko tanpa
 * bisa diverifikasi/dites di sini. Sekarang memakai **libadb-android**
 * (io.github.muntashirakon.adb, oleh pembuat App Manager) yang SUDAH
 * mengimplementasikan wireless pairing Android 11+ secara lengkap & teruji
 * di production — lihat [AdbConnectionManager] untuk pemakaiannya.
 *
 * Pengelola keypair RSA 2048-bit + sertifikat X.509 self-signed yang
 * dipakai AetherX untuk mengautentikasi dirinya sendiri ke `adbd` — persis
 * seperti keypair `~/.android/adbkey`/`adbkey.pub` yang dipakai
 * command-line `adb` di komputer. Disimpan sekali di penyimpanan internal
 * privat app (`filesDir`, tidak bisa diakses app lain tanpa root) dan
 * dipakai ulang selamanya — INILAH kunci utama kenapa pairing "tidak
 * gampang ter-reset": begitu satu kali di-pair, adbd di perangkat
 * mengingat PUBLIC KEY/certificate ini secara permanen, sehingga sesi ADB
 * berikutnya tidak perlu pairing ulang — hanya connect biasa.
 *
 * Generate certificate X.509 di Android BUKAN hal sepele — Android
 * standar TIDAK expose `sun.security.x509` (dipakai command-line `adb`
 * yang jalan di JVM penuh) — karena itu dipakai dependency
 * `com.github.MuntashirAkon:sun-security-android`, port dari OpenJDK
 * `sun.security.x509` khusus Android, PERSIS seperti dicontohkan resmi
 * di README libadb-android.
 */
class AdbKeyManager(context: Context) {

    private val keyDir = File(context.filesDir, "adb_key").apply { mkdirs() }
    private val privateKeyFile = File(keyDir, "aetherx_adb_private.key")
    private val certificateFile = File(keyDir, "aetherx_adb_cert.pem")

    data class AdbIdentity(val privateKey: PrivateKey, val certificate: Certificate)

    /**
     * Ambil keypair+certificate yang sudah tersimpan, atau bikin baru
     * sekali kalau ini pertama kalinya AetherX dipasang/dijalankan.
     * Identitas yang sama ini dipakai TERUS-MENERUS untuk semua percobaan
     * pairing & connect berikutnya — mengganti keypair akan membuat adbd
     * menganggap AetherX sebagai klien baru yang belum dikenal, sehingga
     * wajib pairing ulang. Karena itu SENGAJA tidak pernah di-regenerate
     * otomatis oleh AetherX sendiri.
     */
    @Synchronized
    fun getOrCreateIdentity(): AdbIdentity {
        if (privateKeyFile.exists() && certificateFile.exists()) {
            val loaded = runCatching { loadIdentity() }.getOrNull()
            if (loaded != null) return loaded
            // Berkas korup (mis. penyimpanan penuh saat menulis
            // sebelumnya) — regenerasi adalah satu-satunya pilihan aman,
            // meski konsekuensinya pengguna wajib pairing ulang sekali lagi.
        }
        return generateAndSaveIdentity()
    }

    private fun loadIdentity(): AdbIdentity {
        val privateKeyBytes = privateKeyFile.readBytes()
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

        val certificate = certificateFile.inputStream().use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input)
        }
        return AdbIdentity(privateKey, certificate)
    }

    private fun generateAndSaveIdentity(): AdbIdentity {
        // Persis mengikuti contoh resmi README libadb-android (lihat
        // https://github.com/MuntashirAkon/libadb-android) — generate
        // RSA 2048-bit, lalu bungkus jadi X509CertImpl self-signed lewat
        // sun-security-android karena Android standar tidak punya API ini.
        val keySize = 2048
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
        val generatedKeyPair = keyPairGenerator.generateKeyPair()
        val publicKey: PublicKey = generatedKeyPair.public
        val privateKey: PrivateKey = generatedKeyPair.private

        val subject = "CN=AetherX"
        val algorithmName = "SHA512withRSA"
        // Sertifikat berumur sangat panjang (~27 tahun) — BUKAN sertifikat
        // sesi sekali pakai. Ini penting: kalau umurnya pendek, adbd akan
        // menolak koneksi setelah sertifikat kedaluwarsa walau key pair-nya
        // masih sama persis, memaksa pairing ulang padahal seharusnya
        // tidak perlu — bertentangan dengan tujuan "tidak gampang
        // ter-reset" yang diminta.
        val expiryDate = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 27

        val certificateExtensions = CertificateExtensions()
        certificateExtensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier),
        )
        val x500Name = X500Name(subject)
        val notBefore = Date()
        val notAfter = Date(expiryDate)
        certificateExtensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
        val certificateValidity = CertificateValidity(notBefore, notAfter)

        val x509CertInfo = X509CertInfo()
        x509CertInfo.set("version", CertificateVersion(2))
        x509CertInfo.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Integer.MAX_VALUE))
        x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        x509CertInfo.set("subject", CertificateSubjectName(x500Name))
        x509CertInfo.set("key", CertificateX509Key(publicKey))
        x509CertInfo.set("validity", certificateValidity)
        x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
        x509CertInfo.set("extensions", certificateExtensions)

        val x509CertImpl = X509CertImpl(x509CertInfo)
        x509CertImpl.sign(privateKey, algorithmName)

        privateKeyFile.writeBytes(privateKey.encoded)
        certificateFile.outputStream().use { output ->
            output.write(x509CertImpl.encoded)
        }

        return AdbIdentity(privateKey, x509CertImpl)
    }

    /**
     * Hapus keypair+certificate secara eksplisit — dipanggil HANYA oleh
     * aksi pengguna "Lupakan perangkat ini" di layar Izin Akses, BUKAN
     * dipanggil otomatis oleh alur mana pun. Setelah ini, pairing wireless
     * debugging wajib diulang dari awal karena adbd di perangkat masih
     * mengingat certificate LAMA yang sudah tidak lagi cocok dengan
     * private key yang baru akan dibuat.
     */
    fun forgetIdentity() {
        privateKeyFile.delete()
        certificateFile.delete()
    }

    fun hasIdentity(): Boolean = privateKeyFile.exists() && certificateFile.exists()
}
