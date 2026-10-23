# Cerlita 🐷🐨

**Juego conversacional para parejas.** La cerdita y el koala responden preguntas en sus teléfonos, se ríen, se conocen mejor y —cuando llegue el modo pareja— conectan sus móviles **sin internet** para descubrir cuánto tienen en común.

- Sin cuentas, sin anuncios, sin servidores propios
- Todo offline-first: tu perfil y tus respuestas viven solo en tu teléfono
- Catálogo de preguntas actualizable desde este mismo repositorio
- **Puedes proponer preguntas nuevas desde la app** (ver más abajo)

---

## Estado del desarrollo

| Fase | Contenido | Estado |
|------|-----------|--------|
| **F0** | Proyecto, tema visual, navegación | ✅ Hecho |
| **F1** | Perfil, banco de preguntas, juego local (rondas de 10), editor de preguntas, catálogo remoto, flujo comunitario | ✅ Hecho |
| **F2** | Modo pareja P2P (Nearby Connections, sin internet): lobby de conexión, ronda compartida, respuestas cruzadas en vivo, resumen con coincidencias | ✅ |
| **F3** | IA local: gestión del modelo (descarga zip/importación/respaldo) + motor ONNX + tokenizador WordPiece | ✅ **Esta entrega** |
| **F4** | Deduplicación semántica en el envío de preguntas + CI de contenido | ⏳ (base ya en CI) |

### Pantallas de las Fases 1–3
- **Home**: mascotas animadas, estadísticas y acceso directo a jugar (solo o en pareja)
- **Ronda local**: 10 preguntas al azar con 4 tipos de respuesta (sí/no, escala 1-5, opciones y texto libre). Se retoma sola si cierras la app (el estado vive en Room)
- **Modo pareja**: lobby P2P por Bluetooth sin internet; quien conecta elige la ronda (host) y el otro la recibe sola. Cada teléfono muestra si su pareja ya respondió (sin revelar la respuesta) y al final el resumen muestra las dos columnas con sus "Coinciden"
- **Perfil**: tu nombre, el de tu pareja y tu mascota (cerdita 🐷 o koala 🐨)
- **Mis preguntas**: editor para crear/activar/borrar preguntas propias
- **Ajustes**: buscar actualizaciones del catálogo y gestionar el **modelo de IA** (descarga desde GitHub, importación desde el almacenamiento, eliminación)

---

## Stack técnico (actualizado a octubre 2026)

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
| Nearby Connections | **19.5.1** (Fase 2) |
| ONNX Runtime Android | **1.31.0** (Fase 3) |

Fuente Nunito + Quicksand (variables, eje `wght`), paleta oscura `#121212` con acentos pastel (`#FFB6C1` cerdita / `#9FB8D9` koala), mascotas dibujadas 100% con Canvas (sin assets).

### Módulos

```
app/                UI Compose (pantallas, navegación, DI manual)
domain/             Modelos y contratos puros (Kotlin JVM)
data/               Room + DataStore + catálogo (assets/remoto) + repos
core/designsystem/  Tema, tipografía, componentes y mascotas
core/common/        DispatcherProvider
core/network/       Cliente Nearby Connections: modo pareja P2P (Fase 2)
core/ai/            Contrato de embeddings + similitud coseno (Fase 3)
content/            preguntas.json ← catálogo canónico servido por raw URL
scripts/            Validación y adición de preguntas (Python, CI)
```

---

## Cómo funciona el catálogo comunitario

1. **Dentro de la app** → pantalla *Mis preguntas* → crea tu pregunta → botón **“Proponer al catálogo”**.
2. Se abre GitHub con un **issue precargado** (plantilla `nueva-pregunta`).
3. Al etiquetar el issue como `pregunta-comunidad`, un **workflow** valida la pregunta (formato, duplicados), la agrega al catálogo y **abre un PR automáticamente**.
4. Al mezclar el PR, la app de cualquiera puede pulsar *Buscar actualizaciones* en Ajustes y descargará el nuevo JSON.

El APK nunca lleva credenciales: la subida pasa por GitHub, y la descarga usa la raw URL pública del repo.

---

## Modo pareja (Fase 2): cómo funciona el P2P

**Sin internet, sin servidores, sin credenciales.** Dos teléfonos se encuentran por Bluetooth/Wi-Fi Direct con **Nearby Connections** (`Strategy.P2P_CLUSTER`, par simétrico) y juegan la misma ronda:

1. **Lobby**: ambos tocan *Buscar a mi pareja* (anuncian y descubren a la vez). Cada teléfono aparece con el nombre del perfil; se toca y se conecta — auto-aceptación, es una app de parejas, no un chat.
2. **Host determinístico**: quien toca "Conectar" decide la ronda. Nada de carreras ni elecciones ambiguas.
3. **Ronda compartida**: el host sortea 10 preguntas y envía `InicioRonda` con el **id de ronda + las preguntas completas** (así el otro juega aunque tenga un catálogo más viejo o distinto). Ambos teléfonos persisten la misma ronda en Room.
4. **Cruce en vivo**: al responder, cada teléfono envía su respuesta por P2P y la del otro se guarda en Room al llegar (receptor a nivel de app: aunque cierres la pantalla, no se pierde). En pantalla solo se ve "X ya respondió" — la respuesta se revela al final.
5. **Resumen a dos columnas**: "Tú" vs. el nombre de la pareja, con un chip **Coinciden** cuando las respuestas (numéricas/opción) son iguales. Las respuestas que llegan tarde aparecen solas.

