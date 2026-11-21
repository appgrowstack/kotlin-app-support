package com.appgrowstack.support

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** When the person last opened each request's replies (epoch milliseconds of the newest reply seen). */
public interface SupportSeenStore {
    public fun seenAt(ticketId: String): Long?
    public fun markSeen(ticketId: String, replyAt: Long)
}

/** In memory, for tests and previews. Android apps pass one backed by SharedPreferences or DataStore. */
public class InMemorySupportSeenStore : SupportSeenStore {
    private val seen = mutableMapOf<String, Long>()
    override fun seenAt(ticketId: String): Long? = synchronized(seen) { seen[ticketId] }
    override fun markSeen(ticketId: String, replyAt: Long): Unit = synchronized(seen) { seen[ticketId] = replyAt }
}

/** What the support screens read. */
public data class SupportState(
    val tickets: List<SupportTicket> = emptyList(),
    val articles: List<HelpArticle> = emptyList(),
    val isLoading: Boolean = false,
    /** The last error from [SupportCenter.refresh], for a quiet notice in the list. Sending errors are thrown instead. */
    val lastError: SupportError? = null,
    /** Requests with a reply the person hasn't opened yet. Show it as a badge on the entry point. */
    val unreadCount: Int = 0,
)

/**
 * The state the support screens read: this install's requests, the help articles, and whether a
 * reply is unread. Make one per app and keep it (it is cheap to refresh on return to the foreground).
 *
 * @param transport where requests go ([MailSupportTransport], or one that talks to a server).
 * @param articles the help articles. When `null`, the transport's own, if it is a [HelpArticleSource].
 * @param lang the language of the person's UI (`ja`, `en`, `zh-Hant`). Requests and articles use it.
 * @param context builds the context at send time ([SupportContext.current] with the app's premium
 *   state, recent events and campaigns). `screen` is where the form was opened.
 */
public class SupportCenter(
    public val transport: SupportTransport,
    articles: HelpArticleSource? = null,
    public var lang: String,
    private val context: (screen: String?) -> SupportContext,
    private val seen: SupportSeenStore = InMemorySupportSeenStore(),
) {
    private val articleSource: HelpArticleSource? = articles ?: (transport as? HelpArticleSource)
    private val stateFlow = MutableStateFlow(SupportState())

    public val state: StateFlow<SupportState> = stateFlow.asStateFlow()

    /** What the transport can do. The screens hide the replies and the screenshots when it cannot. */
    public val capabilities: SupportCapabilities get() = transport.capabilities

    public fun isUnread(ticket: SupportTicket): Boolean {
        val reply = ticket.latestReply ?: return false
        return reply.at > (seen.seenAt(ticket.id) ?: 0)
    }

    /** Marks the ticket's replies as read (call when the thread appears). */
    public fun markRead(ticket: SupportTicket) {
        val reply = ticket.latestReply ?: return
        seen.markSeen(ticket.id, reply.at)
        stateFlow.update { it.copy(unreadCount = it.tickets.count(::isUnread)) }
    }

    /** Reloads requests and articles. Errors are kept in [SupportState.lastError], not thrown. */
    public suspend fun refresh() {
        stateFlow.update { it.copy(isLoading = true) }
        try {
            if (capabilities.replies) {
                try {
                    setTickets(transport.tickets())
                    stateFlow.update { it.copy(lastError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SupportError) {
                    stateFlow.update { it.copy(lastError = e) }
                } catch (e: Exception) {
                    stateFlow.update { it.copy(lastError = SupportError.Transport(e.message ?: e.toString())) }
                }
            }
            loadArticles()
        } finally {
            stateFlow.update { it.copy(isLoading = false) }
        }
    }

    /** Loads the articles only (for the form's suggestions before anything was sent). */
    public suspend fun loadArticles() {
        val source = articleSource ?: return
        val loaded = try {
            source.articles(lang)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        stateFlow.update { it.copy(articles = loaded) }
    }

    /** Articles that may already answer what the person is typing. */
    public fun suggestions(text: String, limit: Int = 3): List<HelpArticle> = HelpSearch.search(text, state.value.articles, limit)

    /**
     * Sends the form and reloads the list. Throws only when the request was not made; images that
     * did not reach it come back in [SupportSubmission.unsentImages].
     */
    public suspend fun submit(request: SupportRequest, screen: String? = null): SupportSubmission {
        val sending = if (capabilities.images) request else request.copy(images = emptyList())
        val submission = transport.submit(sending, context(screen), lang)
        if (capabilities.replies) runCatching { transport.tickets() }.onSuccess(::setTickets)
        return submission
    }

    /** Adds a message to a request and reloads it. */
    public suspend fun reply(body: String, ticket: SupportTicket) {
        transport.reply(body, ticket.id)
        runCatching { transport.tickets() }.onSuccess(::setTickets)
    }

    private fun setTickets(tickets: List<SupportTicket>) {
        stateFlow.update { it.copy(tickets = tickets, unreadCount = tickets.count(::isUnread)) }
    }
}
