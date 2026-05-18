package com.orgzly.android.ui.compose.widgets

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OrgzlyCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    Checkbox(
        checked,
        onCheckedChange,
        modifier,
        enabled,
        colors = when (isError) {
            true -> CheckboxDefaults.colors().copy(
                checkedBorderColor = MaterialTheme.colorScheme.error,
                uncheckedBorderColor = MaterialTheme.colorScheme.error
            )
            else -> CheckboxDefaults.colors()
        }
    )
}

@Composable
fun OrgzlyRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    RadioButton(
        selected,
        onClick,
        modifier,
        enabled,
        colors = when (isError) {
            true -> RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.error,
                unselectedColor = MaterialTheme.colorScheme.error
            )
            else -> RadioButtonDefaults.colors()
        }
    )
}