package com.cerlita.core.network

import android.content.Context
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.EstadoP2P
import com.cerlita.domain.model.EndpointPareja
import com.cerlita.domain.model.InfoJugador
import com.cerlita.domain.model.MensajePareja
import com.cerlita.domain.repository.CanalPareja
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cliente Nearby Connections (Google Play Services) para el modo pareja:
 * la cerdita y el koala se encuentran y juegan sin internet.
 *
 * - Anuncia y descubre a la vez con [ESTRATEGIA] P2P_CLUSTER (par simétrico,
 *   sin jerarquía de red, tolerante a desconexiones breves).
 * - Auto-acepta cualquier endpoint Cerlita: la "autenticación" es verse
 *   mutuamente por nombre en la misma sala. Es una app de parejas, no un chat.
 * - Al conectarse, ambos se saludan ([MensajePareja.Saludo]); el resto del
 *   protocolo viaja como bytes JSON ver [ProtocoloP2P].
 * - Quien toca "Conectar" se vuelve host ([soyHost]) y decide la ronda; el
 *   otro solo espera. Determinístico y sin carreras.
 *
 * El objeto es singleton en el contenedor de la app: sobrevive a la
 * navegación para que las respuestas cruzadas lleguen a la pantalla de juego.
 */
class CerlitaNearbyClient(context: Context) : CanalPareja {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    // ------------------------------------------------------------------
    // Estado expuesto (interfaz CanalPareja)
    // ------------------------------------------------------------------

    private val _estado = MutableStateFlow(EstadoP2P.INACTIVO)
    override val estado: StateFlow<EstadoP2P> = _estado.asStateFlow()

    private val _encontrados = MutableStateFlow<List<EndpointPareja>>(emptyList())
    override val encontrados: StateFlow<List<EndpointPareja>> = _encontrados.asStateFlow()

    private val _otro = MutableStateFlow<InfoJugador?>(null)
    override val otro: StateFlow<InfoJugador?> = _otro.asStateFlow()

    private val _soyHost = MutableStateFlow(false)
    override val soyHost: StateFlow<Boolean> = _soyHost.asStateFlow()

    private val _mensajes = MutableSharedFlow<MensajePareja>(extraBufferCapacity = 64)
    override val mensajes: SharedFlow<MensajePareja> = _mensajes.asSharedFlow()

    private val _errores = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errores: SharedFlow<String> = _errores.asSharedFlow()

    // ------------------------------------------------------------------
    // Estado interno de sesión
    // ------------------------------------------------------------------

    /** Mi identidad en esta sesión (para saludar y para conectarme). */
    private var miInfo = InfoJugador(nombre = "Cerlita", animal = Animal.CERDITA)

    /** Endpoint del otro teléfono; null hasta que hay enlace. */
    private var endpointRemoto: String? = null

    // ------------------------------------------------------------------
    // Callbacks de Nearby
    // ------------------------------------------------------------------

    private val callbackConexion = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Ambos lados reciben esto; auto-aceptar. Si yo no llamé a
            // conectarA(), soy el invitado (soyHost queda en false).
            // En el API 19.5.1 el PayloadCallback se entrega al aceptar.
            endpointRemoto = endpointId
            if (_estado.value != EstadoP2P.CONECTADO) _estado.value = EstadoP2P.CONECTANDO
            client.acceptConnection(endpointId, callbackPayload)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    _estado.value = EstadoP2P.CONECTADO
                    _encontrados.value = emptyList()
                    // Ya no hace falta seguir visibles para otros dispositivos.
                    client.stopAdvertising()
                    client.stopDiscovery()
                    enviarSaludo()
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    falloConReintento("El otro teléfono rechazó la conexión. Inténtalo de nuevo.")

