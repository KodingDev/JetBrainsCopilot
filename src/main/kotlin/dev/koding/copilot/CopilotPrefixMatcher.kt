package dev.koding.copilot

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator

class CopilotPrefixMatcher(private val inner: PrefixMatcher) : PrefixMatcher(inner.prefix) {
    override fun prefixMatches(element: LookupElement): Boolean {
        if (element.`object` is CopilotCompletionContributor.ResponseChoice) return true
        else if (element is LookupElementDecorator<*>) return prefixMatches(element.delegate)
        return super.prefixMatches(element)
    }

    override fun isStartMatch(element: LookupElement?): Boolean {
        if (element?.`object` is CopilotCompletionContributor.ResponseChoice) return true
        return super.isStartMatch(element)
    }

    override fun prefixMatches(name: String) = inner.prefixMatches(name)
    override fun cloneWithPrefix(prefix: String) = CopilotPrefixMatcher(inner.cloneWithPrefix(prefix))
}