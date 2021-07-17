package dev.koding.copilot.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import dev.koding.copilot.completion.api.CompletionRequest
import dev.koding.copilot.copilotIcon
import kotlinx.coroutines.runBlocking
import kotlin.math.max


class CopilotCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.isAutoPopup) return

        val prompt = """
        // Language: ${parameters.originalFile.language.displayName}
        // Path: ${parameters.originalFile.name}
        ${getPrompt(parameters)}
        """.trimIndent()

        val (prefix, suffix) = parameters.prefixSuffix
        val response = ApplicationUtil.runWithCheckCanceled({
            return@runWithCheckCanceled runBlocking { CompletionRequest(prompt).send(System.getenv("GITHUB_COPILOT_TOKEN")) }
        }, ProgressManager.getInstance().progressIndicator) ?: return

        val choices = response.choices.filter { it.text.isNotBlank() }
        if (choices.isEmpty()) return

        val originalMatcher = result.prefixMatcher
        val set =
            result.withPrefixMatcher(CopilotPrefixMatcher(originalMatcher.cloneWithPrefix(originalMatcher.prefix)))
                .withRelevanceSorter(
                    CompletionSorter.defaultSorter(parameters, originalMatcher)
                        .weigh(CopilotWeigher())
                )

        set.restartCompletionOnAnyPrefixChange()
        set.addAllElements(choices.map { choice ->
            val completion = choice.text.removePrefix(prefix).removeSuffix(suffix)
                .let {
                    val split = prefix.split(".")
                    if (split.size >= 2) "${split.last()}$it" else it
                }

            PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(choice, completion)
                    .withPresentableText(prefix)
                    .withTailText(completion, true)
                    .withTypeText("GitHub Copilot")
                    .withIcon(copilotIcon)
                    .bold(), Double.MAX_VALUE
            )
        })
    }

    private fun getPrompt(parameters: CompletionParameters): String {
        // Using the parameters, get the last 10 lines of the current editor document and return their text
        val lineNumber = parameters.editor.document.getLineNumber(parameters.offset)
        val startOffset = parameters.editor.document.getLineStartOffset(max(0, lineNumber - 10))
        return parameters.editor.document.getText(TextRange(startOffset, parameters.offset))
    }

    private val CompletionParameters.prefixSuffix: Pair<String, String>
        get() {
            val document = editor.document
            val lineNumber = document.getLineNumber(offset)

            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)

            val start = document.getText(TextRange.create(lineStart, offset))
            val end = document.getText(TextRange.create(offset, lineEnd)).trim()
            return start to end
        }

}