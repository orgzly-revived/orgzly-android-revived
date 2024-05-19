package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.orgzly.R
import com.orgzly.BuildConfig
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.ui.main.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Assume


@RunWith(value = Parameterized::class)
class SshKeyCreationTest(private val param: Parameter) : OrgzlyTest() {
    data class Parameter(val keyType: Int)
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Parameter> {
            return listOf(
                Parameter(keyType = R.string.ssh_keygen_label_rsa),
                Parameter(keyType = R.string.ssh_keygen_label_ecdsa),
                Parameter(keyType = R.string.ssh_keygen_label_ed25519),
            )
        }
    }

    @Test
    fun testCreateUnprotectedKey() {
        Assume.assumeFalse(BuildConfig.IS_GIT_REMOVED);
        ActivityScenario.launch(MainActivity::class.java).use {
            EspressoUtils.onActionItemClick(R.id.activity_action_settings, R.string.settings)
            EspressoUtils.clickSetting(null, R.string.app)
            EspressoUtils.clickSetting(null, R.string.developer_options)
            EspressoUtils.clickSetting(null, R.string.git_repository_type)
            pressBack()
            pressBack()
            EspressoUtils.clickSetting(null, R.string.sync)
            EspressoUtils.clickSetting(null, R.string.ssh_keygen_preference_title)
            onView(withText(param.keyType)).perform(click())
            onView(withText(R.string.ssh_keygen_generate)).perform(click())
            getInstrumentation().waitForIdleSync()
            onView(withText(R.string.your_public_key)).inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }
}
