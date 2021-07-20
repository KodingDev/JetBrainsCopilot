@file:Suppress("EXPERIMENTAL_API_USAGE")

package dev.koding.copilot.auth

import com.google.gson.annotations.SerializedName
import com.intellij.ide.BrowserUtil
import dev.koding.copilot.config.settings
import dev.koding.copilot.httpClient
import dev.koding.copilot.util.Notifications
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.swing.JOptionPane

const val authenticateUrl = "https://vscode-auth.github.com/authorize/" +
        "?callbackUri=vscode://vscode.github-authentication/did-authenticate" +
        "&scope=read:user" +
        "&responseType=code" +
        "&authServer=https://github.com"

data class GitHubTokenResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("token_type")
    val tokenType: String?,
    val scope: String?
)

data class CopilotTokenResponse(
    val token: String,
    @SerializedName("expires_at")
    val expiry: Int
)

private suspend fun getAuthToken(url: String): CopilotTokenResponse {
    val code = Url(url).parameters["code"] ?: error("Code not present")

    val tokenResponse = httpClient.post<GitHubTokenResponse>("https://vscode-auth.github.com/token") {
        parameter("code", code)
        accept(ContentType.Application.Json)
    }

    return httpClient.get("https://api.github.com/copilot_internal/token") {
        header("Authorization", "token ${tokenResponse.accessToken ?: error("Invalid GitHub token")}")
    }
}

fun handleLogin(handler: (String) -> Unit = { Notifications.send("Login successful") }) {
    BrowserUtil.browse(authenticateUrl)
    val url = JOptionPane.showInputDialog("Enter the callback URL")

    // TODO: Change from global scope
    GlobalScope.launch {
        val token = getAuthToken(url).token
        settings.token = token
        handler(token)
    }
}