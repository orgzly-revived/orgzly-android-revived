package com.orgzly.android.ui.compose.widgets

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.orgzly.R
import com.orgzly.android.ui.compose.providers.provideFragmentManager

@Composable
fun BackButton(
    modifier: Modifier = Modifier,
) {
    val fragmentManager = provideFragmentManager()
    if (fragmentManager == null || fragmentManager.backStackEntryCount <= 1) return
    BaseBackButton(
        onClick = {
            fragmentManager.popBackStack()
        },
        modifier = modifier
    )
}

@Composable
fun BaseBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.then(modifier),
    ) {
        Icon(
            painterIcon(Icons.ARROW_BACK),
            contentDescription = stringResource(R.string.content_description_back_button)
        )
    }
}