**Desconexión a mitad de ronda**: P2P_CLUSTER tolera ~60 s, pero si el enlace muere, no se pierde nada — la ronda vive en Room y se sigue jugando en solitario; al final el resumen muestra lo que llegó. Se puede reconectar desde el lobby en cualquier momento.

**Protocolo de bytes**: un JSON por payload (`{"tipo":"…", …}`) con versión implícita por campo `tipo`; los mensajes de una versión futura se ignoran sin romper la actual. Codificación/decodificación y sus tests viven en `core/network/ProtocoloP2P.kt`.

**Idempotencia**: índice único `(ronda, pregunta, autor)` en Room — un reintento P2P reemplaza, nunca duplica (migración 1→2 incluida y probada).

---

## Modelo de IA local (Fase 3): gestión e instalación

El modelo **no viaja dentro del APK** (que queda en ~73 MB con las dos ABI de teléfono). En su lugar, Ajustes → *Modelo de IA* ofrece dos caminos:

1. **Descargar desde GitHub**: la app consulta el release [`modelo-ia-v1`](https://github.com/Alain314159/cerlita/releases/tag/modelo-ia-v1), baja el **zip comprimido** (72 MB) con barra de progreso, valida el manifiesto y lo instala en `filesDir/modelos/cerlita`.

2. **Importar desde el almacenamiento**: el selector de archivos del sistema (SAF, sin permisos) acepta el mismo zip desde Descargas, "Mis archivos" o una memoria USB.

**Respaldo que sobrevive a desinstalar la app**: tras descargar, el zip se copia a la carpeta pública `Descargas/Cerlita`. Esa carpeta no se borra al desinstalar Cerlita, así que tras una reinstalación basta con *Importar desde el almacenamiento* para recuperar el modelo sin volver a descargarlo (ideal con datos lentos).

### Formato del paquete (contrato v1)

```

cerlita-modelo-ia-v1.zip

├── metadatos.json    ← manifiesto: nombre, versión, dimensiones, tokenizador…

├── modelo.onnx       ← pesos ONNX cuantizados INT8

└── vocab.txt         ← tokenizador WordPiece

```

La app valida el manifiesto antes de instalar (formato, tokenizador soportado, dimensiones y archivos presentes), por lo que el mismo mecanismo sirve para reemplazar el modelo por cualquiera compatible sin tocar el código.

### Modelo publicado en v1

[`hiiamsid/sentence_similarity_spanish_es`](https://huggingface.co/hiiamsid/sentence_similarity_spanish_es): BETO (BERT español) afinado para similitud de frases, 768d, **tokenizador WordPiece** (el mismo que implementa la app en Kotlin, sin dependencias nativas extra). Verificación de humo del paquete publicado: preguntas parafraseadas ≈ **0.77** de similitud coseno; preguntas sin relación ≈ **0.23**.

El motor ejecuta en cascada **NNAPI → XNNPACK → CPU** según lo que soporte el teléfono, y la cuantización INT8 dinámica mantiene el modelo en ~110 MB de RAM en inferencia.

### Regenerar y publicar el paquete

```bash

# 1) Empaquetar (exporta a ONNX, cuantiza INT8 y valida con onnxruntime)

python scripts/empaquetar_modelo.py --modelo hiiamsid/sentence_similarity_spanish_es \

    --salida cerlita-modelo-ia-v1.zip

# 2) Publicar como asset del release modelo-ia-v1

python scripts/subir_modelo.py cerlita-modelo-ia-v1.zip --token ghp_XXX --version 1

```

> **Token**: el repositorio es público, así que la descarga desde la app **no necesita credenciales**. Si algún día se vuelve privado, define `cerlita.modelo.token` en `local.properties` (nunca en git). No incrustes tokens en el APK: se extraen trivialmente.

### Decisión de IA original (contexto)

El plan inicial apuntaba a `all-MiniLM-L6-v2`, descartado por ser **solo-inglés**. Para la v1 se priorizó un modelo **WordPiece en español** (BETO) porque el tokenizador viaja en Kotlin puro; `google/embeddinggemma-300m` (multilingüe, MRL 768d→128d) queda como mejora futura: exigiría un tokenizador SentencePiece en la app, pero el formato de paquete ya lo contempla (`tokenizador: "sentencepiece"` + bump de `formato`). Plan B ligero documentado: `intfloat/multilingual-e5-small`.

La deduplicación semántica del catálogo seguirá corriendo **server-side** en GitHub Actions (Python), sin tocar el móvil.

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
- `INTERNET` solo para buscar actualizaciones del catálogo y descargar/importar el modelo de IA (opcional, manual). El selector de archivos (SAF) y la copia del modelo en Descargas no requieren permisos
- El modelo de IA se instala y ejecuta **solo en el teléfono**; ningún texto sale del dispositivo
- Los permisos de "dispositivos cercanos" (Bluetooth) solo se piden al activar el modo pareja; con `neverForLocation` no se exige ubicación en Android 12+. En Android 8–11 se pide ubicación fina (requisito del SO para perfilado Bluetooth), sin salir nunca del teléfono: el P2P no usa internet

## Licencia

Código bajo Apache 2.0 (por compatibilidad con las fuentes OFL y los modelos Gemma). Las fuentes Nunito y Quicksand son OFL © de sus autores.
