package com.orgzly.android.ui.compose.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import cl.emilym.compose.units.rdp
import com.orgzly.android.ui.compose.modifiers.noRippleClickable

@Composable
fun FormLockupTitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.then(modifier)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium
        )
        subtitle?.let {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun VerticalFormLockup(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.then(modifier),
        verticalArrangement = Arrangement.spacedBy(0.5.rdp)
    ) {
        FormLockupTitle(
            title,
            subtitle
        )
        content()
    }
}

@Composable
fun HorizontalFormLockup(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.then(modifier),
        horizontalArrangement = Arrangement.spacedBy(0.5.rdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
        FormLockupTitle(
            title,
            subtitle
        )
    }
}

@Composable
fun CheckboxFormLockup(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    HorizontalFormLockup(
        title,
        Modifier
            .noRippleClickable(
                role = Role.Checkbox
            ) {
                onCheckedChange?.let { it(!checked) }
            }
            .then(modifier),
        subtitle,
    ) {
        OrgzlyCheckbox(
            checked,
            onCheckedChange,
            enabled = enabled,
            isError = isError
        )
    }
}

@Composable
fun RadioButtonFormLockup(
    selected: Boolean,
    onClick: (() -> Unit)?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    HorizontalFormLockup(
        title,
        Modifier
            .noRippleClickable(
                role = Role.RadioButton
            ) {
                onClick?.let { it() }
            }
            .then(modifier),
        subtitle,
    ) {
        OrgzlyRadioButton(
            selected,
            onClick,
            enabled = enabled,
            isError = isError
        )
    }
}