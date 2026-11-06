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
import org.ward.adhkar.data.ReminderClock
import org.ward.adhkar.data.DhikrRepository
import org.ward.adhkar.data.Period
import org.ward.adhkar.data.WardStore
import java.time.ZonedDateTime

/** Inexact daily reminders avoid the restricted exact-alarm permission. */
object ReminderScheduler {
    private const val CHANNEL_ID = "ward_daily_reminders"
    const val EXTRA_PERIOD = "period"

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
        manager.createNotificationChannel(channel)
    }

    fun sync(context: Context) = Period.entries.forEach { schedule(context, it) }

    fun schedule(context: Context, period: Period) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_PERIOD, period.id)
        val requestCode = if (period == Period.MORNING) 1001 else 1002
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        val store = WardStore(context)
        if (!store.isReminderEnabled(period)) return

        val desiredTime = store.reminderTime(period)
        val now = ZonedDateTime.now()
        val next = ReminderClock.nextOccurrence(now, desiredTime)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending
        )
    }

    fun show(context: Context, period: Period) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra("open_period", period.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, if (period == Period.MORNING) 2001 else 2002,
            open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (period == Period.MORNING) "حان وقت أذكار الصباح" else "حان وقت أذكار المساء"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("لحظات من الذكر تمنح يومك سكينة. افتح وِردك الآن.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(
            if (period == Period.MORNING) 3001 else 3002, notification
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val period = Period.fromId(intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD))
        if (WardStore(context).isReminderEnabled(period)) {
            ReminderScheduler.createChannel(context)
            val completed = runCatching {
                WardStore(context).isSessionComplete(period, DhikrRepository.load(context))
            }.getOrDefault(false)
            if (!completed) ReminderScheduler.show(context, period)
            ReminderScheduler.schedule(context, period)
        }
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
