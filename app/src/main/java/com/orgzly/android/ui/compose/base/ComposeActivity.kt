package com.orgzly.android.ui.compose.base

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.orgzly.android.ui.CommonActivity

abstract class ComposeActivity: CommonActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrgzlyBootstrap { this@ComposeActivity.Content() }
        }
    }

    @Composable
    abstract fun Content()

}