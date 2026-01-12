package com.orgzly.android.ui.compose.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.emilym.compose.units.rdp
import com.orgzly.android.ui.compose.widgets.BackButton
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.compose.widgets.OrgzlyButtonColorScheme
import com.orgzly.android.ui.compose.widgets.OrgzlyFloatingActionButton
import com.orgzly.android.ui.compose.widgets.OrgzlyOutlinedButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.compose.widgets.painterIcon

private const val LORUM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit"
private const val TEST_BUTTON = "Test Button"

class TestComposeFragment: ComposeFragment() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Scaffold(
            topBar = {
                OrgzlyTopAppBar(
                    title = "Test Title",
                    navigationIcon = {
                        BackButton()
                    }
                )
            },
            floatingActionButton = {
                val navigator = LocalNavigator.current
                OrgzlyFloatingActionButton(
                    onClick = {
                        navigator.navigate(
                            NavigationDestination.Books
                        )
                    },
                    colorScheme = OrgzlyButtonColorScheme.ERROR
                ) {
                    Icon(
                        painterIcon(Icons.ARROW_UPWARD),
                        contentDescription = null
                    )
                }
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
                    OrgzlyButton(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OrgzlyButton(
                        onClick = {},
                        colorScheme = OrgzlyButtonColorScheme.ERROR
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OrgzlyOutlinedButton(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OrgzlyOutlinedButton(
                        onClick = {},
                        colorScheme = OrgzlyButtonColorScheme.ERROR
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OrgzlyTextButton(
                        onClick = {}
                    ) {
                        Text(TEST_BUTTON)
                    }
                    OrgzlyTextButton(
                        onClick = {},
                        colorScheme = OrgzlyButtonColorScheme.ERROR
                    ) {
                        Text(TEST_BUTTON)
                    }
                    val textFieldState = rememberTextFieldState()
                    OrgzlyTextField(
                        textFieldState,
                        label = {
                            Text("Test text field")
                        },
                        placeholder = {
                            Text("Placeholder")
                        },
                        supportingText = {
                            Text("Supporting text")
                        },
                        leadingIcon = {
                            Icon(
                                painterIcon(Icons.ARROW_UPWARD),
                                contentDescription = null
                            )
                        }
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