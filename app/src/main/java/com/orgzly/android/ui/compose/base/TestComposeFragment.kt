package com.orgzly.android.ui.compose.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cl.emilym.compose.units.rdp
import com.orgzly.android.ui.compose.widgets.BackButton

private const val LORUM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit"
private const val TEST_BUTTON = "Test Button"

class TestComposeFragment: ComposeFragment() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Test Title")
                    },
                    navigationIcon = {
                        BackButton()
                    }
                )
            }
        ) { contentPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(1.rdp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.rdp)
                ) {
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        LORUM_IPSUM,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OutlinedButton(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    TextButton(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    val textFieldState = remember { TextFieldState() }
                    TextField(
                        textFieldState
                    )
                }
            }
        }
    }

    companion object {

        @JvmStatic
        val FRAGMENT_TAG: String = TestComposeFragment::class.java.name

        @JvmStatic
        fun getInstance(): TestComposeFragment {
            return TestComposeFragment()
        }

    }

}