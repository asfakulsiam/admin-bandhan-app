package com.admin.bandhan17.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertNotNull(appName)
    assertTrue(appName.contains("Bandhan"))
    val bengaliName = context.getString(R.string.app_name_bengali)
    assertNotNull(bengaliName)
    assertTrue(bengaliName.contains("বন্ধন"))
  }
}


