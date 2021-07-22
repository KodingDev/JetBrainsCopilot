package dev.koding.copilot.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import dev.koding.copilot.auth.handleLogin
import dev.koding.copilot.completion.api.CompletionRequest
import dev.koding.copilot.completion.api.CompletionResponse
import dev.koding.copilot.config.settings
import dev.koding.copilot.copilotIcon
import dev.koding.copilot.util.Notifications
import io.ktor.client.features.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max


class CopilotCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.isAutoPopup) return

        if (settings.token == null || settings.token?.isBlank() == true) return Notifications.send(
            "You have not set a token for GitHub Copilot.",
            type = NotificationType.ERROR,
            once = true,
            Notifications.NotificationAction("Login") { handleLogin() })

        val (prefix, suffix) = parameters.prefixSuffix
        val prompt = """
        // Language: ${parameters.originalFile.language.displayName}
        // Path: ${parameters.originalFile.name}
        ${parameters.prompt}
        """.trimIndent()

        val matcher = result.prefixMatcher
        val set = result
            .withPrefixMatcher(CopilotPrefixMatcher(matcher.cloneWithPrefix(matcher.prefix)))
            .withRelevanceSorter(CompletionSorter.defaultSorter(parameters, matcher).weigh(CopilotWeigher()))

        var response: CompletionResponse? = null
        var errored = false

        val job = GlobalScope.launch {
            try {
                response = CompletionRequest(prompt).send(settings.token!!)
            } catch (e: ClientRequestException) {
                e.printStackTrace()
                errored = true
                return@launch Notifications.send(
                    "Failed to fetch response. Is your copilot token valid?",
                    type = NotificationType.ERROR,
                    once = true,
                    Notifications.NotificationAction("Login") { handleLogin() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        while (response == null) {
            if (errored) return
            try {
                ProgressManager.getInstance().progressIndicator.checkCanceled()
                Thread.sleep(10)
            } catch (e: ProcessCanceledException) {
                job.cancel()
                return
            }
        }

        val choices = response!!.choices.filter { it.text.isNotBlank() }
        if (choices.isEmpty()) return

        set.restartCompletionOnAnyPrefixChange()
        set.addAllElements(choices.map { choice ->
            val completion = choice.text.removePrefix(prefix.trim()).removeSuffix(suffix.trim())
            val insert = "$prefix${completion.trim()}\n"

            LookupElementBuilder.create(choice, "")
                .withInsertHandler { context, _ ->
                    val caret = context.editor.caretModel
                    val startOffset = caret.visualLineStart
                    val endOffset = caret.visualLineEnd

                    context.document.deleteString(startOffset, endOffset)
                    context.document.insertString(startOffset, insert)
                    caret.moveToOffset(startOffset + insert.length - 1)
                }
                .withPresentableText(prefix.split(".").last().trim())
                .withTailText(completion, true)
                .withCaseSensitivity(false)
                .withTypeText("GitHub Copilot")
                .withIcon(copilotIcon)
                .bold()
        })
    }

    private val CompletionParameters.prompt: String
        get() {
            val document = editor.document
            val lineNumber = document.getLineNumber(offset)
            val startOffset = document.getLineStartOffset(max(0, lineNumber - settings.contentLines))
            return document.getText(TextRange(startOffset, offset))
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