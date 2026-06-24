package com.maku.idleharvest.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.ui.theme.IdleHarvestBrand
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestTheme

private object BuyerPortalUrls {
    const val BACKEND_BASE_URL = "http://127.0.0.1:8080"
}

@Composable
fun BuyerPortalScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    IdleHarvestTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal, vertical = IdleHarvestDimens.SpaceXL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BuyerPortalBody(onBack = onBack, onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun BuyerPortalBody(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 960.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Buyer Portal",
            style = IdleHarvestBrand.LogoTextStyle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = "This route shows the buyer side of the ecosystem and points to the local Ktor backend.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
        Row(horizontalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM)) {
            Button(onClick = onBack) {
                Text("Back to Landing")
            }
            OutlinedButton(onClick = { onOpenUrl("${BuyerPortalUrls.BACKEND_BASE_URL}/health") }) {
                Text("Backend Health")
            }
        }
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
        BuyerPortalCard(
            title = "Buyer Demand",
            body =
            "Simulated order intake, payout requests, and model demand " +
                "live in the backend.",
            actionText = "Open Orders API",
            onAction = { onOpenUrl("${BuyerPortalUrls.BACKEND_BASE_URL}/api/buyer/summary") },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
        BuyerPortalCard(
            title = "Model Artifacts",
            body =
            "The backend serves model metadata and the trained .pte artifact " +
                "from the local demo path.",
            actionText = "Download .pte",
            onAction = { onOpenUrl("${BuyerPortalUrls.BACKEND_BASE_URL}/api/models/idleharvest_model.pte") },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
        BuyerPortalCard(
            title = "Demo Flow",
            body =
            "Use adb reverse tcp:8080 tcp:8080 so the Android device can " +
                "reach the local backend during testing.",
            actionText = "Open README",
            onAction = { onOpenUrl("https://github.com/ma-za-kpe/IdleHarvest#vastai-training") },
        )
    }
}

@Composable
private fun BuyerPortalCard(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            OutlinedButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}
