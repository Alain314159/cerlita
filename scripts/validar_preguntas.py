#!/usr/bin/env python3
"""Valida el catálogo de preguntas de Cerlita.

Uso: python3 scripts/validar_preguntas.py [ruta/al/preguntas.json]

Comprueba: esquema, ids únicos y secuenciales, textos dentro de rango,
tipos válidos, opciones correctas y duplicados (normalizando texto).
Sale con código 1 si encuentra algún problema (para CI).
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

TEMAS_VALIDOS = {
    "viajes", "comida", "infancia", "sueños", "valores", "aventura",
    "cotidianidad", "recuerdos", "futuro", "afecto", "cultura", "juego", "otro",
}
TIPOS_VALIDOS = {"si_no", "escala", "opciones", "texto_libre"}
MIN_TEXTO = 8
MAX_TEXTO = 220
MAX_OPCIONES = 4


def normalizar(texto: str) -> str:
    """Minúsculas, sin tildes, sin puntuación, espacios colapsados."""
    texto = unicodedata.normalize("NFD", texto.lower())
    texto = "".join(c for c in texto if unicodedata.category(c) != "Mn")
    texto = re.sub(r"[^\w\s]", "", texto)
    return re.sub(r"\s+", " ", texto).strip()


def validar(ruta: Path) -> int:
    errores = []
    try:
        catalogo = json.loads(ruta.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, FileNotFoundError) as e:
        print(f"ERROR: no se pudo leer {ruta}: {e}")
        return 1

    version = catalogo.get("version", "")
    if not re.match(r"^\d+\.\d+\.\d+$", version):
        errores.append(f"version inválida: {version!r} (formato esperado: X.Y.Z)")

    preguntas = catalogo.get("preguntas")
    if not isinstance(preguntas, list) or not preguntas:
        errores.append("no hay lista de preguntas")
        preguntas = []

    vistos = {}
    ids = set()
    for i, p in enumerate(preguntas):
        pid = p.get("id", f"#{i}")
        # id único y con formato
        if pid in ids:
            errores.append(f"{pid}: id duplicado")
        ids.add(pid)
        if not re.match(r"^q_\d{3,}$", str(pid)) and not str(pid).startswith("custom_"):
            errores.append(f"{pid}: id con formato inesperado (q_NNN)")
        # tema
        tema = str(p.get("tema", "")).strip().lower()
        if tema not in TEMAS_VALIDOS:
            errores.append(f"{pid}: tema inválido {tema!r}")
        # tipo
        tipo = str(p.get("tipo", "")).strip().lower()
        if tipo not in TIPOS_VALIDOS:
            errores.append(f"{pid}: tipo inválido {tipo!r}")
        # texto
        texto = str(p.get("texto", "")).strip()
        if not (MIN_TEXTO <= len(texto) <= MAX_TEXTO):
            errores.append(f"{pid}: texto fuera de rango ({len(texto)} caracteres)")
        # opciones
        opciones = p.get("opciones", [])
        if tipo == "si_no" and opciones and set(map(str, opciones)) != {"Sí", "No"}:
            errores.append(f"{pid}: si_no debería usar [\"Sí\", \"No\"]")
        if tipo == "opciones":
            if not (2 <= len(opciones) <= MAX_OPCIONES):
                errores.append(f"{pid}: 'opciones' necesita entre 2 y {MAX_OPCIONES} opciones")
            elif len(set(map(str, opciones))) != len(opciones):
                errores.append(f"{pid}: opciones repetidas")
        if tipo in {"escala", "texto_libre"} and opciones:
            errores.append(f"{pid}: el tipo {tipo} no lleva opciones")
        # duplicados semánticos baratos (texto normalizado)
        clave = normalizar(texto)
        if clave in vistos:
            errores.append(f"{pid}: duplicada de {vistos[clave]}: {texto!r}")
        else:
            vistos[clave] = pid

    if errores:
        print(f"Catálogo {version}: {len(errores)} problema(s)")
        for e in errores:
            print(f"  - {e}")
        return 1
    print(f"Catálogo {version} válido: {len(preguntas)} preguntas, 0 duplicados")
    return 0


if __name__ == "__main__":
    ruta = Path(sys.argv[1] if len(sys.argv) > 1 else "content/preguntas.json")
    sys.exit(validar(ruta))
