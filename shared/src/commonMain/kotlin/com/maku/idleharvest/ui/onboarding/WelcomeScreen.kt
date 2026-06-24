package com.maku.idleharvest.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.cd_logo
import idleharvest.shared.generated.resources.idleharvest_logo
import idleharvest.shared.generated.resources.onboarding_welcome_cta
import idleharvest.shared.generated.resources.onboarding_welcome_description
import idleharvest.shared.generated.resources.onboarding_welcome_subtitle
import idleharvest.shared.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val logoDescription = stringResource(Res.string.cd_logo)
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.idleharvest_logo),
            contentDescription = null,
            modifier =
            Modifier
                .size(IdleHarvestDimens.LogoOnboarding)
                .semantics { contentDescription = logoDescription },
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
        Text(
            text = stringResource(Res.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
        Text(
            text = stringResource(Res.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
        Text(
            text = stringResource(Res.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        Button(
            onClick = onNext,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(IdleHarvestDimens.ButtonHeight),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_welcome_cta),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
