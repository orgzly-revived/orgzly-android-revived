package com.orgzly.android.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ApplicationProvider
import com.orgzly.R
import com.orgzly.android.AppDataRule
import com.orgzly.android.ui.sync.SyncFragment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivitySmokeTest {
    @get:Rule
    val data = AppDataRule()

    @Test
    fun testLifecycle() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()

        val activity = controller.get()
        val drawer = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
        assertNotNull(activity.supportFragmentManager.findFragmentByTag(SyncFragment.FRAGMENT_TAG))

        drawer.openDrawer(GravityCompat.START, false)
        idle()
        assertTrue(drawer.isDrawerOpen(GravityCompat.START))

        // Back closes the open drawer instead of leaving
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        assertFalse(activity.isFinishing)

        drawer.closeDrawer(GravityCompat.START, false)
        idle()

        controller.pause().resume()
        idle()

        // Back with the drawer closed leaves the notebooks list
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        assertTrue(activity.isFinishing)

        controller.pause().resume()
        idle()

        controller.pause().stop().destroy()
    }

    @Test
    fun testRecreateKeepsSyncFragment() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        val syncFragment = controller.get().supportFragmentManager.findFragmentByTag(SyncFragment.FRAGMENT_TAG)

        controller.recreate()
        idle()

        assertSame(syncFragment, controller.get().supportFragmentManager.findFragmentByTag(SyncFragment.FRAGMENT_TAG))
        assertSame(syncFragment, controller.get().mSyncFragment)

        controller.pause().stop().destroy()
    }

    @Test
    fun testDeepLinks() {
        for (uri in listOf("orgzly://note/123", "orgzly://note/not-a-number", "orgzly://note", "https://orgzlyrevived.com/note/")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
            idle()

            controller.newIntent(intent)
            idle()

            controller.pause().stop().destroy()
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
