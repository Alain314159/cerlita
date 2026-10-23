#!/usr/bin/env python3
"""Agrega una pregunta de la comunidad al catálogo de Cerlita.

Fuentes de entrada:
  1. Issue de GitHub (env ISSUE_TITLE / ISSUE_BODY) — usado por el workflow
     "procesar-pregunta.yml".
  2. Argumentos directos para pruebas locales:
     python3 scripts/agregar_pregunta.py --texto "¿...?" --tema viajes --tipo si_no

Acciones:
  - Valida la pregunta (texto, tema, tipo, opciones).
  - Comprueba duplicados exactos (texto normalizado).
  - Asigna el siguiente id q_NNN, inserta ordenada por tema, sube el parche
    de versión (1.0.0 -> 1.0.1) y actualiza updated_at.
  - Escribe el resultado en resultado.txt para que el workflow lo comente.
"""
import argparse
import json
import re
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

CATALOGO = Path(__file__).resolve().parent.parent / "content" / "preguntas.json"
RESULTADO = Path("resultado.txt")

TEMAS_VALIDOS = {
    "viajes", "comida", "infancia", "sueños", "valores", "aventura",
    "cotidianidad", "recuerdos", "futuro", "afecto", "cultura", "juego", "otro",
}
TIPOS_VALIDOS = {"si_no", "escala", "opciones", "texto_libre"}
MIN_TEXTO = 8
MAX_TEXTO = 220
MAX_OPCIONES = 4


def normalizar(texto: str) -> str:
    texto = unicodedata.normalize("NFD", texto.lower())
    texto = "".join(c for c in texto if unicodedata.category(c) != "Mn")
    texto = re.sub(r"[^\w\s]", "", texto)
    return re.sub(r"\s+", " ", texto).strip()


def seccion(cuerpo: str, titulo: str) -> str:
    """Extrae el contenido de '### titulo' hasta el siguiente '###'."""
    patron = re.compile(
        rf"^###\s*{re.escape(titulo)}\s*$(.*?)(?=^###|\Z)",
        re.MULTILINE | re.DOTALL | re.IGNORECASE,
    )
    m = patron.search(cuerpo)
    if not m:
        return ""
    # quitar comentarios HTML y líneas vacías sobrantes
    contenido = re.sub(r"<!--.*?-->", "", m.group(1), flags=re.DOTALL)
    return contenido.strip()


def desde_issue() -> dict | None:
    import os
    cuerpo = os.environ.get("ISSUE_BODY", "")
    if not cuerpo:
        return None
    texto = seccion(cuerpo, "La pregunta")
    tema = seccion(cuerpo, "Tema").splitlines()[0].strip().lower() if seccion(cuerpo, "Tema") else ""
    tipo = seccion(cuerpo, "Tipo de respuesta").splitlines()[0].strip().lower() if seccion(cuerpo, "Tipo de respuesta") else ""
    opciones_crudas = seccion(cuerpo, 'Opciones (solo si el tipo es "opciones")')
    opciones = []
    if opciones_crudas and opciones_crudas.strip().upper() != "N/A":
        for linea in opciones_crudas.splitlines():
            linea = linea.strip()
            if linea and linea.upper() != "N/A":
                opciones.extend(o.strip() for o in linea.split(",") if o.strip())
    return {"texto": texto, "tema": tema, "tipo": tipo, "opciones": opciones}


def siguiente_id(preguntas: list) -> str:
    maximo = 0
    for p in preguntas:
        m = re.match(r"^q_(\d+)$", str(p.get("id", "")))
        if m:
            maximo = max(maximo, int(m.group(1)))
    return f"q_{maximo + 1:03d}"


def bump_version(version: str) -> str:
    try:
        mayor, menor, parche = (int(x) for x in version.split("."))
        return f"{mayor}.{menor}.{parche + 1}"
    except ValueError:
        return "1.0.1"


def agregar(texto: str, tema: str, tipo: str, opciones: list) -> tuple[bool, str]:
    texto = texto.strip()
    tema = tema.strip().lower()
    tipo = tipo.strip().lower()

    # --- validaciones ---
    if not (MIN_TEXTO <= len(texto) <= MAX_TEXTO):
        return False, f"El texto debe tener entre {MIN_TEXTO} y {MAX_TEXTO} caracteres (tiene {len(texto)})."
    if tema not in TEMAS_VALIDOS:
        return False, f"Tema inválido: {tema!r}. Válidos: {sorted(TEMAS_VALIDOS)}"
    if tipo not in TIPOS_VALIDOS:
        return False, f"Tipo inválido: {tipo!r}. Válidos: {sorted(TIPOS_VALIDOS)}"
    if tipo == "opciones":
        if not (2 <= len(opciones) <= MAX_OPCIONES):
            return False, "El tipo 'opciones' necesita entre 2 y 4 opciones."
        if len(set(opciones)) != len(opciones):
            return False, "Hay opciones repetidas."
    if tipo == "si_no":
        opciones = ["Sí", "No"]
    if tipo in {"escala", "texto_libre"}:
        opciones = []

    catalogo = json.loads(CATALOGO.read_text(encoding="utf-8"))
    preguntas = catalogo.get("preguntas", [])

    clave = normalizar(texto)
    for p in preguntas:
        if normalizar(str(p.get("texto", ""))) == clave:
            return False, f"Parece duplicada de {p['id']}: “{p['texto']}”."

    nueva = {
        "id": siguiente_id(preguntas),
        "tema": tema,
        "tipo": tipo,
        "texto": texto,
    }
    if opciones:
        nueva["opciones"] = opciones

    preguntas.append(nueva)
    # orden estable: por tema y luego id
    preguntas.sort(key=lambda p: (str(p.get("tema", "")), str(p.get("id", ""))))
    catalogo["preguntas"] = preguntas
    catalogo["version"] = bump_version(catalogo.get("version", "1.0.0"))
    catalogo["updated_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    CATALOGO.write_text(
        json.dumps(catalogo, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return True, (
        f"Pregunta añadida como {nueva['id']} (catálogo v{catalogo['version']}). "
        "El PR se abrirá automáticamente para revisión."
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--texto")
    parser.add_argument("--tema")
    parser.add_argument("--tipo")
    parser.add_argument("--opciones", default="", help="separadas por comas")
    args = parser.parse_args()

    if args.texto:
        datos = {
            "texto": args.texto,
            "tema": args.tema or "",
            "tipo": args.tipo or "",
            "opciones": [o.strip() for o in args.opciones.split(",") if o.strip()],
        }
    else:
        datos = desde_issue()
        if not datos or not datos["texto"]:
            mensaje = "No se pudo leer la pregunta desde el issue (falta la sección '### La pregunta')."
            RESULTADO.write_text(mensaje, encoding="utf-8")
            print("ERROR: " + mensaje)
            return 1

    ok, mensaje = agregar(datos["texto"], datos["tema"], datos["tipo"], datos["opciones"])
    RESULTADO.write_text(mensaje, encoding="utf-8")
    print(("OK: " if ok else "ERROR: ") + mensaje)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
