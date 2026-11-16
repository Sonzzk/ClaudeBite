package com.sonzzk.claudebite

import android.app.Application
import com.sonzzk.claudebite.system.Reminders
import com.sonzzk.claudebite.system.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ClaudeBiteApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching { Reminders.schedule(this) }
        // Keep the home-screen widget in step with what's logged today (and roll over at midnight).
        appScope.launch {
            flow {
                while (true) {
                    emit(LocalDate.now().toString())
                    delay(60_000)
                }
            }.distinctUntilChanged()
                .flatMapLatest { container.repository.entriesFor(it) }
                .debounce(1_500)
                .collect { runCatching { TodayWidget.refresh(this@ClaudeBiteApp) } }
        }
    }
}
