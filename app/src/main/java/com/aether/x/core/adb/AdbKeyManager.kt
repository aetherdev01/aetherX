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

class AdbKeyManager(context: Context) {

    private val keyDir = File(context.filesDir, "adb_key").apply { mkdirs() }
    private val privateKeyFile = File(keyDir, "aetherx_adb_private.key")
    private val certificateFile = File(keyDir, "aetherx_adb_cert.pem")

    data class AdbIdentity(val privateKey: PrivateKey, val certificate: Certificate)

    @Synchronized
    fun getOrCreateIdentity(): AdbIdentity {
        if (privateKeyFile.exists() && certificateFile.exists()) {
            val loaded = runCatching { loadIdentity() }.getOrNull()
            if (loaded != null) return loaded

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

        val keySize = 2048
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
        val generatedKeyPair = keyPairGenerator.generateKeyPair()
        val publicKey: PublicKey = generatedKeyPair.public
        val privateKey: PrivateKey = generatedKeyPair.private

        val subject = "CN=AetherX"
        val algorithmName = "SHA512withRSA"

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

    fun forgetIdentity() {
        privateKeyFile.delete()
        certificateFile.delete()
    }

    fun hasIdentity(): Boolean = privateKeyFile.exists() && certificateFile.exists()
}
