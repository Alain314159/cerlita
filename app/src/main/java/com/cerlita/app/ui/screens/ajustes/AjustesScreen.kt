package com.cerlita.app.ui.screens.ajustes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cerlita.core.ai.EstadoModeloIA
import com.cerlita.core.designsystem.components.CerlitaBoton
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.data.repo.ResultadoActualizacion
import java.util.Locale

@Composable
fun AjustesScreen(
    estado: AjustesUiState,
    onBuscarActualizaciones: () -> Unit,
    onDescargarModelo: () -> Unit,
    onImportarModelo: (Uri) -> Unit,
    onEliminarModelo: () -> Unit,
    onVolver: () -> Unit,
) {
    // Selector de archivos del sistema (SAF): el zip del modelo puede venir
    // de Descargas, "Mis archivos", una memoria USB… sin permisos rarios.
    val selectorZipModelo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportarModelo(uri)
    }
    val abrirSelector: () -> Unit = {
        selectorZipModelo.launch(
            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
        )
    }

    CerlitaFondo {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(20.dp))

            CerlitaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Catálogo de preguntas", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Las preguntas viven en un archivo público del repositorio. " +
                            "Aquí puedes buscar la última versión: se descarga una sola vez y queda guardada para jugar sin internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CerlitaChip(
                        if (estado.versionCatalogo.isBlank()) "versión inicial (assets)" else "instalada: v${estado.versionCatalogo}",
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (estado.buscando) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Buscando novedades…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        CerlitaBoton(
                            texto = "Buscar actualizaciones",
                            onClick = onBuscarActualizaciones,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            icono = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        )
                    }
                    estado.mensajeResultado?.let { mensaje ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val color = when (estado.resultado) {
                            is ResultadoActualizacion.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TarjetaModeloIA(
                estado = estado,
                onDescargar = onDescargarModelo,
                onImportar = abrirSelector,
                onEliminar = onEliminarModelo,
            )

            Spacer(modifier = Modifier.height(14.dp))

            CerlitaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Acerca de Cerlita", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Un jueguito conversacional para parejas: la cerdita y el koala. " +
                            "Sin cuentas, sin anuncios y sin servidores: todo vive en su teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Código abierto en github.com/Alain314159/cerlita",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TarjetaModeloIA(
    estado: AjustesUiState,
    onDescargar: () -> Unit,
    onImportar: () -> Unit,
    onEliminar: () -> Unit,
) {
    CerlitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text("Modelo de IA (opcional)", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Una red neuronal pequeñita que vive en tu teléfono y detecta preguntas repetidas " +
                    "cuando propones una nueva. Se instala desde un zip comprimido: se descarga de GitHub " +
                    "o se importa desde el almacenamiento (por ejemplo, de Descargas/Cerlita tras reinstalar).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val modelo = estado.estadoModelo) {
                is EstadoModeloIA.Instalado -> {
                    CerlitaChip(
                        "${modelo.metadatos.nombre} v${modelo.metadatos.version} · " +
                            "${modelo.metadatos.dimensiones}d · " +
                            "${"%.0f".format(Locale.US, modelo.tamanoMb)} MB instalado",
                    )
                }

                is EstadoModeloIA.Descargando -> {
                    LinearProgressIndicator(
                        progress = { modelo.progreso.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (modelo.mbTotales > 0f) {
                            "Descargando… ${"%.1f".format(Locale.US, modelo.mbDescargados)} de " +
                                "${"%.0f".format(Locale.US, modelo.mbTotales)} MB"
                        } else {
                            "Descargando… ${"%.1f".format(Locale.US, modelo.mbDescargados)} MB"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                EstadoModeloIA.Importando -> {
                    FilaOcupada("Copiando desde el almacenamiento…")
                }

                EstadoModeloIA.Instalando -> {
                    FilaOcupada("Validando e instalando modelo…")
                }

                is EstadoModeloIA.Error -> {
                    Text(
                        text = modelo.mensaje,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                EstadoModeloIA.NoInstalado -> Unit
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (estado.estadoModelo is EstadoModeloIA.Instalado) {
                Row {
                    TextButton(onClick = onImportar, enabled = !estado.operandoModelo) {
                        Text("Importar otra versión")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onEliminar, enabled = !estado.operandoModelo) {
                        Text("Eliminar modelo", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                CerlitaBoton(
                    texto = "Descargar desde GitHub",
                    onClick = onDescargar,
                    habilitado = !estado.operandoModelo,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onImportar,
                    enabled = !estado.operandoModelo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importar desde el almacenamiento…")
                }
            }

            estado.mensajeModelo?.let { mensaje ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun FilaOcupada(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}
