package com.cerlita.core.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estados posibles de la conexión P2P entre la cerdita y el koala. */
enum class EstadoP2P {
    INACTIVO,
    ANUNCIANDO,
    DESCUBRIENDO,
    CONECTANDO,
    CONECTADO,
    RECONECTANDO,
}

/**
 * Cliente Nearby Connections (Google Play Services) para el modo pareja.
 *
 * ## Fase 2 (próximamente)
 *  - Advertising/Discovery simultáneo con [ESTRATEGIA] P2P_CLUSTER.
 *  - `ConnectionLifecycleCallback` que acepta conexiones y expone el
 *    `endpointId` del otro; `PayloadCallback` para el intercambio de
 *    rondas y respuestas (JSON por bytes).
 *  - `onDisconnected` => estado RECONECTANDO + ronda pausada con banner
 *    "Reconectando..." (el estado de la ronda vive en Room, así que la
 *    desconexión a mitad de ronda no pierde nada).
 *
 * En Fase 1 solo se define el esqueleto para fijar la API interna;
 * la app no lo llama todavía.
 */
class CerlitaNearbyClient(context: Context) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _estado = MutableStateFlow(EstadoP2P.INACTIVO)
    val estado: StateFlow<EstadoP2P> = _estado.asStateFlow()

    fun detener() {
        client.stopAllEndpoints()
        _estado.value = EstadoP2P.INACTIVO
    }

    companion object {
        /** Identifica el servicio Cerlita entre los dispositivos cercanos. */
        const val ID_SERVICIO = "com.cerlita.app.P2P"

        /**
         * P2P_CLUSTER: par simétrico, sin jerarquía; tolera hasta ~60s de
         * desconexión para el mismo endpoint. Ideal para dos teléfonos.
         */
        val ESTRATEGIA = Strategy.P2P_CLUSTER

        fun opcionesAnuncio(): AdvertisingOptions =
            AdvertisingOptions.Builder().setStrategy(ESTRATEGIA).build()

        fun opcionesDescubrimiento(): DiscoveryOptions =
            DiscoveryOptions.Builder().setStrategy(ESTRATEGIA).build()
    }
}
