package dev.koding.copilot

import com.google.gson.annotations.SerializedName
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.TextRange
import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking


val client = HttpClient {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

val icon = IconLoader.findIcon("/icons/icon.png")

class CopilotCompletionContributor : CompletionContributor() {

    data class Response(
        val choices: List<ResponseChoice>
    )

    data class ResponseChoice(
        val text: String
    )

    data class Request(
        val prompt: String,
        @SerializedName("max_tokens")
        val maxTokens: Int = 50,
        val temperature: Double = 0.2,
        @SerializedName("top_p")
        val topP: Double = 1.0,
        val n: Int = 3,
        val logprobs: Int = 2,
        val stop: List<String> = listOf("\n")
    )

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.isAutoPopup) return

        val prompt = getPrompt(parameters)
        val sentPrompt =
            "// Language: ${parameters.originalFile.language.displayName}\n// Path: ${parameters.originalFile.name}\n$prompt"

        val prefix = getCursorPrefix(parameters)
        val suffix = getCursorSuffix(parameters)

        val response = ApplicationUtil.runWithCheckCanceled({
            return@runWithCheckCanceled runBlocking {
                client.post<Response>("https://copilot.githubassets.com/v1/engines/github-multi-stochbpe-cushman-pii/completions") {
                    header(
                        "Authorization",
                        "Bearer ${System.getenv("GITHUB_COPILOT_TOKEN")}"
                    )
                    header("Content-Type", "application/json")
                    header("Accept", "application/json")
                    header("Openai-Organization", "github-copilot")

                    body = Request(sentPrompt)
                }
            }
        }, ProgressManager.getInstance().progressIndicator)

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
        set.addAllElements(choices.map {
            val completion = it.text.split("\n").first()
            val preview = completion.removePrefix(prefix).removeSuffix(suffix)

            PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(it, preview)
                    .withPresentableText(prefix)
                    .withTailText(preview, true)
                    .withTypeText("GitHub Copilot")
                    .withIcon(icon)
                    .bold(), Double.MAX_VALUE
            )
        })
    }

    private fun getPrompt(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val cursorPosition = parameters.offset
        val lineNumber = document.getLineNumber(cursorPosition)

        val minLineOffset = document.getLineStartOffset(lineNumber - 8)
        val text = document.getText(TextRange.create(minLineOffset, cursorPosition))
        return if (text.split("\n").last().isBlank()) text else text.trim()
    }

    private fun getCursorPrefix(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val cursorPosition = parameters.offset
        val lineNumber = document.getLineNumber(cursorPosition)
        val lineStart = document.getLineStartOffset(lineNumber)
        return document.getText(TextRange.create(lineStart, cursorPosition)).trim()
    }

    private fun getCursorSuffix(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val cursorPosition = parameters.offset
        val lineNumber = document.getLineNumber(cursorPosition)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange.create(cursorPosition, lineEnd)).trim()
    }

}