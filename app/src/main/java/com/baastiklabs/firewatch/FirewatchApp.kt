package com.baastiklabs.firewatch

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.baastiklabs.firewatch.data.BackupStore
import com.baastiklabs.firewatch.data.RecordStore
import com.baastiklabs.firewatch.data.Repository
import com.baastiklabs.firewatch.update.CrashGuard
import com.baastiklabs.firewatch.update.UpdateScheduler
import com.baastiklabs.firewatch.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FirewatchApp : Application() {
    lateinit var graph: AppGraph
        private set

    @Volatile
    private var startedActivities = 0

    /** True while any Firewatch screen is visible. */
    val isInForeground: Boolean get() = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        // Keep this minimal: if it ever crashes, the crash guard can't help.
        CrashGuard.install(this)
        graph = AppGraph(this)
        // Keep home-screen widgets in step with the data (debounced).
        var pending: kotlinx.coroutines.Job? = null
        graph.repository.onChange = {
            pending?.cancel()
            pending = graph.appScope.launch {
                kotlinx.coroutines.delay(400)
                com.baastiklabs.firewatch.widget.refreshWidgets(this@FirewatchApp)
            }
        }
        registerActivityLifecycleCallbacks(ForegroundTracker())
        if (!CrashGuard.rollbackIfCrashLooping(this)) {
            UpdateScheduler.ensureScheduled(this)
            UpdateScheduler.checkNow(this)
        }
        graph.appScope.launch {
            runCatching {
                graph.repository.ensureLoaded()
                BackupStore.dailyIfDue(this@FirewatchApp, graph.repository)
            }
        }
    }

    private inner class ForegroundTracker : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            // A downloaded update waits for D to leave the app; now's the time.
            if (startedActivities == 0 && graph.updateState.pendingInstall) {
                UpdateScheduler.checkNow(this@FirewatchApp)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/** App-wide singletons. */
class AppGraph(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = Repository(RecordStore(app))
    val updateState = UpdateState(app)
}
