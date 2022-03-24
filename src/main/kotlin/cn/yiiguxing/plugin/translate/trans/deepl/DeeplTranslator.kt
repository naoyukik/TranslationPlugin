@file:Suppress("SpellCheckingInspection")

package cn.yiiguxing.plugin.translate.trans.deepl

import cn.yiiguxing.plugin.translate.trans.*
import cn.yiiguxing.plugin.translate.ui.settings.TranslationEngine.DEEPL
import cn.yiiguxing.plugin.translate.util.Http
import cn.yiiguxing.plugin.translate.util.Settings
import cn.yiiguxing.plugin.translate.util.i
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import org.jsoup.nodes.Document
import javax.swing.Icon

/**
 * Deepl translator
 */
object DeeplTranslator : AbstractTranslator(), DocumentationTranslator {

    private const val DEEPL_FREE_TRANSLATE_API_URL = "https://api-free.deepl.com/v2/translate"
    private const val DEEPL_PRO_TRANSLATE_API_URL = "https://api.deepl.com/v2/translate"


    /** 通用版支持的语言列表 */
    private val SUPPORTED_LANGUAGES: List<Lang> = listOf(
        Lang.BULGARIAN,
        Lang.CHINESE,
        Lang.CZECH,
        Lang.DANISH,
        Lang.DUTCH,
        Lang.GERMAN,
        Lang.GREEK,
        Lang.ENGLISH,
        Lang.ESTONIAN,
        Lang.FINNISH,
        Lang.FRENCH,
        Lang.HUNGARIAN,
        Lang.ITALIAN,
        Lang.JAPANESE,
        Lang.LATVIAN,
        Lang.LITHUANIAN,
        Lang.POLISH,
        Lang.PORTUGUESE,
        Lang.ROMANIAN,
        Lang.RUSSIAN,
        Lang.SLOVAK,
        Lang.SLOVENIAN,
        Lang.SPANISH,
        Lang.SWEDISH,
    )

    private val SUPPORTED_TARGET_LANGUAGES: List<Lang> = listOf(
        Lang.BULGARIAN,
        Lang.CHINESE,
        Lang.CZECH,
        Lang.DANISH,
        Lang.DUTCH,
        Lang.GERMAN,
        Lang.GREEK,
        Lang.ENGLISH,
        Lang.ENGLISH_AMERICAN,
        Lang.ENGLISH_BRITISH,
        Lang.ESTONIAN,
        Lang.FINNISH,
        Lang.FRENCH,
        Lang.HUNGARIAN,
        Lang.ITALIAN,
        Lang.JAPANESE,
        Lang.LATVIAN,
        Lang.LITHUANIAN,
        Lang.POLISH,
        Lang.PORTUGUESE,
        Lang.PORTUGUESE_BRAZILIAN,
        Lang.PORTUGUESE_PORTUGUESE,
        Lang.ROMANIAN,
        Lang.RUSSIAN,
        Lang.SLOVAK,
        Lang.SLOVENIAN,
        Lang.SPANISH,
        Lang.SWEDISH,
    )

    private val logger: Logger = Logger.getInstance(DeeplTranslator::class.java)

    override val id: String = DEEPL.id

    override val name: String = DEEPL.translatorName

    override val icon: Icon = DEEPL.icon

    override val intervalLimit: Int = DEEPL.intervalLimit

    override val contentLengthLimit: Int = DEEPL.contentLengthLimit

    override val primaryLanguage: Lang
        get() = DEEPL.primaryLanguage

    override val supportedSourceLanguages: List<Lang> = SUPPORTED_LANGUAGES
        .toMutableList()
        .apply { add(0, Lang.AUTO) }
    override val supportedTargetLanguages: List<Lang> = SUPPORTED_TARGET_LANGUAGES

    override fun checkConfiguration(force: Boolean): Boolean {
        if (force || Settings.deeplTranslateSettings.let { it.appId.isEmpty() || it.getAppKey().isEmpty() }) {
            return DEEPL.showConfigurationDialog()
        }

        return true
    }

    override fun doTranslate(text: String, srcLang: Lang, targetLang: Lang): Translation {
        return SimpleTranslateClient(
            this,
            { _, _, _ ->call(text, srcLang, targetLang, false) },
            DeeplTranslator::parseTranslation
        ).execute(text, srcLang, targetLang)
    }

    private fun call(text: String, srcLang: Lang, targetLang: Lang, isDocument: Boolean): String {
        val settings = Settings.deeplTranslateSettings
        val privateKey = settings.getAppKey()
        val splitPrivatekeys = privateKey.split(":")
        val isFree = (splitPrivatekeys.size == 2 && splitPrivatekeys[1] == "fx")
        val requestURL = if (isFree) DEEPL_FREE_TRANSLATE_API_URL else DEEPL_PRO_TRANSLATE_API_URL
        val postData: LinkedHashMap<String, String> = linkedMapOf(
            "auth_key" to privateKey,
            "target_lang" to targetLang.deeplLanguageCode,
            "text" to text
        )

        if (isDocument) {
            postData["tag_handling"] = "html"
        }

        if (srcLang !== Lang.AUTO) {
            postData["source_lang"] = srcLang.langName
        }

        return Http.post(
            requestURL,
            postData
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun parseTranslation(translation: String, original: String, srcLang: Lang, targetLang: Lang): Translation {
        logger.i("Translate result: $translation")

        return Gson().fromJson(translation, DeeplTranslations::class.java).apply {
            this.original = original
            this.targetLang = targetLang
            if (!isSuccessful) {
                throw TranslationResultException(translations[0].code)
            }
        }.toTranslation()
    }

    override fun translateDocumentation(documentation: Document, srcLang: Lang, targetLang: Lang): Document {
        return checkError {
                processAndTranslateDocumentation(documentation) {
                translateDocumentation(it, srcLang, targetLang)
            }
        }
    }

    private fun processAndTranslateDocumentation(
        documentation: Document,
        translate: (String) -> String
    ): Document {
        val body = documentation.body()
        val content = body.html()
        if (content.isBlank()) {
            return documentation
        }

        val translation = translate(content)

        body.html(translation)

        return documentation
    }

    private fun translateDocumentation(documentation: String, srcLang: Lang, targetLang: Lang): String {
        return SimpleTranslateClient(
            this,
            { _, _, _ ->call(documentation, srcLang, targetLang, true) },
            DeeplTranslator::parseTranslation
        ).execute(documentation, srcLang, targetLang).translation ?: ""
    }
}
