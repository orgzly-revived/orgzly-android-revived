package com.orgzly.android.espresso

import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.clickClickableSpan
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(value = Parameterized::class)
class ExternalLinksTest(private val param: Parameter) : OrgzlyTest() {

    data class Parameter(val link: String, val check: () -> Any)

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        GrantPermissionRule.grant()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<Parameter> {
            val cacheDir = App.getAppContext().cacheDir
            val storageDir = Environment.getExternalStorageDirectory()
//            val downloadsDir = Environment.getExternalStoragePublicDirectory(
//                    Environment.DIRECTORY_DOWNLOADS)

            return listOf(
                    Parameter("file:./non-existing-file") {
                        onSnackbar().check(matches(
                                withText("File does not exist: $storageDir/non-existing-file")))
                    },

                    Parameter("file:$cacheDir") {
                        onSnackbar().check(matches(withText(startsWith(
                                "Failed to open file: Failed to find configured root"))))
                    }

//                    Parameter("file:${storageDir.absolutePath}/orgzly-tests/book.org") {
//                        onSnackbar().check(matches(withText(startsWith(
//                                "No application found to open this file"))))
//                    }
            )
        }
    }

    @Test
    fun testLink() {
        testUtils.setupBook("book", "* Note\n${param.link}")

        ActivityScenario.launch(MainActivity::class.java).use {
            // Open book
            onBook(0).perform(click())

            // Click on link
            onNoteInBook(1, R.id.item_head_content_view).perform(clickClickableSpan(param.link))

            SystemClock.sleep(500)

            param.check()
        }
    }
}