package org.ward.adhkar.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.ward.adhkar.MainActivity
import org.ward.adhkar.R
import org.ward.adhkar.data.DhikrRepository
import org.ward.adhkar.data.Period
import org.ward.adhkar.data.ReminderClock
import org.ward.adhkar.data.WardStore
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Daily, one-time follow-up and user-requested snooze alarms.
 * All alarms are inexact: this app intentionally avoids exact-alarm permissions.
 */
object ReminderScheduler {
    private const val CHANNEL_ID = "ward_daily_reminders"
    const val EXTRA_PERIOD = "period"
    const val EXTRA_KIND = "reminder_kind"
    const val EXTRA_DAY = "reminder_day"
    const val KIND_DAILY = "daily"
    const val KIND_FOLLOW_UP = "follow_up"
    const val KIND_SNOOZE = "snooze"

    private const val FOLLOW_UP_MINUTES = 60L
    private const val SNOOZE_MINUTES = 20L

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
        }
        // Keep the original channel ID so existing custom ringtones are preserved.
        manager.createNotificationChannel(channel)
    }

    fun sync(context: Context) = Period.entries.forEach { schedule(context, it) }

    private fun requestCode(period: Period, kind: String): Int {
        val offset = if (period == Period.MORNING) 1 else 2
        return when (kind) {
            KIND_FOLLOW_UP -> 4000 + offset
            KIND_SNOOZE -> 6000 + offset
            else -> 1000 + offset // Preserve legacy daily PendingIntent identity.
        }
    }

    private fun pendingAlarm(context: Context, period: Period, kind: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_PERIOD, period.id)
            .putExtra(EXTRA_KIND, kind)
        if (kind != KIND_DAILY) intent.putExtra(EXTRA_DAY, LocalDate.now().toString())
        return PendingIntent.getBroadcast(
            context, requestCode(period, kind), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarms(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(context: Context, period: Period) {
        val alarmManager = alarms(context)
        val pending = pendingAlarm(context, period, KIND_DAILY)
        alarmManager.cancel(pending)
        if (!WardStore(context).isReminderEnabled(period)) {
            cancelPending(context, period)
            return
        }
        val next = ReminderClock.nextOccurrence(
            ZonedDateTime.now(), WardStore(context).reminderTime(period)
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending
        )
    }

    private fun scheduleOneTime(context: Context, period: Period, kind: String, minutes: Long) {
        val pending = pendingAlarm(context, period, kind)
        val alarmManager = alarms(context)
        alarmManager.cancel(pending)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pending
        )
    }

    fun followUp(context: Context, period: Period) {
        if (WardStore(context).followUpEnabled) {
            scheduleOneTime(context, period, KIND_FOLLOW_UP, FOLLOW_UP_MINUTES)
        }
    }

    fun snooze(context: Context, period: Period) {
        // Avoid two reminders appearing close together after snooze.
        alarms(context).cancel(pendingAlarm(context, period, KIND_FOLLOW_UP))
        scheduleOneTime(context, period, KIND_SNOOZE, SNOOZE_MINUTES)
    }

    fun cancelPending(context: Context, period: Period) {
        val alarmManager = alarms(context)
        alarmManager.cancel(pendingAlarm(context, period, KIND_FOLLOW_UP))
        alarmManager.cancel(pendingAlarm(context, period, KIND_SNOOZE))
    }

    fun cancelFollowUps(context: Context) {
        Period.entries.forEach {
            alarms(context).cancel(pendingAlarm(context, it, KIND_FOLLOW_UP))
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, period: Period): Boolean {
        if (!hasPermission(context)) return false
        val store = WardStore(context)
        val entries = runCatching {
            DhikrRepository.load(context).filter { it.belongsTo(period) }
        }.getOrNull() ?: return false
        if (entries.isEmpty()) return false
        val completed = entries.count { store.count(period, it.id) >= it.target }
        val remaining = entries.size - completed
        if (remaining == 0) return false

        val open = Intent(context, MainActivity::class.java)
            .putExtra("open_period", period.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPending = PendingIntent.getActivity(
            context, if (period == Period.MORNING) 2001 else 2002,
            open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionIntent = Intent(context, ReminderActionReceiver::class.java)
            .putExtra(EXTRA_PERIOD, period.id)
        val snoozePending = PendingIntent.getBroadcast(
            context, if (period == Period.MORNING) 8001 else 8002,
            actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (completed > 0) {
            "بقي $remaining من أذكار ${period.label}"
        } else if (period == Period.MORNING) {
            "حان وقت أذكار الصباح"
        } else {
            "حان وقت أذكار المساء"
        }
        val body = if (completed > 0) {
            "أكملت $completed من ${entries.size} أذكار. افتح وِرد لإتمام الباقي."
        } else {
            "ابدأ وِرد ${period.label} اليوم. اضغط لقراءة الأذكار وعدّها."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_notification, "ابدأ الورد", openPending)
            .addAction(R.drawable.ic_notification, "ذكّرني بعد 20 دقيقة", snoozePending)
            .build()
        NotificationManagerCompat.from(context).notify(
            if (period == Period.MORNING) 3001 else 3002, notification
        )
        return true
    }

    fun dismiss(context: Context, period: Period) {
        NotificationManagerCompat.from(context).cancel(
            if (period == Period.MORNING) 3001 else 3002
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = Period.fromId(intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD))
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)
            ?: ReminderScheduler.KIND_DAILY
        val store = WardStore(context)
        if (!store.isReminderEnabled(period)) return
        if (kind != ReminderScheduler.KIND_DAILY &&
            intent.getStringExtra(ReminderScheduler.EXTRA_DAY) != LocalDate.now().toString()
        ) return // Prevent yesterday's snooze/follow-up firing on a new day's count.

        ReminderScheduler.createChannel(context)
        val shown = ReminderScheduler.show(context, period)
        if (kind == ReminderScheduler.KIND_DAILY) {
            if (shown) ReminderScheduler.followUp(context, period)
            ReminderScheduler.schedule(context, period)
        }
        if (!shown) ReminderScheduler.cancelPending(context, period)
    }
}

/** Action on the notification itself; a snooze does not change the regular daily time. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = Period.fromId(intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD))
        if (!WardStore(context).isReminderEnabled(period)) return
        val complete = runCatching {
            val entries = DhikrRepository.load(context)
            WardStore(context).isSessionComplete(period, entries)
        }.getOrDefault(true)
        if (!complete) ReminderScheduler.snooze(context, period)
        ReminderScheduler.dismiss(context, period)
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) ReminderScheduler.sync(context)
    }
}
