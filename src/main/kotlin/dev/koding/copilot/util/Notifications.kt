package dev.koding.copilot.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

object Notifications {

    private val group = NotificationGroupManager.getInstance().getNotificationGroup("GitHub Copilot")
    private var shown = mutableSetOf<String>()

    @Suppress("DialogTitleCapitalization")
    fun send(
        message: String,
        type: NotificationType = NotificationType.INFORMATION,
        once: Boolean = false,
        vararg actions: NotificationAction
    ) {
        if (once && message in shown) return
        group.createNotification(message, type)
            .apply {
                actions.forEach {
                    addAction(object : AnAction(it.name) {
                        override fun actionPerformed(e: AnActionEvent) = it.action()
                    })
                }
                shown += message
            }.notify(null)
    }

    data class NotificationAction(
        val name: String,
        val action: () -> Unit
    )
}