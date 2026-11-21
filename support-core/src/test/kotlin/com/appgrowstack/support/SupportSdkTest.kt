package com.appgrowstack.support

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportSdkTest {
    @Test
    fun versionMatchesGradleProperties() {
        val properties = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "gradle.properties") }
            .first { it.isFile && it.readText().contains(Regex("(?m)^version=")) }
        val version = Regex("(?m)^version=(.+)$").find(properties.readText())!!.groupValues[1].trim()
        assertEquals(version, SupportSdk.VERSION)
        assertEquals("kotlin-app-support", SupportSdk.NAME)
    }
}
