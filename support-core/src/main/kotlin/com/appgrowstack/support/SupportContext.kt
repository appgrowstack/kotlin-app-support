package com.appgrowstack.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The device the request was written on. Android apps pass `SupportDevice(Build.VERSION.RELEASE, Build.MODEL)`. */
public data class SupportDevice(val osVersion: String, val model: String)

/**
 * The context attached to every request, so the developer can answer without asking "which version?
 * which phone? are you subscribed?". The app passes the build and the device ([current]); nothing
 * the person typed elsewhere in the app goes in here.
 */
@Serializable
public data class SupportContext(
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    val build: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("device_model") val deviceModel: String,
    val channel: Channel,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    /** The recorded session the person was in when writing, if the app records sessions. */
    @SerialName("session_id") val sessionId: String? = null,
    /** The screen the form was opened from. */
    val screen: String? = null,
    /** Recent events, newest first. Only the first 20 are sent. */
    @SerialName("recent_events") val recentEvents: List<SupportRecentEvent> = emptyList(),
    /** In-app campaigns shown recently, newest first. Only the first 10 are sent. */
    @SerialName("campaigns_shown") val campaignsShown: List<SupportCampaignShown> = emptyList(),
) {
    /** Where the build came from. The same values as kotlin-app-campaign's channel. */
    @Serializable
    public enum class Channel(public val wire: String) {
        @SerialName("appstore") APP_STORE("appstore"),
        @SerialName("testflight") TEST_FLIGHT("testflight"),
        @SerialName("debug") DEBUG("debug"),
        @SerialName("play") PLAY("play"),
        @SerialName("play_test") PLAY_TEST("play_test"),
    }

    /** The context as it is sent: long values cut to the contract's lengths, at most 20 events and 10 campaigns. */
    public fun forSending(): SupportContext = copy(
        build = build.take(32),
        osVersion = osVersion.take(32),
        deviceModel = deviceModel.take(64),
        screen = screen?.take(64),
        recentEvents = recentEvents.take(20),
        campaignsShown = campaignsShown.take(10),
    )

    /** The context as lines a person can read at the bottom of a mail. */
    public val readableLines: List<String>
        get() = buildList {
            add("App: $appVersion ($build) ${channel.wire}")
            add("Device: $platform $osVersion $deviceModel")
            isPremium?.let { add("Premium: ${if (it) "yes" else "no"}") }
            screen?.let { add("Screen: $it") }
            sessionId?.let { add("Session: $it") }
            if (recentEvents.isNotEmpty()) add("Recent: " + recentEvents.take(5).joinToString(", ") { it.name })
            if (campaignsShown.isNotEmpty()) add("Campaigns: " + campaignsShown.take(3).joinToString(", ") { it.campaign })
        }

    public companion object {
        /**
         * The context of this build and device. [appVersion] is cut to `x.y.z`.
         *
         * @param appVersion `versionName`.
         * @param build `versionCode`.
         */
        public fun current(
            appVersion: String,
            build: String,
            device: SupportDevice,
            channel: Channel,
            isPremium: Boolean? = null,
            sessionId: String? = null,
            screen: String? = null,
            recentEvents: List<SupportRecentEvent> = emptyList(),
            campaignsShown: List<SupportCampaignShown> = emptyList(),
        ): SupportContext = SupportContext(
            appVersion = normalizedVersion(appVersion),
            build = build.take(32),
            osVersion = device.osVersion.take(32),
            deviceModel = device.model.take(64),
            channel = channel,
            isPremium = isPremium,
            sessionId = sessionId,
            screen = screen?.take(64),
            recentEvents = recentEvents,
            campaignsShown = campaignsShown,
        )

        /** `3.2` stays `3.2`, `3.2.0.1` becomes `3.2.0`, anything else becomes `0.0`. */
        public fun normalizedVersion(raw: String): String {
            val parts = raw.split(".").filter { it.isNotEmpty() }.take(3)
            val ok = parts.size >= 2 && parts.all { part -> part.isNotEmpty() && part.all { it in '0'..'9' } }
            return if (ok) parts.joinToString(".") else "0.0"
        }
    }
}
