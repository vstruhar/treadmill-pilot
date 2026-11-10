package com.threadmill.connect

import android.app.Application
import com.threadmill.connect.ble.TreadmillLink
import com.threadmill.connect.data.AppDatabase
import com.threadmill.connect.data.AppRepository
import com.threadmill.connect.data.SettingsStore
import com.threadmill.connect.run.CuePlayer
import com.threadmill.connect.run.RunController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TreadmillApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var link: TreadmillLink
        private set
    lateinit var runController: RunController
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.create(this)
        repository = AppRepository(database)
        settings = SettingsStore(this)
        link = TreadmillLink(this)
        runController = RunController(this, repository, settings, link, CuePlayer(this))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.ensureSeed()
        }
    }
}
