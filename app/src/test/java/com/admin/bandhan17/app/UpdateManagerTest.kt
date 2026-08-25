package com.admin.bandhan17.app

import com.admin.bandhan17.app.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testDefaultRepositoryConfiguration() {
        assertEquals("asfakulsiam", UpdateManager.DEFAULT_REPO_OWNER)
        assertEquals("admin-bandhan-app", UpdateManager.DEFAULT_REPO_NAME)
    }

    @Test
    fun testUpdateManagerInstantiation() {
        val manager = UpdateManager()
        assertNotNull(manager)
    }
}
