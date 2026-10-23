#!/usr/bin/env python3
"""
Publica el zip del modelo en el release `modelo-ia-vN` del repositorio.

Uso:

    python scripts/subir_modelo.py cerlita-modelo-ia-v1.zip --token ghp_xxx
    # o con variable de entorno: GITHUB_TOKEN=ghp_xxx python scripts/subir_modelo.py …

Crea el release si no existe, borra el asset anterior con el mismo nombre
y sube el nuevo. Después, la app lo encuentra sola desde
Ajustes → Modelo de IA → "Descargar desde GitHub".
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
REPO_POR_DEFECTO = os.environ.get("CERLITA_REPO", "Alain314159/cerlita")


def pedir(metodo: str, url: str, token: str, cuerpo: bytes | None = None, tipo: str = "application/json"):
    solicitud = urllib.request.Request(url, method=metodo, data=cuerpo)
    solicitud.add_header("Authorization", f"Bearer {token}")
    solicitud.add_header("Accept", "application/vnd.github+json")
    if cuerpo is not None:
        solicitud.add_header("Content-Type", tipo)
    with urllib.request.urlopen(solicitud) as respuesta:
        crudo = respuesta.read()
        if crudo.startswith(b"{") or crudo.startswith(b"["):
            return respuesta.status, json.loads(crudo)
        return respuesta.status, crudo


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", help="Ruta al zip generado por empaquetar_modelo.py")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
    parser.add_argument("--repo", default=REPO_POR_DEFECTO)
    parser.add_argument("--version", type=int, default=1)
    args = parser.parse_args()

    if not args.token:
        sys.exit("Falta el token (--token ghp_xxx o variable GITHUB_TOKEN).")

    zip_path = Path(args.zip)
    if not zip_path.is_file():
        sys.exit(f"No existe {zip_path}")

    tag = f"modelo-ia-v{args.version}"
    nombre_asset = zip_path.name

    # 1) Buscar o crear el release.
    try:
        _, release = pedir("GET", f"{API}/repos/{args.repo}/releases/tags/{tag}", args.token)
        print(f"Release existente: {release['html_url']}")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        _, release = pedir(
            "POST",
            f"{API}/repos/{args.repo}/releases",
            args.token,
            json.dumps(
                {
                    "tag_name": tag,
                    "name": f"Modelo IA Cerlita v{args.version}",
                    "body": (
                        "Modelo de embeddings para detectar preguntas duplicadas. "
                        "Se instala desde la app: Ajustes → Modelo de IA → Descargar desde GitHub "
                        "(o Importar desde el almacenamiento con este mismo zip)."
                    ),
                }
            ).encode(),
        )
        print(f"Release creado: {release['html_url']}")

    # 2) Borrar asset previo con el mismo nombre (idempotente).
    for asset in release.get("assets", []):
        if asset["name"] == nombre_asset:
            pedir("DELETE", f"{API}/repos/{args.repo}/releases/assets/{asset['id']}", args.token)
            print(f"Asset anterior borrado: {nombre_asset}")

    # 3) Subir el zip.
    print(f"Subiendo {nombre_asset} ({zip_path.stat().st_size / 1e6:.1f} MB)…")
    estado, respuesta = pedir(
        "POST",
        f"{UPLOADS}/repos/{args.repo}/releases/{release['id']}/assets?name={nombre_asset}",
        args.token,
        cuerpo=zip_path.read_bytes(),
        tipo="application/zip",
    )
    url_descarga = respuesta.get("browser_download_url", "?") if isinstance(respuesta, dict) else "?"
    print(f"OK ({estado}) → {url_descarga}")


if __name__ == "__main__":
    main()
