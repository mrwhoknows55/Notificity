package com.darshan.notificity

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Application.module(database: AppDatabase) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/ping-device") {
            call.respond(Build.DEVICE)
        }
        get("/notifications") {
            val notifications: List<NotificationEntity> = database.notificationDao().getNotifications(50, 0)
            call.respond(notifications)
        }
    }
}

class NotificityListener : NotificationListenerService() {
    private lateinit var database: AppDatabase
    private lateinit var server: NettyApplicationEngine


    private fun startServer() {
        server = embeddedServer(Netty, port = 1337, module = {
            module(database)
        })
        Log.d("Server", "startServer: ${server.application.log}")
        server.start()
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize the Room database
        database = AppDatabase.getInstance(applicationContext)
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE, "")
        val text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString()
        val timestamp = sbn.postTime
        val image = extras.getString(Notification.EXTRA_PICTURE)

        //Find App Name from Package Name
        val pm = applicationContext.packageManager
        val ai: ApplicationInfo? = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: NameNotFoundException) {
            null
        }
        val applicationName =
            (if (ai != null) pm.getApplicationLabel(ai) else "(unknown)") as String

        // Create a new notification entity
        val notificationEntity = NotificationEntity(
            packageName = packageName,
            timestamp = timestamp,
            appName = applicationName,
            title = title,
            content = text,
            imageUrl = image,
            extras = extras.toString()
        )

        if (notificationEntity.content.isNotEmpty() && notificationEntity.title.isNotEmpty()) {
            // Insert the notification into the database using coroutines
            CoroutineScope(Dispatchers.IO).launch {
                database.notificationDao().insertNotification(notificationEntity)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle removed notifications if necessary
    }
}

