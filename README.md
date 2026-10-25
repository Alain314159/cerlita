# Cerlita 🐷🐨

**Juego conversacional para parejas.** La cerdita y el koala responden preguntas —cada uno en su móvil conectados sin internet, o pasándose un solo teléfono— y descubren cuánto se conocen realmente.

- Sin cuentas, sin anuncios, sin servidores propios
- **Sin IA en el teléfono**: la comparación la hacen ustedes (ver abajo)
- Todo offline-first: tu perfil y tus respuestas viven solo en tu teléfono
- Catálogo de preguntas actualizable desde este mismo repositorio
- **Puedes proponer preguntas nuevas desde la app** (ver más abajo)

---

## Sin IA en el teléfono (decisión de arquitectura)

Cerlita **no lleva ningún modelo de IA**: ni ONNX, ni embeddings, ni descargas de 72 MB. La decisión es deliberada y cubre tres frentes:

1. **Las preguntas cerradas no necesitan modelo.** Un Sí/No se compara por igualdad exacta; la escala tolera ±1; las opciones coinciden o no. Cualquier modelo añadiría peso y latencia para dar la misma respuesta.
2. **El texto libre lo juzga la pareja, y con más contexto que cualquier modelo.** Dos personas que se conocen evalúan en dos segundos si "pizza casera un viernes" y "una noche de pasta" se parecen — con la ironía, los apodos y la historia que ningún modelo ve. Los botones `[Sí] [Parecido] [No]` (ambos responden) y `[Acertó] [Más o menos] [Falló]` (adivina) son el "motor de comparación".
3. **La deduplicación del catálogo vive en GitHub Actions**, donde un modelo multilingüe no cuesta nada al APK: el teléfono queda en ~14 MB.

Resultado: APK ligero, cero dependencias nativas de inferencia, cero descargas opcionales y mejor criterio donde importa.

## Los tres modos de juego

| Modo | Mecánica | Veredicto |
|------|----------|-----------|
| **Ambos responden** | Los dos responden la misma pregunta por separado y se revelan juntas | Igualdad automática (sí/no, opciones), ±1 en escala; texto libre → `[Sí] [Parecido] [No]` |
| **Adivina** | Uno escribe lo que cree que responderá el otro; luego el otro responde la verdad | Igualdad automática en cerradas; texto libre → `[Acertó] [Más o menos] [Falló]` |
| **Mixto** | Alterna pregunta a pregunta los dos modos de arriba | El que toque en cada pregunta |

- **En un solo móvil** (pasa-el-móvil): compuerta "Pasa el móvil a X" entre turnos para que nadie espíe la respuesta anterior, y revelación con veredicto al final de cada pregunta.
- **En dos móviles** (modo pareja P2P): cada quien responde en su teléfono al ritmo que quiera; las respuestas se cruzan solas y la revelación con veredictos llega en el resumen final.

Las reglas puras (planificación de modos, turnos, veredictos automáticos) viven en `domain/juego/ReglasJuego.kt`: deterministas, sin Android y cubiertas por tests. En modo MIXTO la cerdita abre las preguntas pares y adivina las impares — regla que ambos teléfonos deducen igual sin comunicarse.

## Estado del desarrollo

| Fase | Contenido | Estado |
|------|-----------|--------|
| **F0** | Proyecto, tema visual, navegación | ✅ |
| **F1** | Perfil, banco de preguntas, editor, catálogo remoto, flujo comunitario | ✅ |
| **F2** | Modo pareja P2P (Nearby Connections, sin internet) | ✅ |
| **F3** | Rediseño del juego: 3 modos con juicio humano + eliminación de toda la IA del teléfono | ✅ **Esta entrega** |
| **F4** | Deduplicación semántica del catálogo en GitHub Actions | ✅ |

### Pantallas

- **Home**: mascotas animadas, estadísticas y acceso a jugar
- **Ronda**: selector de modo → 10 preguntas con turnos y compuertas (un móvil) o a tu ritmo (dos móviles) → revelación con veredictos → resumen con marcador de coincidencias
- **Modo pareja**: lobby P2P por Bluetooth; quien conecta elige el modo de juego y arranca la ronda compartida
- **Perfil**: nombres de ambos y mascota (cerdita 🐷 o koala 🐨)
- **Mis preguntas**: editor para crear/activar/borrar preguntas propias
- **Ajustes**: buscar actualizaciones del catálogo

---

## Stack técnico (octubre 2026)

| Pieza | Versión |
|-------|---------|
| Android Gradle Plugin | **9.4.0** (Gradle 9.6.0, JDK 17+) |
| Kotlin | **2.4.0** |
| KSP | **2.3.12** |
| Jetpack Compose BOM | **2026.09.00** (Compose 1.12, Material 3) |
| compileSdk / targetSdk / minSdk | **37 / 36 / 26** |
| Room | **2.8.5** |
| Navigation Compose | **2.10.2** |
| Lifecycle | **2.11.0** |
| DataStore Preferences | **1.3.0** |
| kotlinx-serialization | **1.12.0** |
| Nearby Connections | **19.5.1** |

