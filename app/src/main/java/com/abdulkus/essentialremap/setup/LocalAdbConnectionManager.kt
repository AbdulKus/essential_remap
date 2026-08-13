package com.abdulkus.essentialremap.setup

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class LocalAdbConnectionManager(context: Context) : AbsAdbConnectionManager() {
    private val identity = EncryptedAdbIdentityStore(context.applicationContext).loadOrCreate()

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = identity.privateKey

    override fun getCertificate(): Certificate = identity.certificate

    override fun getDeviceName(): String = "Essential Remap"
}

internal data class AdbIdentity(
    val privateKey: PrivateKey,
    val certificate: Certificate,
)

/**
 * Conscrypt cannot use an AndroidKeyStore-backed RSA key for the ADB TLS handshake on
 * every OEM build. Keep the TLS key exportable, but encrypt it at rest with an AES key
 * that never leaves AndroidKeyStore.
 */
internal class EncryptedAdbIdentityStore(private val context: Context) {
    private val identityFile = context.filesDir.resolve(IDENTITY_FILE)

    fun loadOrCreate(): AdbIdentity {
        runCatching { read() }.getOrNull()?.let { return it }
        identityFile.delete()
        return create().also(::write)
    }

    private fun create(): AdbIdentity {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()
        val now = Date()
        val expires = Date(now.time + TWENTY_YEARS_MS)
        val subject = X500Name("CN=Essential Remap")
        val provider = BouncyCastleProvider()
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(provider)
            .build(keyPair.private)
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            now,
            expires,
            subject,
            keyPair.public,
        ).build(signer)
        val certificate = JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(holder)
        certificate.verify(keyPair.public)
        return AdbIdentity(keyPair.private, certificate)
    }

    private fun write(identity: AdbIdentity) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val encryptedPrivateKey = cipher.doFinal(identity.privateKey.encoded)
        DataOutputStream(identityFile.outputStream().buffered()).use { output ->
            output.writeInt(FILE_VERSION)
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.writeInt(encryptedPrivateKey.size)
            output.write(encryptedPrivateKey)
            val encodedCertificate = identity.certificate.encoded
            output.writeInt(encodedCertificate.size)
            output.write(encodedCertificate)
        }
    }

    private fun read(): AdbIdentity? {
        if (!identityFile.exists()) return null
        return DataInputStream(identityFile.inputStream().buffered()).use { input ->
            if (input.readInt() != FILE_VERSION) return null
            val iv = input.readSized(MAX_IV_BYTES)
            val encryptedPrivateKey = input.readSized(MAX_KEY_BYTES)
            val encodedCertificate = input.readSized(MAX_CERTIFICATE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
            val privateKeyBytes = cipher.doFinal(encryptedPrivateKey)
            val privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(encodedCertificate.inputStream())
            AdbIdentity(privateKey, certificate)
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun DataInputStream.readSized(maximum: Int): ByteArray {
        val size = readInt()
        require(size in 1..maximum) { "Invalid ADB identity file" }
        return ByteArray(size).also(::readFully)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val WRAPPING_KEY_ALIAS = "essential_key_remapper_adb_wrapping_key"
        const val IDENTITY_FILE = "adb_identity_v2.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_VERSION = 2
        const val MAX_IV_BYTES = 64
        const val MAX_KEY_BYTES = 16_384
        const val MAX_CERTIFICATE_BYTES = 16_384
        const val TWENTY_YEARS_MS = 20L * 365 * 24 * 60 * 60 * 1000
    }
}
