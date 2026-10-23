package com.cerlita.app

import android.app.Application
import com.cerlita.app.di.Contenedor
import com.cerlita.app.p2p.ReceptorP2P
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CerlitaApp : Application() {

    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)

        // Offline-first: en el primer arranque siembra el catálogo incluido
        // en assets. Las actualizaciones remotas se buscan manualmente desde
        // Ajustes (Fase 1).
        alcanceApp.launch {
            runCatching { contenedor.gestorCatalogo.sembrarCatalogoInicial() }
                .onFailure { android.util.Log.w(TAG, "No se pudo sembrar el catálogo inicial", it) }
        }

        // Respuestas de la pareja persistidas aunque el juego esté cerrado:
        // el receptor vive tanto como el proceso (alcance de aplicación).
        ReceptorP2P(contenedor.canalPareja, contenedor.rondaRepository, alcanceApp)
    }

    companion object {
        private const val TAG = "CerlitaApp"
    }
}
