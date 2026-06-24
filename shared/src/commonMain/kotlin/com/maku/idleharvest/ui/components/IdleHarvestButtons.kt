package com.maku.idleharvest.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.ui.theme.IdleHarvestColors
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestShapeTokens

/**
 * IdleHarvest branded button components.
 *
 * Follows the design system: 52dp height, 12dp corner radius,
 * emerald green primary, gold accent variant.
 */

@Composable
fun IHPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(IdleHarvestDimens.ButtonHeight),
        enabled = enabled,
        shape = IdleHarvestShapeTokens.Button,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun IHSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(IdleHarvestDimens.ButtonHeight),
        enabled = enabled,
        shape = IdleHarvestShapeTokens.Button,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun IHAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(IdleHarvestDimens.ButtonHeight),
        enabled = enabled,
        shape = IdleHarvestShapeTokens.Button,
        colors =
        ButtonDefaults.buttonColors(
            containerColor = IdleHarvestColors.AccentGold,
            contentColor = IdleHarvestColors.Navy,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun IHTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(IdleHarvestDimens.MinTouchTarget),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
