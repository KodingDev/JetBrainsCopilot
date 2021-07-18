package dev.koding.copilot.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import dev.koding.copilot.completion.api.CompletionRequest
import dev.koding.copilot.completion.api.CompletionResponse
import dev.koding.copilot.copilotIcon
import dev.koding.copilot.copilotToken
import io.ktor.client.features.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max


class CopilotCompletionContributor : CompletionContributor() {

    private var notified = false

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.isAutoPopup) return

        if (copilotToken == null) {
            if (notified) return
            @Suppress("DialogTitleCapitalization")
            Notification(
                "Error Report",
                "GitHub Copilot",
                "You have not set a token for GitHub Copilot.",
                NotificationType.ERROR
            ).notify(parameters.editor.project)
            return run { notified = true }
        }

        val prompt = """
        // Language: ${parameters.originalFile.language.displayName}
        // Path: ${parameters.originalFile.name}
        ${getPrompt(parameters)}
        """.trimIndent()

        val (prefix, suffix) = parameters.prefixSuffix

        var response: CompletionResponse? = null
        val job = GlobalScope.launch {
            try {
                response = CompletionRequest(prompt).send(copilotToken)
            } catch (e: ClientRequestException) {
                if (!notified) {
                    @Suppress("DialogTitleCapitalization")
                    Notification(
                        "Error Report",
                        "GitHub Copilot",
                        "Failed to fetch response. Is your <code>GITHUB_COPILOT_TOKEN</code> environment variable up to date?",
                        NotificationType.ERROR
                    ).notify(parameters.editor.project)
                    notified = true
                }

                return@launch result.stopHere()
            }
        }

        if (result.isStopped) return
        while (response == null) {
            try {
                ProgressManager.getInstance().progressIndicator.checkCanceled()
                Thread.sleep(10)
            } catch (e: ProcessCanceledException) {
                job.cancel()
                return result.stopHere()
            }
        }

        val choices = response!!.choices.filter { it.text.isNotBlank() }
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
            val completion = choice.text.removePrefix(prefix.trim()).removeSuffix(suffix.trim())
            val insert = "$prefix${completion.trim()}\n"

            PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(choice, "")
                    .withInsertHandler { context, _ ->
                        val caret = context.editor.caretModel
                        val startOffset = caret.visualLineStart
                        val endOffset = caret.visualLineEnd

                        context.document.deleteString(startOffset, endOffset)
                        context.document.insertString(startOffset, insert)
                        caret.moveToOffset(startOffset + insert.length - 1)
                    }
                    .withPresentableText(prefix.split(".").last())
                    .withTailText(completion, true)
                    .withTypeText("GitHub Copilot")
                    .withIcon(copilotIcon)
                    .bold(), Double.MAX_VALUE
            )
        })
    }

    private fun getPrompt(parameters: CompletionParameters): String {
        val lineNumber = parameters.editor.document.getLineNumber(parameters.offset)
        val startOffset = parameters.editor.document.getLineStartOffset(max(0, lineNumber - 15))
        return parameters.editor.document.getText(TextRange(startOffset, parameters.offset))
    }

    private val CompletionParameters.prefixSuffix: Pair<String, String>
        get() {
            val document = editor.document
            val lineNumber = document.getLineNumber(offset)

            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)

            val start = document.getText(TextRange.create(lineStart, offset))
            val end = document.getText(TextRange.create(offset, lineEnd))
            return start to end
        }

}