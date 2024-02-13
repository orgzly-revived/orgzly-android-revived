package com.orgzly.android.git

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.SshKeygenActivity
import com.orgzly.android.util.BiometricAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntry.parsePublicKeyEntry
import java.io.File
import java.io.IOException
import java.security.*
import java.security.interfaces.RSAKey

private const val PROVIDER_ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "orgzly_sshkey"
private const val ANDROIDX_SECURITY_KEYSET_PREF_NAME = "orgzly_sshkey_keyset_prefs"
private const val AUTH_VALIDITY_DURATION = 30

/** Alias to [lazy] with thread safety mode always set to [LazyThreadSafetyMode.NONE]. */
private fun <T> unsafeLazy(initializer: () -> T) = lazy(LazyThreadSafetyMode.NONE) { initializer.invoke() }

private val androidKeystore: KeyStore by unsafeLazy {
    KeyStore.getInstance(PROVIDER_ANDROID_KEY_STORE).apply { load(null) }
}

private val KeyStore.sshPrivateKey
    get() = getKey(KEYSTORE_ALIAS, null) as? PrivateKey

private val KeyStore.sshPublicKey
    get() = getCertificate(KEYSTORE_ALIAS)?.publicKey

fun parseSshPublicKey(sshPublicKey: String): PublicKey? {
    return parsePublicKeyEntry(sshPublicKey).resolvePublicKey(null, null, null)
}

fun toSshPublicKey(publicKey: PublicKey): String {
    return PublicKeyEntry.toString(publicKey)
}

