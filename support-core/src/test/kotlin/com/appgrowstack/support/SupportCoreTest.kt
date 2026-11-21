package com.appgrowstack.support

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A transport with replies and screenshots that keeps what it was given. */
private class FakeTransport : SupportTransport, HelpArticleSource {
    val submitted = mutableListOf<Triple<SupportRequest, SupportContext, String>>()
    override var capabilities = SupportCapabilities(replies = true, images = true)
    var ticketList: Result<List<SupportTicket>> = Result.success(emptyList())
    var articleList: List<HelpArticle> = emptyList()

    override suspend fun submit(request: SupportRequest, context: SupportContext, lang: String): String {
        submitted += Triple(request, context, lang)
        return "tkt_0123456789abcdef"
    }

    override suspend fun tickets(): List<SupportTicket> = ticketList.getOrThrow()
    override suspend fun reply(body: String, ticketId: String): SupportMessage = SupportMessage("msg_1", SupportMessage.Author.USER, body, 0)
    override suspend fun articles(lang: String): List<HelpArticle> = articleList
}

private val json = Json { ignoreUnknownKeys = true }

private const val TICKET_JSON = """{"tickets":[{"ticket_id":"tkt_0123456789abcdef","status":"answered","subject":null,"created_at":1700000000000,"updated_at":1700000100000,"messages":[{"id":"msg_0123456789abcdef","from":"user","body":"解約したい","at":1700000000000},{"id":"msg_fedcba9876543210","from":"support","body":"Google Play で解約できます","at":1700000100000}]}]}"""
private const val ARTICLES_JSON = """{"articles":[{"slug":"cancel-subscription","lang":"ja","title":"サブスクリプションを解約する","summary":"iPhoneはApp Store、AndroidはGoogle Playで解約します。","url":"https://gohobishukan.com/help/cancel-subscription/","tags":["billing"],"updated_at":1},{"slug":"widget","lang":"ja","title":"ウィジェットを置く","summary":"ホーム画面にウィジェットを置きます。","url":"https://gohobishukan.com/help/widget/","tags":[],"updated_at":1}]}"""

private val tickets: List<SupportTicket> =
    json.decodeFromString(MapSerializer(String.serializer(), kotlinx.serialization.builtins.ListSerializer(SupportTicket.serializer())), TICKET_JSON).getValue("tickets")
private val articles: List<HelpArticle> =
    json.decodeFromString(MapSerializer(String.serializer(), kotlinx.serialization.builtins.ListSerializer(HelpArticle.serializer())), ARTICLES_JSON).getValue("articles")

private fun context() = SupportContext(
    appVersion = "3.2.0", build = "120", osVersion = "16", deviceModel = "Pixel 9", channel = SupportContext.Channel.PLAY, isPremium = true, screen = "settings",
    recentEvents = List(25) { SupportRecentEvent("habit_achieved", 1_700_000_000_000, mapOf("streak_days" to SupportValue.of(3), "category" to SupportValue.of("health"))) },
    campaignsShown = listOf(SupportCampaignShown("trial-offer", 3, 1_700_000_000_000)),
)

class MailSupportTransportTest {
    @Test
    fun opensAMailWithTheTextAndTheContext() = runTest {
        val opened = mutableListOf<String>()
        val mail = MailSupportTransport("support@example.com") { opened += it; true }
        assertEquals(SupportCapabilities(replies = false, images = false), mail.capabilities)

        val id = mail.submit(SupportRequest(SupportCategory.BILLING, subject = "  ", body = " 解約したい \n"), context().copy(sessionId = "ses_0123456789abcdef"), "ja")
        assertEquals("mail", id)
        val uri = URI(opened.single())
        assertEquals("mailto", uri.scheme)
        assertEquals("support@example.com", uri.schemeSpecificPart.substringBefore('?'))
        val items = uri.rawSchemeSpecificPart.substringAfter('?').split("&").associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
        assertEquals("[billing]", items["subject"])
        val body = items.getValue("body")
        assertTrue(body.startsWith("解約したい\n\n---\n"))
        for (line in listOf("App: 3.2.0 (120) play", "Device: android 16 Pixel 9", "Premium: yes", "Screen: settings", "Session: ses_0123456789abcdef", "Campaigns: trial-offer")) {
            assertTrue(body.contains(line), line)
        }
    }

