package com.cerlita.app.ui.screens.perfil

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerlita.core.designsystem.components.CerlitaBoton
import com.cerlita.core.designsystem.components.CerlitaCard
import com.cerlita.core.designsystem.components.CerlitaChip
import com.cerlita.core.designsystem.components.CerlitaFondo
import com.cerlita.core.designsystem.components.KoalaFace
import com.cerlita.core.designsystem.components.PigFace
import com.cerlita.domain.model.Animal
import com.cerlita.domain.model.Perfil

@Composable
fun PerfilScreen(
    estado: PerfilUiState,
    onActualizar: ((Perfil) -> Perfil) -> Unit,
    onGuardar: () -> Unit,
    onVolver: () -> Unit,
) {
    val perfil = estado.perfil

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
                Text("Perfil", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "¿Quién eres en la pareja?",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Cada teléfono es una mascota. Elige la tuya: cuando el modo pareja esté listo, la cerdita y el koala se reconocerán a distancia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Selector de mascota
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CerlitaCard(
                    modifier = Modifier.weight(1f),
                    color = if (perfil.animal == Animal.CERDITA) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    onClick = { onActualizar { it.copy(animal = Animal.CERDITA) } },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PigFace(tamano = 64.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cerdita", style = MaterialTheme.typography.titleMedium)
                    }
                }
                CerlitaCard(
                    modifier = Modifier.weight(1f),
                    color = if (perfil.animal == Animal.KOALA) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    onClick = { onActualizar { it.copy(animal = Animal.KOALA) } },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KoalaFace(tamano = 64.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Koala", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = perfil.nombrePropio,
                onValueChange = { nombre -> onActualizar { it.copy(nombrePropio = nombre) } },
                label = { Text("Tu nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = perfil.nombrePareja,
                onValueChange = { nombre -> onActualizar { it.copy(nombrePareja = nombre) } },
                label = { Text("El nombre de tu pareja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(26.dp))

            CerlitaBoton(
                texto = if (estado.guardado) "Guardado" else "Guardar perfil",
                onClick = onGuardar,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                habilitado = estado.puedeGuardar,
                icono = { Icon(Icons.Rounded.Check, contentDescription = null) },
            )
            if (estado.guardado) {
                Spacer(modifier = Modifier.height(10.dp))
                CerlitaChip(
                    "Listo. Ya pueden jugar una ronda",
                    tono = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tus respuestas y tu perfil viven solo en tu teléfono. Cerlita no tiene servidores ni cuentas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
