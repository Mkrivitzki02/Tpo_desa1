package com.uade.hmig1

import android.app.Application
import com.uade.hmig1.data.RobotRepository
import com.uade.hmig1.data.SimulatedRobotRepository
import com.uade.hmig1.data.local.EventDatabase
import com.uade.hmig1.data.sim.RobotSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: un único lugar decide qué implementación de Repository se usa. */
class AppContainer(app: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = EventDatabase.create(app)
    private val simulator = RobotSimulator(appScope)
    val repository: RobotRepository = SimulatedRobotRepository(simulator, database.eventDao(), appScope)
}

class HmiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.repository.start()
    }
}
