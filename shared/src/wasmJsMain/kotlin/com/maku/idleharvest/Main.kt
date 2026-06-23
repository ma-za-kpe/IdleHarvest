package com.maku.idleharvest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.maku.idleharvest.ui.web.WebApp
import com.maku.idleharvest.ui.web.WebRoute
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class) // reload trigger
fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        WebApp(
            route = WebRoute.fromPath(window.location.pathname),
            onOpenUrl = { url ->
                if (url.startsWith("/")) {
                    window.location.assign(url)
                } else {
                    window.open(url, "_blank")
                }
            },
        )
    }
}
