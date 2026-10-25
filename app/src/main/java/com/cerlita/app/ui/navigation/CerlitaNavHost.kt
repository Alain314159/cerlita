package com.cerlita.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cerlita.app.CerlitaApp
import com.cerlita.app.di.Contenedor
import com.cerlita.app.ui.screens.ajustes.AjustesScreen
import com.cerlita.app.ui.screens.ajustes.AjustesViewModel
import com.cerlita.app.ui.screens.editor.EditorPreguntasScreen
import com.cerlita.app.ui.screens.editor.EditorPreguntasViewModel
import com.cerlita.app.ui.screens.home.HomeScreen
import com.cerlita.app.ui.screens.home.HomeViewModel
import com.cerlita.app.ui.screens.juego.JuegoScreen
import com.cerlita.app.ui.screens.juego.JuegoViewModel
import com.cerlita.app.ui.screens.pareja.ParejaScreen
import com.cerlita.app.ui.screens.pareja.ParejaViewModel
import com.cerlita.app.ui.screens.perfil.PerfilScreen
import com.cerlita.app.ui.screens.perfil.PerfilViewModel

/** Crea un ViewModel con acceso al contenedor de dependencias. */
@Composable
private inline fun <reified VM : ViewModel> cerlitaViewModel(
    crossinline crear: (Contenedor) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as CerlitaApp
    val fabrica = viewModelFactory { initializer { crear(app.contenedor) } }
    return viewModel(factory = fabrica)
}

object Rutas {
    const val HOME = "home"
    const val JUEGO = "juego"
    const val PAREJA = "pareja"
    const val PERFIL = "perfil"
    const val EDITOR = "editor"
    const val AJUSTES = "ajustes"
}

@Composable
fun CerlitaRoot() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Rutas.HOME,
        enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 26 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(280)) { it / 26 } },
    ) {
        composable(Rutas.HOME) {
            val vm = cerlitaViewModel {
                HomeViewModel(it.perfilRepository, it.preguntaRepository, it.rondaRepository)
            }
            val estado by vm.estado.collectAsState()
            HomeScreen(
                estado = estado,
                onJugar = { navController.navigate(Rutas.JUEGO) },
                onPareja = { navController.navigate(Rutas.PAREJA) },
                onEditor = { navController.navigate(Rutas.EDITOR) },
                onPerfil = { navController.navigate(Rutas.PERFIL) },
                onAjustes = { navController.navigate(Rutas.AJUSTES) },
            )
        }
        composable(Rutas.JUEGO) {
            val vm = cerlitaViewModel {
                JuegoViewModel(
                    it.rondaRepository,
                    it.preguntaRepository,
                    it.perfilRepository,
                    it.canalPareja,
                )
            }
            val estado by vm.estado.collectAsState()
            JuegoScreen(
                estado = estado,
                onResponder = vm::responder,
                onCambiarBorrador = vm::actualizarBorrador,
                onGuardarBorrador = vm::responderBorrador,
                onAvanzar = vm::avanzar,
                onIniciar = vm::iniciarRonda,
                onJuzgar = vm::juzgar,
                onAbrirCompuerta = vm::abrirCompuerta,
                onVolver = { navController.popBackStack() },
            )
        }
        composable(Rutas.PAREJA) {
            val vm = cerlitaViewModel {
                ParejaViewModel(
                    it.perfilRepository,
                    it.preguntaRepository,
                    it.rondaRepository,
                    it.canalPareja,
                )
            }
            val estado by vm.estado.collectAsState()
            val contexto = LocalContext.current

            // Navegación one-shot cuando la ronda pareja quedó en Room
            // (la arrancó el host o la recibió el invitado).
            LaunchedEffect(estado.irAJuego) {
                if (estado.irAJuego) {
                    navController.navigate(Rutas.JUEGO)
                    vm.consumirNavegacion()
                }
            }

            ParejaScreen(
                estado = estado,
                onBuscar = vm::buscar,
                onConectar = vm::conectarA,
                onElegirModo = vm::elegirModo,
                onIniciarRonda = vm::iniciarRonda,
                onDesconectar = vm::desconectar,
                onVolver = { navController.popBackStack() },
                onResultadoPermisos = vm::alConcederPermisos,
                onAbrirAjustesApp = {
                    contexto.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", contexto.packageName, null),
                        ),
                    )
                },
                onLimpiarError = vm::limpiarError,
            )
        }
        composable(Rutas.PERFIL) {
            val vm = cerlitaViewModel { PerfilViewModel(it.perfilRepository) }
            val estado by vm.estado.collectAsState()
            PerfilScreen(
                estado = estado,
                onActualizar = vm::actualizar,
                onGuardar = vm::guardar,
                onVolver = { navController.popBackStack() },
            )
        }
        composable(Rutas.EDITOR) {
            val vm = cerlitaViewModel { EditorPreguntasViewModel(it.preguntaRepository) }
            val estado by vm.estado.collectAsState()
            EditorPreguntasScreen(
                estado = estado,
                onAbrirDialogoNuevo = vm::abrirDialogoNuevo,
                onAbrirDialogoEdicion = vm::abrirDialogoEdicion,
                onCambiarDialogo = vm::cambiarDialogo,
                onGuardarDialogo = vm::guardarDialogo,
                onCerrarDialogo = vm::cerrarDialogo,
                onToggleActiva = vm::toggleActiva,
                onEliminar = vm::eliminar,
                onVolver = { navController.popBackStack() },
            )
        }
        composable(Rutas.AJUSTES) {
            val vm = cerlitaViewModel { AjustesViewModel(it.gestorCatalogo) }
            val estado by vm.estado.collectAsState()
            AjustesScreen(
                estado = estado,
                onBuscarActualizaciones = vm::buscarActualizaciones,
                onVolver = { navController.popBackStack() },
            )
        }
    }
}
