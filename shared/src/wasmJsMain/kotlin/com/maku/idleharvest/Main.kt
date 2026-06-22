package com.maku.idleharvest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.maku.idleharvest.ui.web.WebApp
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class) // reload trigger
fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        WebApp(onOpenUrl = { url -> window.open(url, "_blank") })
    }
}