    @Test
    fun noMailAppAndNoReplies() = runTest {
        val mail = MailSupportTransport("support@example.com") { false }
        assertEquals(SupportError.Transport("no mail app"), assertFailsWith<SupportError> { mail.submit(SupportRequest(SupportCategory.BUG, body = "x"), context(), "ja") })
        assertTrue(mail.tickets().isEmpty())
        assertEquals(SupportError.Unsupported, assertFailsWith<SupportError> { mail.reply("x", "tkt_x") })
    }
}

class BundledHelpArticlesTest {
    @Test
    fun picksTheLanguageThenTheBaseThenEnglish() = runTest {
        val en = """{"slug":"widget","lang":"en","title":"Add the widget","summary":"On the Home Screen.","url":"https://x.com/en/help/widget/","tags":[],"updated_at":1}"""
        val zh = """{"slug":"widget","lang":"zh","title":"小组件","summary":"主屏幕。","url":"https://x.com/zh/help/widget/","tags":[],"updated_at":1}"""
        val bundled = BundledHelpArticles.fromJson("""{"articles":[$en,$zh]}""")
        assertEquals(listOf("zh"), bundled.articles("zh-Hans").map { it.lang })
        assertEquals(listOf("en"), bundled.articles("en").map { it.lang })
        assertEquals(listOf("en"), bundled.articles("ja").map { it.lang })
        assertTrue(BundledHelpArticles.fromJson("nope").articles("ja").isEmpty())
    }
}

class SupportContextTest {
    @Test
    fun versionIsCutToThreeParts() {
        assertEquals("3.2", SupportContext.normalizedVersion("3.2"))
        assertEquals("3.2.0", SupportContext.normalizedVersion("3.2.0.1"))
        assertEquals("0.0", SupportContext.normalizedVersion("3.2-beta"))
        assertEquals("0.0", SupportContext.normalizedVersion(""))
    }

    @Test
    fun currentTakesTheBuildAndTheDevice() {
        val c = SupportContext.current("3.4.0.2", "340", SupportDevice("16", "Pixel 9"), SupportContext.Channel.PLAY_TEST, isPremium = false, screen = "s".repeat(80))
        assertEquals("3.4.0", c.appVersion)
        assertEquals("Pixel 9", c.deviceModel)
        assertEquals(64, c.screen?.length)
        assertEquals("android", c.platform)
    }

    @Test
    fun theWireShapeIsTheContracts() {
        val encoder = Json { encodeDefaults = true; explicitNulls = false }
        val sent = context().forSending()
        assertEquals(20, sent.recentEvents.size)
        val obj = Json.parseToJsonElement(encoder.encodeToString(SupportContext.serializer(), sent)).jsonObject
        assertEquals(
            setOf("platform", "app_version", "build", "os_version", "device_model", "channel", "is_premium", "screen", "recent_events", "campaigns_shown"),
            obj.keys,
        )
        assertEquals("\"play\"", obj.getValue("channel").toString())
        assertEquals("""{"streak_days":3,"category":"health"}""", (obj.getValue("recent_events") as kotlinx.serialization.json.JsonArray)[0].jsonObject.getValue("props").toString())
    }

    @Test
    fun typedValuesKeepTheirType() {
        val values = mapOf("a" to SupportValue.of(3), "b" to SupportValue.of(1.5), "c" to SupportValue.of(true), "d" to SupportValue.of("x"))
        val serializer = MapSerializer(String.serializer(), SupportValue.serializer())
        assertEquals(values, Json.decodeFromString(serializer, Json.encodeToString(serializer, values)))
    }
}

class HelpSearchTest {
    @Test
    fun japaneseWithoutSpacesFindsTheArticle() {
        assertEquals(listOf("cancel-subscription"), HelpSearch.search("サブスクを解約したいです", articles).map { it.slug })
        assertEquals("widget", HelpSearch.search("ウィジェットが出ない", articles).firstOrNull()?.slug)
    }

    @Test
    fun grammarAloneMatchesNothing() {
        assertEquals(listOf("約し", "解約"), HelpSearch.terms("解約したい"))
        assertTrue(HelpSearch.search("したい", articles).isEmpty())
        assertTrue(HelpSearch.search("", articles).isEmpty())
    }

