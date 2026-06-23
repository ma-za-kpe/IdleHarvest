package com.maku.idleharvest

import platform.Foundation.NSProcessInfo

// Probe: test basic NSProcessInfo properties
@Suppress("unused")
fun thermalProbe(): String = NSProcessInfo.processInfo.operatingSystemVersionString
