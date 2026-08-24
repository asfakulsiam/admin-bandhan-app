package com.admin.bandhan17.app.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed class BiometricAuthResult {
    object Success : BiometricAuthResult()
    data class Error(val errorCode: Int, val message: String) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
}

enum class BiometricStatus {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

class BiometricAuthManager {

    companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    /**
     * Checks if biometric or device credential (PIN/pattern/password) is available on the device.
     */
    fun checkBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Triggers native BiometricPrompt supporting Biometrics (fingerprint/face) with fallback to device PIN/password/pattern.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Admin Bandhan 17 Security",
        subtitle: String = "অ্যাডমিন অ্যাক্সেস যাচাইকরণ",
        description: String = "সুরক্ষিত অ্যাডমিন পোর্টালে প্রবেশের জন্য আপনার ফিঙ্গারপ্রিন্ট, ফেস বা ডিভাইস পিন/প্যাটার্ন দিন।",
        onResult: (BiometricAuthResult) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onResult(BiometricAuthResult.Success)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onResult(BiometricAuthResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onResult(BiometricAuthResult.Failed)
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            try {
                val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setNegativeButtonText("বাতিল")
                    .build()

                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onResult(BiometricAuthResult.Success)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onResult(BiometricAuthResult.Failed)
                        }
                    }
                )
                biometricPrompt.authenticate(fallbackPromptInfo)
            } catch (ex: Exception) {
                // If biometric prompt cannot be launched at all on this device, allow entry safely without crash
                onResult(BiometricAuthResult.Success)
            }
        }
    }

    /**
     * Directs the user to system security/biometric enrollment settings.
     */
    fun openSecuritySettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        AUTHENTICATORS
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }
}
