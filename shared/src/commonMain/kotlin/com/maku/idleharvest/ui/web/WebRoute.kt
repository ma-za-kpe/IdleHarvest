package com.maku.idleharvest.ui.web

enum class WebRoute {
    Landing,
    Buyer,
    ;

    companion object {
        fun fromPath(path: String): WebRoute = when {
            path.startsWith("/buyer") -> Buyer
            else -> Landing
        }
    }
}
