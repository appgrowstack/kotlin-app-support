package com.appgrowstack.support

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What a request is about. The person picks one in the form. */
@Serializable
public enum class SupportCategory(public val wire: String) {
    @SerialName("bug") BUG("bug"),
    @SerialName("billing") BILLING("billing"),
    @SerialName("account") ACCOUNT("account"),
    @SerialName("question") QUESTION("question"),
    @SerialName("request") REQUEST("request"),
    @SerialName("other") OTHER("other"),
}

/** The state the person sees. `waiting` covers received, drafted and in review. */
@Serializable
public enum class SupportTicketStatus {
    @SerialName("waiting") WAITING,
    @SerialName("answered") ANSWERED,
    @SerialName("resolved") RESOLVED,
}

/** A value of an event's property. Keeps its type: an integer stays an integer. */
@Serializable(with = SupportValue.Serializer::class)
public sealed class SupportValue {
    public data class Text(val value: String) : SupportValue()
    public data class Integer(val value: Long) : SupportValue()
    public data class Decimal(val value: Double) : SupportValue()
    public data class Flag(val value: Boolean) : SupportValue()

    public companion object {
        public fun of(value: String): SupportValue = Text(value)
        public fun of(value: Long): SupportValue = Integer(value)
        public fun of(value: Int): SupportValue = Integer(value.toLong())
        public fun of(value: Double): SupportValue = Decimal(value)
        public fun of(value: Boolean): SupportValue = Flag(value)
    }

    /** A JSON number, string or boolean. Text is cut to 200 characters when sent. */
    public object Serializer : KSerializer<SupportValue> {
        override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

        override fun serialize(encoder: Encoder, value: SupportValue) {
            val json = encoder as? JsonEncoder ?: error("SupportValue is JSON only")
            json.encodeJsonElement(
                when (value) {
                    is Text -> JsonPrimitive(value.value.take(200))
                    is Integer -> JsonPrimitive(value.value)
                    is Decimal -> JsonPrimitive(value.value)
                    is Flag -> JsonPrimitive(value.value)
                },
            )
        }

        override fun deserialize(decoder: Decoder): SupportValue {
            val json = decoder as? JsonDecoder ?: error("SupportValue is JSON only")
            val element = json.decodeJsonElement() as? JsonPrimitive ?: error("SupportValue is a number, string or boolean")
            if (element.isString) return Text(element.content)
            element.booleanOrNull?.let { return Flag(it) }
            element.longOrNull?.let { return Integer(it) }
            element.doubleOrNull?.let { return Decimal(it) }
            return Text(element.content)
        }
    }
}

/**
 * One of the person's recent events, attached so the developer sees what happened before the
 * request. Never put text the person wrote (a habit's name, a note) in [props].
 */
@Serializable
public data class SupportRecentEvent(
    val name: String,
    /** Epoch milliseconds. */
    val at: Long,
    val props: Map<String, SupportValue> = emptyMap(),
)

/** An in-app campaign shown recently (from kotlin-app-campaign's `campaign_shown`). */
@Serializable
public data class SupportCampaignShown(
    val campaign: String,
    @SerialName("set_version") val setVersion: Int,
    /** Epoch milliseconds. */
    val at: Long,
)

/** A help article in the product's index. The text itself is on the product's website at [url]. */
@Serializable
public data class HelpArticle(
    val slug: String,
    val lang: String,
    val title: String,
    val summary: String,
    val url: String,
    val tags: List<String> = emptyList(),
    /** Epoch milliseconds. */
    @SerialName("updated_at") val updatedAt: Long,
) {
    public val id: String get() = "$slug/$lang"
}

/** One message in a request: the person's, or the developer's reply. */
@Serializable
public data class SupportMessage(
    val id: String,
    val from: Author,
    val body: String,
    /** Epoch milliseconds. */
    val at: Long,
) {
    @Serializable
    public enum class Author {
        @SerialName("user") USER,
        @SerialName("support") SUPPORT,
    }
}

/** One of the person's requests with its messages, newest last. */
@Serializable
public data class SupportTicket(
    @SerialName("ticket_id") val id: String,
    val status: SupportTicketStatus,
    val subject: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val messages: List<SupportMessage>,
) {
    /** The latest reply from the developer, if any. */
    public val latestReply: SupportMessage? get() = messages.lastOrNull { it.from == SupportMessage.Author.SUPPORT }
}

/** An image attached to a request. */
@Serializable
public data class SupportAttachment(
    val id: String,
    @SerialName("content_type") val contentType: String,
    val bytes: Int,
    /** Epoch milliseconds. */
    val at: Long,
)

/** What the person wrote in the form. */
public data class SupportRequest(
    val category: SupportCategory,
    val subject: String? = null,
    val body: String,
    /** Only when the person also wants the reply by email. */
    val email: String? = null,
    /** Images to attach (up to 4). JPEG, PNG, HEIC or WebP, 5 MB each. */
    val images: List<SupportImage> = emptyList(),
)

/** An image ready to upload. Re-encode screenshots as JPEG under [MAX_BYTES] before attaching them. */
public class SupportImage(public val data: ByteArray, public val contentType: String) {
    public companion object {
        /** The server's limit for one image. */
        public const val MAX_BYTES: Int = 5_000_000
    }

    override fun equals(other: Any?): Boolean = other is SupportImage && other.contentType == contentType && other.data.contentEquals(data)
    override fun hashCode(): Int = 31 * data.contentHashCode() + contentType.hashCode()
}