@RequiresApi(Build.VERSION_CODES.N)
object SshKey {
    val sshPublicKey
        get() = if (publicKeyFile.exists()) publicKeyFile.readText() else null
    val canShowSshPublicKey
        get() = type in listOf(Type.KeystoreNative, Type.KeystoreWrappedEd25519)
    val exists
        get() = type != null
    private val context: Context
        get() = App.getAppContext()
    private val privateKeyFile
        get() = File(context.filesDir, "ssh_key")
    private val publicKeyFile
        get() = File(context.filesDir, "ssh_key.pub")
    private var type: Type?
        get() = Type.fromValue(AppPreferences.gitSshKeyType(context))
        set(value) = AppPreferences.gitSshKeyType(context, value?.value)
    private val isStrongBoxSupported by unsafeLazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        else false
    }
    private var privateKeyLoadAttempts = 0

    private enum class Type(val value: String) {
        KeystoreNative("keystore_native"),
        KeystoreWrappedEd25519("keystore_wrapped_ed25519"),
        ;

        companion object {
            fun fromValue(value: String?): Type? = values().associateBy { it.value }[value]
        }
    }

    enum class Algorithm(
        val algorithm: String,
        val applyToSpec: KeyGenParameterSpec.Builder.() -> Unit
    ) {
        Rsa(
            KeyProperties.KEY_ALGORITHM_RSA,
            {
                setKeySize(3072)
                setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                setDigests(
                    KeyProperties.DIGEST_SHA1,
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )
            }
        ),

        Ecdsa(
            KeyProperties.KEY_ALGORITHM_EC,
            {
                setKeySize(256)
                setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                setDigests(KeyProperties.DIGEST_SHA256)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(isStrongBoxSupported)
                }
            }
        ),
    }

    private fun delete() {
        androidKeystore.deleteEntry(KEYSTORE_ALIAS)
        // Remove Tink key set used by AndroidX's EncryptedFile.
        context.getSharedPreferences(ANDROIDX_SECURITY_KEYSET_PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                clear()
            }
        if (privateKeyFile.isFile) {
            privateKeyFile.delete()
        }
        if (publicKeyFile.isFile) {
            publicKeyFile.delete()
        }
        type = null
    }

    private suspend fun getOrCreateWrappingMasterKey(requireAuthentication: Boolean) =
        withContext(Dispatchers.IO) {
            MasterKey.Builder(context, KEYSTORE_ALIAS).run {
                setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                setRequestStrongBoxBacked(true)
                setUserAuthenticationRequired(requireAuthentication, AUTH_VALIDITY_DURATION)
                build()
            }
        }

    private suspend fun getOrCreateWrappedPrivateKeyFile(requireAuthentication: Boolean) =
        withContext(Dispatchers.IO) {
            EncryptedFile.Builder(
                context,
                privateKeyFile,
                getOrCreateWrappingMasterKey(requireAuthentication),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            )
            .run {
                setKeysetPrefName(ANDROIDX_SECURITY_KEYSET_PREF_NAME)
                build()
            }
        }


    suspend fun generateKeystoreWrappedEd25519Key(requireAuthentication: Boolean) =
        withContext(Dispatchers.IO) {
            delete()

            val encryptedPrivateKeyFile = getOrCreateWrappedPrivateKeyFile(requireAuthentication)
            // Generate the ed25519 key pair and encrypt the private key.
            val keyPair = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()
            encryptedPrivateKeyFile.openFileOutput().use { os ->
                os.write((keyPair.private as EdDSAPrivateKey).seed)
            }

            // Write public key in SSH format to .ssh_key.pub.
            publicKeyFile.writeText(toSshPublicKey(keyPair.public))

            type = Type.KeystoreWrappedEd25519
        }

    fun generateKeystoreNativeKey(algorithm: Algorithm, requireAuthentication: Boolean) {
        delete()

        // Generate Keystore-backed private key.
        val parameterSpec =
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN).run {
                apply(algorithm.applyToSpec)
                if (requireAuthentication) {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(AUTH_VALIDITY_DURATION, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    } else {
                        @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(
                            AUTH_VALIDITY_DURATION)
                    }
                }
                build()
            }
        val keyPair =
            KeyPairGenerator.getInstance(algorithm.algorithm, PROVIDER_ANDROID_KEY_STORE).run {
                initialize(parameterSpec)
                generateKeyPair()
            }

        // Write public key in SSH format to ssh_key.pub
        publicKeyFile.writeText(toSshPublicKey(keyPair.public))

        type = Type.KeystoreNative
    }

    fun getKeyPair(): KeyPair {
        privateKeyLoadAttempts = 0
        var privateKey: PrivateKey? = null
        val publicKey: PublicKey? = when (type) {
            Type.KeystoreNative -> {
                kotlin.runCatching { androidKeystore.sshPublicKey }
                    .getOrElse { error ->
                        throw IOException(
                            context.getString(R.string.ssh_key_failed_get_public),
                            error
                        )
                    }
            }
            Type.KeystoreWrappedEd25519 -> {
                runCatching { parseSshPublicKey(sshPublicKey!!) }
                    .getOrElse { error ->
                        throw IOException(context.getString(R.string.ssh_key_failed_get_public), error)
                    }
            }
            else -> throw IllegalStateException("SSH key does not exist in Keystore")
        }
        while (privateKeyLoadAttempts < 2) {
            privateKey = when (type) {
                Type.KeystoreNative -> {
                    runCatching { androidKeystore.sshPrivateKey }
                        .getOrElse { error ->
                            throw IOException(
                                context.getString(R.string.ssh_key_failed_get_private),
                                error
                            )
                        }
                }
                Type.KeystoreWrappedEd25519 -> {
                    try {
                        tryToReadEd25519PrivateKey()
                    } catch (e: UserNotAuthenticatedException) {
                        tryBiometricAuthOrFail(e)
                        tryToReadEd25519PrivateKey()
                    } catch (e: Exception) {
                        throw IOException(context.getString(R.string.ssh_key_failed_get_private), e)
                    }
                }
                else -> throw IllegalStateException("SSH key does not exist in Keystore")
            }
            try {
                // Try to sign something to see if the key is unlocked
                val signature = when (privateKey) {
                    is EdDSAPrivateKey -> EdDSAEngine(MessageDigest.getInstance(
                        EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519).hashAlgorithm))
                    is RSAKey -> Signature.getInstance("SHA256withRSA")
                    else -> Signature.getInstance("SHA256withECDSA")
                }
                signature.apply {
                    initSign(privateKey)
                    update("loremipsum".toByteArray())
                }.sign()
                // The key is unlocked; exit the loop.
                break
            } catch (e: UserNotAuthenticatedException) {
                tryBiometricAuthOrFail(e)
            } catch (e: Exception) {
                throw e
            }
        }
        return KeyPair(publicKey, privateKey)
    }

    private fun tryToReadEd25519PrivateKey(): EdDSAPrivateKey {
        // The current MasterKey API does not allow getting a reference to an existing
        // one without specifying the KeySpec for a new one. However, the value for
        // passed here for `requireAuthentication` is not used as the key already exists
        // at this point.
        val encryptedPrivateKeyFile = runBlocking {
            getOrCreateWrappedPrivateKeyFile(false)
        }
        val rawPrivateKey =
            encryptedPrivateKeyFile.openFileInput().use { it.readBytes() }
        return EdDSAPrivateKey(
            EdDSAPrivateKeySpec(
                rawPrivateKey, EdDSANamedCurveTable.ED_25519_CURVE_SPEC
            )
        )
    }

    private fun tryBiometricAuthOrFail(e: UserNotAuthenticatedException) {
        if (privateKeyLoadAttempts == 0) {
            val currentActivity = App.getCurrentActivity()
            checkNotNull(currentActivity) {
                throw IOException(context.getString(R.string.ssh_key_locked_and_no_activity))
            }
            val biometricAuthenticator = BiometricAuthenticator(currentActivity)
            runBlocking(Dispatchers.Main) {
                biometricAuthenticator.authenticate(
                    context.getString(
                        R.string.biometric_prompt_title_unlock_ssh_key
                    )
                )
            }
            privateKeyLoadAttempts++
        } else {
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun promptForKeyGeneration() {
        val activity = App.getCurrentActivity()
        if (activity != null) {
            val builder = MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.git_ssh_on_missing_key_dialog_text)
                .setTitle(R.string.git_ssh_on_missing_key_dialog_title)
            builder.setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                val intent =
                    Intent(activity.applicationContext, SshKeygenActivity::class.java)
                activity.startActivity(intent)
            }
            builder.setNegativeButton(activity.getString(R.string.not_now)) {
                    dialog, _ -> dialog.dismiss()
            }
            runBlocking { activity.alertDialog = builder.show() }
        }
    }

}
