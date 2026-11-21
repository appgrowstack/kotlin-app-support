package com.appgrowstack.support

/** What a transport can do. The screens hide what it cannot (the list of replies, the screenshots). */
public data class SupportCapabilities(
    /** The person can read their requests and the replies inside the app. */
    val replies: Boolean,
    /** Screenshots can be attached. */
    val images: Boolean,
)

/**
 * Where a request goes. [MailSupportTransport] opens a mail to you; a transport that talks to a
 * support server can also list the person's requests and replies.
 */
public interface SupportTransport {
    public val capabilities: SupportCapabilities

    /** Sends a request with its images (when [SupportCapabilities.images]). Returns its id. */
    public suspend fun submit(request: SupportRequest, context: SupportContext, lang: String): String

    /** This install's requests, newest first. Empty when the transport has no replies. */
    public suspend fun tickets(): List<SupportTicket>

    /** Adds a message to one of this install's requests. */
    public suspend fun reply(body: String, ticketId: String): SupportMessage
}

/** The help articles to search before writing. */
public fun interface HelpArticleSource {
    /** The articles in [lang], falling back to the base language, then English. */
    public suspend fun articles(lang: String): List<HelpArticle>
}

/** Why a request could not be sent or read. */
public sealed class SupportError(message: String) : Exception(message) {
    /** Too many requests from this install, or too many images on one request. */
    public data object RateLimited : SupportError("rate limited")

    /** The request isn't this install's, or the product doesn't take requests. */
    public data object NotFound : SupportError("not found")

    /** The request was rejected ([code] and [reason] from the answer). */
    public data class Rejected(val status: Int, val code: String, val reason: String) : SupportError("$status $code: $reason")

    /** No network, no mail app, or the answer could not be read. */
    public data class Transport(val reason: String) : SupportError(reason)

    /** The transport cannot do this (replying by mail). */
    public data object Unsupported : SupportError("unsupported")
}