    @Test
    fun latinWordsIgnoreCaseAccentsAndWidth() {
        val en = listOf(HelpArticle("restore", "en", "Restore purchases", "If Premium is missing after reinstalling.", "https://x.com/en/help/restore/", updatedAt = 0))
        assertEquals(1, HelpSearch.search("RESTORE my premium", en).size)
        assertEquals(1, HelpSearch.search("ＲＥＳＴＯＲＥ", en).size)
        assertEquals(1, HelpSearch.search("réstore", en).size)
        assertTrue(HelpSearch.search("widget", en).isEmpty())
    }
}

class SupportCenterTest {
    @Test
    fun refreshLoadsAndCountsUnreadUntilOpened() = runTest {
        val transport = FakeTransport().apply {
            ticketList = Result.success(tickets)
            articleList = articles
        }
        val center = SupportCenter(transport, lang = "ja", context = { context() })
        center.refresh()
        val state = center.state.value
        assertEquals(1, state.tickets.size)
        assertEquals(2, state.articles.size)
        assertEquals(1, state.unreadCount)
        assertTrue(!state.isLoading)
        center.markRead(state.tickets[0])
        assertEquals(0, center.state.value.unreadCount)
        assertEquals("cancel-subscription", center.suggestions("解約の方法").firstOrNull()?.slug)
    }

    @Test
    fun refreshKeepsTheErrorQuietly() = runTest {
        val transport = FakeTransport().apply { ticketList = Result.failure(SupportError.Transport("offline")) }
        val center = SupportCenter(transport, lang = "ja", context = { context() })
        center.refresh()
        assertEquals(SupportError.Transport("offline"), center.state.value.lastError)
        assertTrue(center.state.value.tickets.isEmpty())
    }

    @Test
    fun withoutRepliesNoListIsReadAndScreenshotsAreDropped() = runTest {
        val transport = FakeTransport().apply {
            capabilities = SupportCapabilities(replies = false, images = false)
            ticketList = Result.failure(SupportError.Transport("must not be read"))
        }
        val center = SupportCenter(transport, BundledHelpArticles(articles), lang = "ja", context = { context() })
        center.refresh()
        assertNull(center.state.value.lastError)
        assertEquals(2, center.state.value.articles.size)
        center.submit(SupportRequest(SupportCategory.BUG, body = "x", images = listOf(SupportImage(byteArrayOf(1), "image/jpeg"))), screen = "settings")
        assertTrue(transport.submitted.single().first.images.isEmpty())
        assertEquals("ja", transport.submitted.single().third)
    }
}

class SupportStringsTest {
    @Test
    fun picksTheLanguageFromItsCode() {
        val picks = listOf(
            "ja" to "お問い合わせ", "ja-JP" to "お問い合わせ", "ko" to "문의하기", "zh-Hans" to "联系我们", "zh" to "联系我们",
            "zh-Hant" to "聯絡我們", "zh_TW" to "聯絡我們", "zh-HK" to "聯絡我們", "zh-Hant-TW" to "聯絡我們",
            "es-MX" to "Contáctanos", "pt-BR" to "Fale conosco", "de" to "Kontakt", "fr-CA" to "Nous contacter",
            "en" to "Contact us", "it" to "Contact us", "" to "Contact us",
        )
        for ((lang, title) in picks) assertEquals(title, SupportStrings.forLanguage(lang).formTitle, lang)
    }

    @Test
    fun everyLanguageNamesEveryCategoryAndStatus() {
        assertEquals(setOf("en", "ja", "ko", "zh-Hans", "zh-Hant", "es", "pt-BR", "de", "fr"), SupportStrings.builtIn.keys)
        for ((lang, strings) in SupportStrings.builtIn) {
            assertEquals(SupportCategory.entries.toSet(), strings.categoryNames.keys, lang)
            assertEquals(SupportTicketStatus.entries.toSet(), strings.statusNames.keys, lang)
            val words = listOf(
                strings.formTitle, strings.category, strings.subject, strings.body, strings.bodyPlaceholder, strings.email,
                strings.emailFooter, strings.images, strings.addImages, strings.send, strings.sending, strings.sent, strings.sentDetail,
                strings.maybeHelpful, strings.contextFooter, strings.requestsTitle, strings.noRequests, strings.replyPlaceholder,
                strings.replySend, strings.helpTitle, strings.searchHelp, strings.noHelpMatches, strings.contact, strings.failed,
                strings.rateLimited,
            ) + strings.categoryNames.values + strings.statusNames.values
            assertTrue(words.all { it.isNotEmpty() }, lang)
            if (lang != "en") assertNotEquals(SupportStrings.english.formTitle, strings.formTitle, lang)
        }
    }
}
