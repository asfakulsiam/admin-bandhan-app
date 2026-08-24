package com.admin.bandhan17.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.admin.bandhan17.app.security.BiometricAuthManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityTest {

    @Test
    fun `security strings exist and are non-empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secTitle = context.getString(R.string.security_title)
        val secSubtitle = context.getString(R.string.security_subtitle)
        val secPromptTitle = context.getString(R.string.security_prompt_title)
        val secBtnUnlock = context.getString(R.string.security_btn_unlock)

        assertNotNull(secTitle)
        assertTrue(secTitle.isNotEmpty())
        assertNotNull(secSubtitle)
        assertTrue(secSubtitle.isNotEmpty())
        assertNotNull(secPromptTitle)
        assertTrue(secPromptTitle.isNotEmpty())
        assertNotNull(secBtnUnlock)
        assertTrue(secBtnUnlock.isNotEmpty())
    }

    @Test
    fun `check biometric status executes cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BiometricAuthManager()
        val status = manager.checkBiometricStatus(context)
        assertNotNull(status)
    }
}

