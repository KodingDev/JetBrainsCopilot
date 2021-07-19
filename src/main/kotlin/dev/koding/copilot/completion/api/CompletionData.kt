package dev.koding.copilot.completion.api

import com.google.gson.annotations.SerializedName
import dev.koding.copilot.httpClient
import io.ktor.client.request.*

data class CompletionResponse(
    val choices: List<CompletionChoice>
)

data class CompletionChoice(
    val text: String
)

data class CompletionRequest(
    val prompt: String,
    @SerializedName("max_tokens")
    val maxTokens: Int = 70,
    val temperature: Double = 0.2,
    @SerializedName("top_p")
    val topP: Double = 1.0,
    @SerializedName("n")
    val count: Int = 3,
    @SerializedName("logprobs")
    val logProbability: Int = 2,
    val stop: List<String> = listOf("\n")
) {
    suspend fun send(token: String): CompletionResponse =
        httpClient.post("https://copilot.githubassets.com/v1/engines/github-multi-stochbpe-cushman-pii/completions") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            header("Accept", "application/json")
            header("Openai-Organization", "github-copilot")
            header("OpenAI-Intent", "copilot-ghost")

            body = this@CompletionRequest
        }
}