Fuente Nunito + Quicksand (variables, eje `wght`), paleta oscura `#121212` con acentos pastel (`#FFB6C1` cerdita / `#9FB8D9` koala), mascotas dibujadas 100% con Canvas (sin assets).

### Módulos

```
app/                UI Compose (pantallas, navegación, DI manual)
domain/             Modelos y contratos puros (Kotlin JVM) + ReglasJuego
data/               Room + DataStore + catálogo (assets/remoto) + repos
core/designsystem/  Tema, tipografía, componentes y mascotas
core/common/        DispatcherProvider
core/network/       Cliente Nearby Connections + protocolo P2P
content/            preguntas.json ← catálogo canónico servido por raw URL
scripts/            Validación, dedup semántica y adición de preguntas (Python, CI)
```

---

## Catálogo comunitario con deduplicación semántica

1. **Dentro de la app** → *Mis preguntas* → crea tu pregunta → **"Proponer al catálogo"**.
2. Se abre GitHub con un **issue precargado** (plantilla `nueva-pregunta`).
3. Al etiquetar el issue como `pregunta-comunidad`, el workflow ejecuta en el servidor el modelo multilingüe `paraphrase-multilingual-MiniLM-L12-v2` y compara la propuesta contra todo el catálogo por similitud coseno:

   | Similitud | Destino |
   |-----------|---------|
   | **> 0.85** | Duplicada: comentario con la original + issue cerrado |
   | **0.70 – 0.85** | Zona gris: etiqueta `revisar` + PR para decisión humana |
   | **< 0.70** | Nueva: commit directo a `content/preguntas.json` + bump de versión + issue cerrado |

4. La app de cualquiera detecta la versión nueva en Ajustes → *Buscar actualizaciones*.

El modelo pesa lo que quiera: vive en GitHub Actions, no en el APK. El APK nunca lleva credenciales: la subida pasa por GitHub y la descarga usa la raw URL pública del repo.

---

## Modo pareja: cómo funciona el P2P

**Sin internet, sin servidores, sin credenciales.** Dos teléfonos se encuentran por Bluetooth/Wi-Fi Direct con **Nearby Connections** (`Strategy.P2P_CLUSTER`, par simétrico) y juegan la misma ronda:

1. **Lobby**: ambos tocan *Buscar a mi pareja*. Cada teléfono aparece con el nombre del perfil; se toca y se conecta.
2. **Host determinístico**: quien toca "Conectar" decide. El host elige el **modo de juego** (ambos adivinan / adivina / mixto) y arranca la ronda.
3. **Ronda compartida**: el host envía `InicioRonda` con el id de ronda + las preguntas completas + el modo y la lista de modos por pregunta (así ambos juegan exactamente la misma secuencia). Ambos teléfonos persisten la misma ronda en Room.
4. **Cruce en vivo**: al responder, cada teléfono envía su respuesta por P2P y la del otro se guarda en Room al llegar. En pantalla solo se ve "X ya respondió" — la respuesta se revela al final.
5. **Resumen con veredictos**: las dos respuestas por pregunta, el chip de coincidencia automática en las cerradas y los botones de juicio humano en las de texto libre. En PAREJA cada teléfono emite su propio veredicto: es cooperativo, y comparar lo que cada quien opinó también es parte del juego.

**Desconexión a mitad de ronda**: no se pierde nada — la ronda vive en Room y se sigue jugando; al final el resumen muestra lo que llegó. Se puede reconectar desde el lobby en cualquier momento.

**Protocolo de bytes**: un JSON por payload (`{"tipo":"…", …}`); los mensajes de una versión futura se ignoran sin romper la actual (`core/network/ProtocoloP2P.kt`). `InicioRonda` ganó los campos `modoJuego`/`modos` con valores por defecto: un teléfono viejo los ignora y sigue jugando.

**Idempotencia**: índice único `(ronda, pregunta, autor)` en respuestas y `(ronda, pregunta)` en evaluaciones — un reintento P2P o un re-juicio reemplazan, nunca duplican (migraciones de Room 1→2→3 incluidas).

---

## Compilar

```bash
git clone https://github.com/Alain314159/cerlita.git
cd cerlita
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/
```

Requisitos: JDK 17+ (probado con 21). El CI de GitHub Actions compila y pasa los tests en cada push/PR y sube el APK debug como artefacto.

---

## Privacidad

- `allowBackup=false`: las respuestas de la pareja no se suben a ningún backup de Google
- `INTERNET` solo para buscar actualizaciones del catálogo. Punto.
- Los permisos de "dispositivos cercanos" (Bluetooth) solo se piden al activar el modo pareja; con `neverForLocation` no se exige ubicación en Android 12+. En Android 8–11 se pide ubicación fina (requisito del SO para perfilado Bluetooth), sin salir nunca del teléfono: el P2P no usa internet
- Cero analítica, cero telemetría, cero rastreadores

## Licencia

Código bajo Apache 2.0. Las fuentes Nunito y Quicksand son OFL © de sus autores.
