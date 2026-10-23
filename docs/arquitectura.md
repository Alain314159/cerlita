# Arquitectura de Cerlita

## Visión general

Cerlita es **offline-first**: todo el estado de juego vive en Room, en cada teléfono. El único elemento remoto es el **catálogo de preguntas** (un JSON público en este repositorio), pensado para actualizarse sin publicar una nueva versión de la app.

```
┌──────────────┐     UI Compose (Material 3, tema oscuro kawaii)
│  app         │──── navegación (Navigation Compose), ViewModels, DI manual
└──────┬───────┘
       │ contratos
┌──────▼───────┐     modelos puros: Pregunta, Respuesta, Ronda, Perfil
│  domain      │     interfaces de repositorio
└──────┬───────┘
       │ implementaciones
┌──────▼───────┐     Room (cerlita.db) · DataStore · catálogo assets/remoto
│  data        │     reemplazo atómico del catálogo OFICIAL (transacción)
└──────────────┘
core/designsystem  tema, tipografía variable, componentes, mascotas Canvas
core/common         DispatcherProvider (tests deterministas)
core/network        Nearby Connections (Fase 2: P2P sin internet)
core/ai             EmbeddingEngine + similitud coseno (Fase 3)
```

## Modelo de datos (Room v1)

| Tabla | Contenido |
|-------|-----------|
| `preguntas` | id, tema, tipo, texto, opcionesJson, origen (OFICIAL/CUSTOM), activa, agregadaEn |
| `respuestas` | preguntaId, sesionId, autor (CERDITA/KOALA), valor, fecha |
| `rondas` | id, modo, estado, preguntaIdsJson, indiceActual, creada, finalizada |
| `perfil` | fila única: nombrePropio, animal, nombrePareja, actualizado |

La **ronda activa es la fuente de verdad del juego**: `observeRondaActiva()` reconstruye la pantalla aunque el proceso muera (mata el killed-activity problem). El borrador de texto libre también se conserva en el ViewModel mientras la ronda exista.

## Ciclo del catálogo

1. **Primer arranque**: `GestorCatalogo.sembrarCatalogoInicial()` vuelca `assets/preguntas.json` (40 preguntas v1.0.0) a Room y marca la versión en DataStore.
2. **Ajustes → Buscar actualizaciones**: `HttpURLConnection` descarga `content/preguntas.json` por raw URL, parsea con kotlinx-serialization (tolerante a campos nuevos) y, si cambia `version`, sustituye todas las preguntas OFICIAL en una transacción. Las CUSTOM del usuario nunca se tocan.
3. **Contribuciones desde la app**: el editor crea issues precargados; el workflow `procesar-pregunta.yml` valida con `scripts/agregar_pregunta.py` (duplicados normalizados, rangos, tipos) y abre PR.

### Contrato del JSON

```json
{
  "version": "1.0.0",
  "updated_at": "2026-10-22T10:00:00Z",
  "preguntas": [
    { "id": "q_001", "tema": "viajes", "tipo": "si_no",
      "texto": "¿Alguna vez han hecho un viaje improvisado juntos?",
      "opciones": ["Sí", "No"] }
  ]
}
```

- `tipo`: `si_no` | `escala` | `opciones` | `texto_libre`
- `tema`: uno de la lista canónica (viajes, comida, infancia, sueños, valores, aventura, cotidianidad, recuerdos, futuro, afecto, cultura, juego, otro)
- Reglas: texto 8-220 caracteres; `opciones` requiere 2-4; ids `q_NNN` secuenciales; sin duplicados (validado en CI)

## Roadmap técnico

### Fase 2 — Modo pareja (P2P) — ✅ Implementada
- `core/network` (`CerlitaNearbyClient : CanalPareja`): anunciador + descubridor simultáneos (`Strategy.P2P_CLUSTER`), auto-aceptación al conectarse y saludo mutuo (nombre + animal).
- **Protocolo** (`ProtocoloP2P.kt`): payload de bytes JSON con sobre `{"tipo": "saludo"|"inicio"|"respuesta"|"fin"}`. Los tipos desconocidos se ignoran (compatibilidad hacia adelante). El host (quien conecta) envía `InicioRonda` con id de ronda compartido + preguntas completas; ambos persisten la misma ronda en Room (`crearRondaPareja`).
- **Cruce de respuestas**: cada respuesta se envía como `RespuestaRecibida` y se persiste en el otro teléfono mediante `ReceptorP2P` (alcance de aplicación, no de pantalla). Índice único `(sesionId, preguntaId, autor)` + `INSERT REPLACE` hacen la escritura idempotente (migración Room 1→2, no destructiva).
- **Resumen en vivo**: el resumen de fin de ronda observa el flujo de respuestas de Room, así las respuestas que llegan tarde aparecen solas; chip "Coinciden" cuando ambas respuestas cerradas coinciden.
- **Desconexión**: `onDisconnected` → estado DESCONECTADO; la ronda sigue en Room y se juega en solitario. Reconexión desde el lobby.
- Permisos: API 31+ `BLUETOOTH_SCAN` (`neverForLocation`) + `BLUETOOTH_ADVERTISE` (runtime, se piden al entrar al lobby); API ≤30 `ACCESS_FINE_LOCATION` + `BLUETOOTH/BLUETOOTH_ADMIN` (legacy, normales).

### Fase 3 — IA local (embeddings)
- `core/ai`: implementación de `EmbeddingEngine` con **EmbeddingGemma-300m** cuantizado INT8 vía ONNX Runtime 1.31 (o LiteRT). Descarga bajo demanda al activar la comparación.
- Embeddings de 128d (MRL) para respuestas de texto libre; similitud coseno; umbral 0.75 = "muy en común".
- Deduplicación semántica del catálogo en GitHub Actions (Python, offline del móvil).

## Decisiones de riesgo conocidas
- **Desconexión a mitad de ronda (F2)**: la ronda persiste en Room → se juega en solitario con banner informativo; las respuestas que alcanzaron a cruzarse quedan ambas guardadas.
- **RAM baja con el modelo (F3)**: se recomienda ≥3 GB RAM; fallback al Plan B (e5-small) para gama baja.
- **Dedup imperfecta**: la exacta ya está; la semántica (F4) arranca con umbral 0.85 + revisión humana vía PR.
