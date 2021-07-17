package dev.koding.copilot

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher

class CopilotWeigher : LookupElementWeigher("CopilotLookupElementWeigher", false, true) {

    override fun weigh(element: LookupElement) =
        if (element.`object` is CopilotCompletionContributor.ResponseChoice) Int.MIN_VALUE else Int.MAX_VALUE

}