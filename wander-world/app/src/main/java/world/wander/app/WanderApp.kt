package world.wander.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.maplibre.android.MapLibre
import world.wander.app.data.Repository
import world.wander.app.work.DailyWorker

class WanderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Wander World is ready to chart your walks"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DISPATCH, "Daily dispatch", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One message a day about your world"
            },
        )
        val repo = Repository.get(this)
        repo.prefs.startDay // fixes the world's first day on first launch
        DailyWorker.schedule(this, repo.prefs.dispatchHour, repo.prefs.dispatchMinute)
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_DISPATCH = "dispatch"
    }
}
