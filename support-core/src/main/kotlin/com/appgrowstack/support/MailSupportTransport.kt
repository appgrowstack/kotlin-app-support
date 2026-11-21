package com.appgrowstack.support

import java.net.URLEncoder

/**
 * Sends a request as a mail to you: the mail app opens with the subject, the person's text and the
 * context below it as readable lines. No server, no replies in the app, no screenshots.
 *
 * ```kotlin
 * MailSupportTransport("support@example.com") { uri ->
 *     runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri))) }.isSuccess
 * }
 * ```
 *
 * @param to your support address.
 * @param open opens the `mailto:` URI; `false` when there is no mail app.
 */
public class MailSupportTransport(
    public val to: String,
    private val open: suspend (String) -> Boolean,
) : SupportTransport {
    override val capabilities: SupportCapabilities = SupportCapabilities(replies = false, images = false)

    override suspend fun submit(request: SupportRequest, context: SupportContext, lang: String): SupportSubmission {
        if (!open(mailtoUri(request, context))) throw SupportError.Transport("no mail app")
        return SupportSubmission("mail")
    }

    override suspend fun tickets(): List<SupportTicket> = emptyList()

    override suspend fun reply(body: String, ticketId: String): SupportMessage = throw SupportError.Unsupported

    /** `mailto:<to>?subject=…&body=…` with the context's readable lines below the text. */
    public fun mailtoUri(request: SupportRequest, context: SupportContext): String {
        val subject = request.subject?.trim()?.takeIf { it.isNotEmpty() }?.take(120) ?: "[${request.category.wire}]"
        val body = request.body.trim().take(4000) + "\n\n---\n" + context.readableLines.joinToString("\n")
        return "mailto:$to?subject=${encode(subject)}&body=${encode(body)}"
    }

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
