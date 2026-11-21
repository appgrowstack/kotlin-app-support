package com.appgrowstack.support

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Help articles shipped in the app: a JSON file with the same shape as a support server's article
 * list (`{ "articles": [ { "slug", "lang", "title", "summary", "url", "tags", "updated_at" } ] }`).
 * The text itself is on your website at each `url`.
 */
public class BundledHelpArticles(private val all: List<HelpArticle>) : HelpArticleSource {
    public companion object {
        @Serializable
        private data class ArticleList(val articles: List<HelpArticle>)

        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the JSON (an asset's text). Unreadable text is an empty list. */
        public fun fromJson(text: String): BundledHelpArticles =
            BundledHelpArticles(runCatching { json.decodeFromString(ArticleList.serializer(), text).articles }.getOrDefault(emptyList()))
    }

    override suspend fun articles(lang: String): List<HelpArticle> {
        val base = lang.split("-").first()
        for (candidate in listOf(lang, base, "en")) {
            val found = all.filter { it.lang == candidate }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }
}

/**
 * Finds help articles for what the person is typing, so the answer can come before the request.
 * Works for languages without spaces: it matches words and, for CJK text, runs of two characters.
 * The same as swift-app-support's `HelpSearch`.
 */
public object HelpSearch {
    /** Articles ranked by how well they match [query]. Articles that don't match are left out. */
    public fun search(query: String, articles: List<HelpArticle>, limit: Int = 5): List<HelpArticle> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        return articles.mapNotNull { article ->
            val title = fold(article.title)
            val body = fold(article.summary + " " + article.tags.joinToString(" "))
            var score = 0.0
            for (t in terms) {
                if (title.contains(t)) score += 3
                if (body.contains(t)) score += 1
            }
            val ratio = score / (terms.size * 4)
            if (ratio >= 0.15) article to ratio else null
        }
            .sortedWith(compareByDescending<Pair<HelpArticle, Double>> { it.second }.thenBy { it.first.title })
            .take(limit)
            .map { it.first }
    }

    private val combiningLatin = Regex("[\\u0300-\\u036f]")

    /** Ignores case, accents on Latin letters and full-width forms. */
    internal fun fold(s: String): String {
        val wide = Normalizer.normalize(s, Normalizer.Form.NFKD)
        return Normalizer.normalize(combiningLatin.replace(wide, ""), Normalizer.Form.NFC).lowercase()
    }

    /** Words of two or more letters; CJK runs become overlapping pairs (「解約したい」→ 解約・約し). */
    internal fun terms(query: String): List<String> {
        val out = mutableListOf<String>()
        val word = StringBuilder()
        val cjk = mutableListOf<Char>()
        fun flushWord() {
            if (word.length >= 2) out += word.toString()
            word.clear()
        }
        fun flushCjk() {
            if (cjk.size == 1) {
                out += cjk[0].toString()
            } else if (cjk.size >= 2) {
                // ひらがなだけの組（した・たい）は文法なので、漢字やカタカナの混じる文では数えない
                val mixed = cjk.any { !isHiragana(it) }
                for (i in 0 until cjk.size - 1) {
                    if (mixed && isHiragana(cjk[i]) && isHiragana(cjk[i + 1])) continue
                    out += "${cjk[i]}${cjk[i + 1]}"
                }
            }
            cjk.clear()
        }
        for (ch in fold(query)) {
            when {
                isCjk(ch) -> {
                    flushWord()
                    cjk += ch
                }
                ch.isLetterOrDigit() -> {
                    flushCjk()
                    word.append(ch)
                }
                else -> {
                    flushWord()
                    flushCjk()
                }
            }
        }
        flushWord()
        flushCjk()
        return out.toSet().sorted()
    }

    internal fun isHiragana(ch: Char): Boolean = ch.code in 0x3040..0x309F

    internal fun isCjk(ch: Char): Boolean {
        val v = ch.code
        return v in 0x3040..0x30FF || v in 0x3400..0x9FFF || v in 0xAC00..0xD7AF || v in 0xF900..0xFAFF
    }
}
