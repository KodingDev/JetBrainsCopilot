package dev.koding.copilot

import com.intellij.openapi.util.IconLoader
import io.ktor.client.*
import io.ktor.client.features.json.*

val httpClient = HttpClient {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

val copilotIcon = IconLoader.findIcon("/icons/icon.png")