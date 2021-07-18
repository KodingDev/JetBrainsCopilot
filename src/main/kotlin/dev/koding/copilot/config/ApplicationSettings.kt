package dev.koding.copilot.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

val settings: ApplicationSettings
    get() = service()

@State(name = "GitHubCopilotSettings", storages = [Storage("copilot.xml")])
class ApplicationSettings : PersistentStateComponent<ApplicationSettings.State> {

    data class State(
        var token: String? = null,
        var contentLines: Int = 15
    )

    private var state = State()

    // TODO: Change this to delegation
    var token: String?
        get() = state.token
        set(value) = value.let { state.token = it }

    var contentLines: Int
        get() = state.contentLines
        set(value) = value.let { state.contentLines = it }

    override fun getState() = state
    override fun loadState(state: State) = state.let { this.state = it }
}
