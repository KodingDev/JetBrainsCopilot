package dev.koding.copilot.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import dev.koding.copilot.completion.api.CompletionChoice

class CopilotWeigher : LookupElementWeigher("CopilotLookupElementWeigher", false, true) {

    override fun weigh(element: LookupElement) =
        if (element.`object` is CompletionChoice) Int.MIN_VALUE else Int.MAX_VALUE

}