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
| **F1** | Perfil, banco de preguntas, juego local (rondas de 10), editor de preguntas, catálogo remoto, flujo comunitario | ✅ **Esta entrega** |
| **F2** | Modo pareja P2P (Nearby Connections, sin internet) | ⏳ Próxima |
| **F3** | IA local (embeddings) para detectar "muy en común" | ⏳ |
| **F4** | Deduplicación semántica + CI de contenido | ⏳ (base ya en CI) |

### Pantallas de la Fase 1
- **Home**: mascotas animadas, estadísticas y acceso directo a jugar
- **Ronda local**: 10 preguntas al azar con 4 tipos de respuesta (sí/no, escala 1-5, opciones y texto libre). Se retoma sola si cierras la app (el estado vive en Room)
- **Perfil**: tu nombre, el de tu pareja y tu mascota (cerdita 🐷 o koala 🐨)
- **Mis preguntas**: editor para crear/activar/borrar preguntas propias
- **Ajustes**: buscar actualizaciones del catálogo (JSON en GitHub)

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
core/network/       Cliente Nearby Connections (esqueleto, Fase 2)
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

## Decisión de IA (Fase 3): EmbeddingGemma en vez de MiniLM

El plan original apuntaba a `all-MiniLM-L6-v2`. Al revisarlo en octubre 2026 se detectó que **es un modelo solo-inglés** (y de 2021), inadecuado para una app en español. Decisión tomada:

- **Modelo elegido: `google/embeddinggemma-300m`** (Apache 2.0)
  - Mejor modelo multilingüe abierto <500M del benchmark MTEB: **100+ idiomas**, incluido el español
  - 308M parámetros; embeddings de 768d **truncables a 128d** (Matryoshka): ahorra 6× en almacenamiento
  - Cuantizado INT8 corre en <200 MB de RAM; soporte Android oficial vía ONNX Runtime / LiteRT
  - Se descargará **bajo demanda** (no se empaqueta en el APK)
- **Plan B ligero**: `intfloat/multilingual-e5-small` (118M, 384d, ~30 MB INT8) si hiciera falta mantener la app ultra-ligera
- EmbeddingGemma 2 (oct 2026, multimodal) se monitoriza: cuando llegue su servicio en ML Kit, podría eliminarse el bundle del modelo por completo

La deduplicación semántica del catálogo correrá **server-side** en GitHub Actions (Python), sin tocar el móvil.

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
- Sin permisos en Fase 1 salvo `INTERNET` (buscar actualizaciones del catálogo)
- En Fase 2, los permisos de Bluetooth/Ubicación solo se pedirán al activar el modo pareja

## Licencia

Código bajo Apache 2.0 (por compatibilidad con las fuentes OFL y los modelos Gemma). Las fuentes Nunito y Quicksand son OFL © de sus autores.
