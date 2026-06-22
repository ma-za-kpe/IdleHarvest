package com.maku.idleharvest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestTheme

/**
 * System health indicator (green/yellow/red dot with label).
 *
 * Used on the main dashboard to show agent and connectivity status at a glance.
 */
enum class HealthStatus { HEALTHY, DEGRADED, CRITICAL }

@Composable
fun HealthIndicator(
    status: HealthStatus,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val extended = IdleHarvestTheme.extendedColors
    val color: Color = when (status) {
        HealthStatus.HEALTHY -> extended.healthGreen
        HealthStatus.DEGRADED -> extended.healthYellow
        HealthStatus.CRITICAL -> extended.healthRed
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IdleHarvestDimens.HealthIndicatorSize)
                .clip(CircleShape)
                .background(color)
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(IdleHarvestDimens.SpaceSM))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
