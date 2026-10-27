package com.tidytroupe

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import com.tidytroupe.core.ChoreEngine
import com.tidytroupe.data.Chore
import com.tidytroupe.data.TidyRepository
import com.tidytroupe.data.isDueOn
import com.tidytroupe.data.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now()
        if (now.hour < 8 || now.hour >= 20) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("chores", applicationContext.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1, NotificationCompat.Builder(applicationContext, "chores").setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(applicationContext.getString(R.string.notification_title)).setContentText(applicationContext.getString(R.string.notification_body)).setContentIntent(open).setAutoCancel(true).build())
        return Result.success()
    }
    companion object {
        fun configure(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) { manager.cancelUniqueWork("daily-teamwork"); return }
            val now = ZonedDateTime.now()
            var due = now.withHour(18).withMinute(0).withSecond(0)
            if (due <= now) due = due.plusDays(1)
            manager.enqueueUniquePeriodicWork("daily-teamwork", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(Duration.between(now, due).toMillis(), TimeUnit.MILLISECONDS).build())
        }
    }
}

/** Calendar-aligned, opt-in reminder work. No exact-alarm permission or Google services are used. */
object ChoreReminders {
    private const val TAG = "tidy-chore-reminders"
    private const val PREFS = "chore_reminder_schedule"
    private val mutex = Mutex()
    private fun name(id: String) = "tidy-chore-reminder:$id"
    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call after chore changes and on foreground/timezone changes. WorkManager persists requests. */
    suspend fun sync(context: Context, chores: List<Chore>) = mutex.withLock {
        val prefs = preferences(context)
        val old = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty().toSet()
        val enabled = chores.filter { it.reminderMinutes != null && !it.paused && !it.archived }
        val enabledIds = enabled.map { it.id }.toSet()
        val manager = WorkManager.getInstance(context)
        (old - enabledIds).forEach { manager.cancelUniqueWork(name(it)); prefs.edit().remove("request:$it").apply() }
        enabled.forEach { enqueue(context, it, append = false) }
        prefs.edit().putStringSet("scheduled_ids", enabledIds).apply()
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        preferences(context).edit().clear().apply()
    }

    private fun nextTime(context: Context, chore: Chore, now: ZonedDateTime): ZonedDateTime? {
        val minutes = chore.reminderMinutes ?: return null
        if (minutes !in 480..1199 || chore.paused || chore.archived) return null
        val lastDelivered = preferences(context).getString("delivered:${chore.id}", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var from = maxOf(now.toLocalDate(), lastDelivered?.plusDays(1) ?: now.toLocalDate())
        repeat(2) {
            val occurrence = runCatching { ChoreEngine.nextDue(chore.toDomain(), from) }.getOrNull() ?: return null
            // atZone resolves DST gaps and repeated offsets using the platform's timezone rules.
            val at = occurrence.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(now.zone)
            if (at > now) return at
            from = occurrence.plusDays(1)
        }
        return null
    }

    private fun enqueue(context: Context, chore: Chore, append: Boolean) {
        val now = ZonedDateTime.now()
        val next = nextTime(context, chore, now)
        val prefs = preferences(context)
        if (next == null) {
            if (!append) WorkManager.getInstance(context).cancelUniqueWork(name(chore.id))
            prefs.edit().remove("request:${chore.id}").apply()
            return
        }
        val signature = "${next.toInstant()}:${next.toLocalDate()}:${chore.reminderMinutes}"
        if (!append && prefs.getString("request:${chore.id}", null) == signature) return
        val request = OneTimeWorkRequestBuilder<ChoreReminderWorker>()
            .setInputData(workDataOf("choreId" to chore.id, "occurrence" to next.toLocalDate().toString()))
            .setInitialDelay(maxOf(0, Duration.between(now, next).toMillis()), TimeUnit.MILLISECONDS)
            .addTag(TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(chore.id), if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
        prefs.edit().putString("request:${chore.id}", signature).apply()
    }

    internal suspend fun finished(context: Context, chore: Chore) = mutex.withLock {
        enqueue(context, chore, append = true)
    }

    internal suspend fun notifyOnce(context: Context, chore: Chore, occurrence: LocalDate) = mutex.withLock {
        val prefs = preferences(context)
        val previous = prefs.getString("delivered:${chore.id}", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (previous != null && previous >= occurrence) return@withLock
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withLock
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return@withLock
        manager.createNotificationChannel(NotificationChannel("chores", context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, "chores").setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(context.getString(R.string.app_name)).setContentText(context.getString(R.string.notification_body)).build()
        val notification = NotificationCompat.Builder(context, "chores").setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notification_title)).setContentText(chore.title)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(open).setAutoCancel(true).build()
        // Persist before posting: a process crash may skip one reminder, but cannot post it twice.
        if (!prefs.edit().putString("delivered:${chore.id}", occurrence.toString()).commit()) return@withLock
        manager.notify(name(chore.id), 2, notification)
    }
}

class ChoreReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("choreId") ?: return Result.success()
        val occurrence = inputData.getString("occurrence")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return Result.success()
        val repo = TidyRepository(applicationContext)
        return try {
            val chore = repo.chores.first().find { it.id == id } ?: return Result.success()
            val now = ZonedDateTime.now()
            val minutes = chore.reminderMinutes
            val completed = repo.completions.first().any { it.choreId == id && it.date == occurrence.toString() }
            if (minutes != null && minutes in 480..1199 && now.hour in 8..19 &&
                now.hour * 60 + now.minute >= minutes && occurrence == now.toLocalDate() &&
                chore.isDueOn(occurrence) && !completed) {
                ChoreReminders.notifyOnce(applicationContext, chore, occurrence)
            }
            ChoreReminders.finished(applicationContext, chore)
            Result.success()
        } catch (canceled: CancellationException) { throw canceled }
        catch (_: Exception) { Result.retry() }
        finally { repo.close() }
    }
}

/** Re-evaluates wall-clock schedules after timezone/date changes rather than assuming 24h days. */
class ReminderTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = TidyRepository(context)
            try {
                ChoreReminders.sync(context, repository.chores.first())
                val enabled = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("reminder", false)
                ReminderWorker.configure(context, enabled)
            } finally { repository.close(); pending.finish() }
        }
    }
}

