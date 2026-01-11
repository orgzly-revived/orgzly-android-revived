package com.orgzly.android.ui.compose.widgets

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.orgzly.R

@Composable
fun BackButton(
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