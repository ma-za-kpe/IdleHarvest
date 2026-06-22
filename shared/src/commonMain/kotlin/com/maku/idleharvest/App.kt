package com.maku.idleharvest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.maku.idleharvest.ui.theme.IdleHarvestBrand
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestTheme
import com.maku.idleharvest.ui.theme.IdleHarvestTheme as Theme
import org.jetbrains.compose.resources.painterResource

import idleharvest.shared.generated.resources.Res
import idleharvest.shared.generated.resources.idleharvest_logo

@Composable
@Preview
fun App() {
    IdleHarvestTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.idleharvest_logo),
                contentDescription = IdleHarvestBrand.APP_NAME,
                modifier = Modifier.size(IdleHarvestDimens.LogoSplash),
            )
            Spacer(modifier = Modifier.height(IdleHarvestDimens.SpaceXL))
            Text(
                text = IdleHarvestBrand.APP_NAME,
                style = IdleHarvestBrand.LogoTextStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(IdleHarvestDimens.SpaceSM))
            Text(
                text = IdleHarvestBrand.APP_TAGLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(IdleHarvestDimens.SpaceXXL))
            Button(
                onClick = { showContent = !showContent },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Get Started")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(IdleHarvestDimens.SpaceLG))
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Theme.extendedColors.accentGold,
                    )
                }
            }
        }
    }
}