                else -> falloConReintento("No se pudo conectar con tu pareja.")
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId == endpointRemoto || endpointRemoto == null) {
                endpointRemoto = null
                if (_estado.value == EstadoP2P.CONECTADO ||
                    _estado.value == EstadoP2P.CONECTANDO
                ) {
                    // La ronda vive en Room: no se pierde nada, se sigue
                    // jugando en solitario y el resumen muestra lo que llegó.
                    _estado.value = EstadoP2P.DESCONECTADO
                }
            }
        }
    }

    private val callbackDescubrimiento = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != ID_SERVICIO) return
            val actual = _encontrados.value
            if (actual.none { it.id == endpointId }) {
                _encontrados.value =
                    actual + EndpointPareja(id = endpointId, nombre = info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _encontrados.value = _encontrados.value.filterNot { it.id == endpointId }
        }
    }

    private val callbackPayload = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val mensaje = ProtocoloP2P.decodificar(bytes) ?: return
            if (mensaje is MensajePareja.Saludo) _otro.value = mensaje.info
            _mensajes.tryEmit(mensaje)
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate,
        ) {
            // Los payloads de bytes llegan completos; nada que hacer.
        }
    }

    // ------------------------------------------------------------------
    // API pública (interfaz CanalPareja)
    // ------------------------------------------------------------------

    override fun buscar(miInfo: InfoJugador) {
        this.miInfo = miInfo
        endpointRemoto = null
        _otro.value = null
        _soyHost.value = false
        _encontrados.value = emptyList()
        _estado.value = EstadoP2P.BUSCANDO

        val nombre = miInfo.nombre.ifBlank { "Cerlita" }
        client.startAdvertising(nombre, ID_SERVICIO, callbackConexion, opcionesAnuncio())
            .addOnFailureListener {
                _estado.value = EstadoP2P.INACTIVO
                _errores.tryEmit("No se pudo iniciar la búsqueda. ¿Tienes Google Play actualizado?")
            }
        client.startDiscovery(ID_SERVICIO, callbackDescubrimiento, opcionesDescubrimiento())
            .addOnFailureListener {
                // El anuncio puede seguir: quizá solo falló descubrir.
                if (_encontrados.value.isEmpty() && _estado.value == EstadoP2P.BUSCANDO) {
                    _errores.tryEmit("No se pudo descubrir dispositivos cercanos.")
                }
            }
    }

    override fun conectarA(endpointId: String) {
        if (_estado.value == EstadoP2P.CONECTANDO || _estado.value == EstadoP2P.CONECTADO) return
        _soyHost.value = true
        _estado.value = EstadoP2P.CONECTANDO
        endpointRemoto = endpointId
        client.stopAdvertising()
        client.stopDiscovery()
        client.requestConnection(miInfo.nombre.ifBlank { "Cerlita" }, endpointId, callbackConexion)
            .addOnFailureListener {
                falloConReintento("No se pudo solicitar la conexión.")
            }
    }

    override fun enviar(mensaje: MensajePareja) {
        val destino = endpointRemoto ?: return
        client.sendPayload(destino, Payload.fromBytes(ProtocoloP2P.codificar(mensaje)))
    }

    override fun detener() {
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
        endpointRemoto = null
        _otro.value = null
        _soyHost.value = false
        _encontrados.value = emptyList()
        _estado.value = EstadoP2P.INACTIVO
    }

    // ------------------------------------------------------------------
    // Ayudantes privados
    // ------------------------------------------------------------------

    private fun enviarSaludo() {
        enviar(MensajePareja.Saludo(miInfo))
    }

    /**
     * Una conexión falló pero la sesión sigue viva: se vuelve a la búsqueda
     * para poder reintentar sin pasar por el lobby.
     */
    private fun falloConReintento(mensaje: String) {
        endpointRemoto = null
        _soyHost.value = false
        _estado.value = EstadoP2P.BUSCANDO
        _errores.tryEmit(mensaje)
        // Retomar el papel pasivo (anunciar + descubrir) para reintentar.
        client.startAdvertising(
            miInfo.nombre.ifBlank { "Cerlita" },
            ID_SERVICIO,
            callbackConexion,
            opcionesAnuncio(),
        )
        client.startDiscovery(ID_SERVICIO, callbackDescubrimiento, opcionesDescubrimiento())
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
