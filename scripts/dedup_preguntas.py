#!/usr/bin/env python3
"""Detección semántica de duplicados para el catálogo de Cerlita (server-side).

Se ejecuta en GitHub Actions, NUNCA en el teléfono: por eso puede permitirse
un modelo de embeddings multilingüe de verdad sin engordar el APK (la app
queda en ~14 MB y sin una sola línea de IA).

Modelo: paraphrase-multilingual-MiniLM-L12-v2 (sentence-transformers),
especializado en pares de frases con significado equivalente en 50+ idiomas.

Umbrales de similitud coseno contra el catálogo existente:
  > 0.85      -> DUPLICADA: se comenta en el issue y se cierra.
  0.70-0.85   -> REVISAR: zona gris; se etiqueta "revisar" y un humano decide.
  < 0.70      -> NUEVA: se agrega al catálogo (commit + versión).

Entrada (igual que agregar_pregunta.py):
  - Issue de GitHub (env ISSUE_BODY), usado por el workflow.
  - Argumentos directos para pruebas locales:
      python3 scripts/dedup_preguntas.py --texto "¿...?" [--mock]

Salida:
  - dedup.txt       mensaje humano para comentar en el issue.
  - GITHUB_OUTPUT   decision=NUEVA|REVISAR|DUPLICADA y similitud=0.xx.
"""
import argparse
import json
import os
import sys
from pathlib import Path

# Reutiliza el parsing del issue y la normalización de agregar_pregunta.py
sys.path.insert(0, str(Path(__file__).resolve().parent))
import agregar_pregunta as ap  # noqa: E402

CATALOGO = Path(__file__).resolve().parent.parent / "content" / "preguntas.json"
SALIDA = Path("dedup.txt")

UMBRAL_DUPLICADA = 0.85
UMBRAL_REVISAR = 0.70
MODELO = "paraphrase-multilingual-MiniLM-L12-v2"
TOP_N = 3  # coincidencias a mostrar en el comentario


def cargar_modelo():
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODELO)


def similitudes(modelo, candidata: str, textos: list[str]) -> list[float]:
    import numpy as np

    vectores = modelo.encode(
        [candidata] + textos,
        normalize_embeddings=True,
    )
    candidata_vec = vectores[0]
    resto = np.asarray(vectores[1:])
    # vectores L2-normalizados: el producto punto ES el coseno
    return (resto @ candidata_vec).tolist()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--texto", help="texto de la pregunta candidata")
    parser.add_argument(
        "--mock",
        action="store_true",
        help="prueba local sin torch: embeddings falsos por trigramas",
    )
    args = parser.parse_args()

    # ------------------------------------------------------------------
    # Candidata: argumento o issue
    # ------------------------------------------------------------------
    if args.texto:
        candidata = args.texto.strip()
    else:
        datos = ap.desde_issue()
        candidata = (datos or {}).get("texto", "").strip() if datos else ""

    if not candidata:
        _salida(
            "DUPLICADA_CHECK_INVALIDO",
            "No se pudo leer la pregunta del issue (falta la sección '### La pregunta').",
        )
        return 1

    catalogo = json.loads(CATALOGO.read_text(encoding="utf-8"))
    preguntas = catalogo.get("preguntas", [])
    if not preguntas:
        _salida("NUEVA", "El catálogo está vacío: cualquier pregunta es nueva.")
        return 0

    textos = [str(p.get("texto", "")) for p in preguntas]

    # ------------------------------------------------------------------
    # Duplicado exacto (barato) antes del semántico
    # ------------------------------------------------------------------
    clave = ap.normalizar(candidata)
    for p in preguntas:
        if ap.normalizar(str(p.get("texto", ""))) == clave:
            _salida(
                "DUPLICADA",
                f"Es copia exacta de **{p['id']}**: “{p['texto']}”. "
                "Gracias, ¡ya la teníamos!",
            )
            return 0

    # ------------------------------------------------------------------
    # Similitud semántica
    # ------------------------------------------------------------------
    if args.mock:
        sims = _similitudes_mock(candidata, textos)
    else:
        modelo = cargar_modelo()
        sims = similitudes(modelo, candidata, textos)

    pares = sorted(
        zip(preguntas, sims),
        key=lambda par: par[1],
        reverse=True,
    )
    mejor_p, mejor_s = pares[0]

    if mejor_s > UMBRAL_DUPLICADA:
        mensaje = (
            f"Demasiado parecida a **{mejor_p['id']}** "
            f"(similitud {mejor_s:.0%}): “{mejor_p['texto']}”.\n\n"
            "Se cierra como duplicada. Si crees que es distinta, coméntalo "
            "y se reabre."
        )
        _salida("DUPLICADA", mensaje, mejor_s, pares)
        return 0

    if mejor_s >= UMBRAL_REVISAR:
        detalle = "\n".join(
            f"- {p['id']} (similitud {s:.0%}): “{p['texto']}”" for p, s in pares[:TOP_N]
        )
        mensaje = (
            f"Zona gris: se parece a preguntas existentes (máximo {mejor_s:.0%}). "
            "Queda etiquetada como **revisar** para decisión humana.\n\n" + detalle
        )
        _salida("REVISAR", mensaje, mejor_s, pares)
        return 0

    _salida(
        "NUEVA",
        f"No se parece a nada del catálogo (máximo {mejor_s:.0%}). "
        "Se agregará al catálogo.",
        mejor_s,
        pares,
    )
    return 0


def _salida(decision: str, mensaje: str, similitud: float | None = None, pares=None) -> None:
    SALIDA.write_text(mensaje, encoding="utf-8")
    print(f"DECISIÓN: {decision}" + (f" ({similitud:.2f})" if similitud is not None else ""))
    print(mensaje)
    # Salidas para el workflow (GitHub Actions)
    salida_github = os.getenv("GITHUB_OUTPUT")
    if salida_github:
        with open(salida_github, "a", encoding="utf-8") as f:
            f.write(f"decision={decision}\n")
            if similitud is not None:
                f.write(f"similitud={similitud:.2f}\n")


def _similitudes_mock(candidata: str, textos: list[str]) -> list[float]:
    """Embeddings falsos por hashing de trigramas: SOLO para depurar el
    workflow en local (decisión no fiable, trayectoria de código idéntica)."""
    import numpy as np

    def vector(texto: str) -> np.ndarray:
        v = np.zeros(256, dtype=np.float32)
        for tri in [texto[i : i + 3].lower() for i in range(len(texto) - 2)]:
            v[hash(tri) % 256] += 1.0
        norma = float(np.linalg.norm(v))
        return v / norma if norma else v

    return [float(vector(candidata) @ vector(t)) for t in textos]


if __name__ == "__main__":
    sys.exit(main())
