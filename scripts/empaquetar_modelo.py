#!/usr/bin/env python3
"""
Empaqueta un modelo de HuggingFace como zip instalable por Cerlita.

Zip resultante (formato 1, ver MetadatosModelo.kt):

    cerlita-modelo-ia-v1.zip
    ├── metadatos.json    manifiesto que la app valida antes de instalar
    ├── modelo.onnx       pesos ONNX con cuantización INT8 dinámica
    └── vocab.txt         tokenizador WordPiece (BERT/BETO/e5/MiniLM)

Requisitos (una vez):

    uv venv venv-modelo && uv pip install --python venv-modelo/bin/python \\
        torch --index-url https://download.pytorch.org/whl/cpu
    uv pip install --python venv-modelo/bin/python transformers onnxruntime huggingface_hub

Uso:

    python scripts/empaquetar_modelo.py \
        --modelo hiiamsid/sentence_similarity_spanish_es \
        --salida cerlita-modelo-ia-v1.zip

El modelo por defecto es BETO afinado para similitud de frases en español
(mismo tokenizador WordPiece que implementa la app; pooling medio, igual
que sentence-transformers). Al terminar hace una verificación de humo:
embeddings de frases de prueba con el ONNX final y sus similitudes.
"""
import argparse
import json
import math
import sys
import tempfile
import zipfile
from pathlib import Path

FRASES_PRUEBA = [
    "¿Cuál es tu color favorito?",
    "¿Qué color prefieres tú?",
    "¿Qué música te pone de buen humor?",
]


def exportar_onnx(modelo_id: str, ruta: Path) -> None:
    import torch
    from transformers import AutoModel, AutoTokenizer

    tokenizador = AutoTokenizer.from_pretrained(modelo_id)
    modelo = AutoModel.from_pretrained(modelo_id)
    modelo.eval()

    class Envoltura(torch.nn.Module):
        """Entradas y salida explícitas: el grafo queda con nombres estables
        (input_ids / attention_mask / token_type_ids → last_hidden_state)."""

        def __init__(self, interno):
            super().__init__()
            self.interno = interno

        def forward(self, input_ids, attention_mask, token_type_ids):
            return self.interno(
                input_ids=input_ids,
                attention_mask=attention_mask,
                token_type_ids=token_type_ids,
            ).last_hidden_state

    envoltura = Envoltura(modelo).eval()
    ejemplo = tokenizador("hola mundo", return_tensors="pt")
    entradas = (
        ejemplo["input_ids"],
        ejemplo["attention_mask"],
        ejemplo["token_type_ids"],
    )
    ejes = {
        "input_ids": {0: "batch", 1: "seq"},
        "attention_mask": {0: "batch", 1: "seq"},
        "token_type_ids": {0: "batch", 1: "seq"},
        "last_hidden_state": {0: "batch", 1: "seq"},
    }

    print(f"[export] torch {torch.__version__} → {ruta}")
    try:
        # Exportador clásico (TorchScript): determinista para BERT/BETO.
        torch.onnx.export(
            envoltura,
            entradas,
            str(ruta),
            input_names=["input_ids", "attention_mask", "token_type_ids"],
            output_names=["last_hidden_state"],
            dynamic_axes=ejes,
            opset_version=17,
            dynamo=False,
        )
    except TypeError:
        # torch.onnx.export sin parámetro dynamo (versiones viejas).
        torch.onnx.export(
            envoltura,
            entradas,
            str(ruta),
            input_names=["input_ids", "attention_mask", "token_type_ids"],
            output_names=["last_hidden_state"],
            dynamic_axes=ejes,
            opset_version=17,
        )


def verificar(ruta_onnx: Path, vocab: Path, metadatos: dict) -> None:
    """Verificación de humo con onnxruntime: mismas entradas que la app."""
    import numpy as np
    import onnxruntime as ort

    sesion = ort.InferenceSession(str(ruta_onnx), providers=["CPUExecutionProvider"])

    # Tokenizador mínimo idéntico al de la app (WordPiece básico).
    piezas = vocab.read_text(encoding="utf-8").splitlines()
    vocabulario = {p: i for i, p in enumerate(piezas)}

    def codificar(texto: str):
        tokens = ["[CLS]"] + [t for t in texto.replace(",", " , ").replace("¿", " ¿ ").replace("?", " ? ").split() if t]
        # suficientemente bueno para la prueba: palabras completas o UNK
        ids = [vocabulario.get(t, vocabulario["[UNK]"]) for t in tokens] + [vocabulario["[SEP]"]]
        return np.array([ids], dtype=np.int64), np.ones((1, len(ids)), dtype=np.int64)

    def embed(texto: str):
        ids, mascara = codificar(texto)
        salida = sesion.run(
            ["last_hidden_state"],
            {
                "input_ids": ids,
                "attention_mask": mascara,
                "token_type_ids": np.zeros_like(ids),
            },
        )[0][0]
        if metadatos["pooling"] == "cls":
            vector = salida[0]
        else:
            vector = salida.mean(axis=0)
        if metadatos["normalizar"]:
            vector = vector / math.sqrt(float((vector * vector).sum()) + 1e-12)
        return vector

    vectores = [embed(f) for f in FRASES_PRUEBA]

    def cos(a, b):
        return float((a * b).sum())

    parecido = cos(vectores[0], vectores[1])
    distinto = cos(vectores[0], vectores[2])
    print(f"[check] sim('{FRASES_PRUEBA[0]}', '{FRASES_PRUEBA[1]}') = {parecido:.3f}")
    print(f"[check] sim('{FRASES_PRUEBA[0]}', '{FRASES_PRUEBA[2]}') = {distinto:.3f}")
    if parecido <= distinto:
        print("[check] ADVERTENCIA: el par similar no puntúa más alto que el distinto")
    else:
        print("[check] OK: el par de preguntas parecidas puntúa por encima del distinto")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modelo", default="hiiamsid/sentence_similarity_spanish_es")
    parser.add_argument("--salida", default="cerlita-modelo-ia-v1.zip")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--max-len", type=int, default=128)
    parser.add_argument("--nombre", default=None)
    args = parser.parse_args()

    try:
        import torch  # noqa: F401
        import transformers  # noqa: F401
        from huggingface_hub import hf_hub_download
        from onnxruntime.quantization import quantize_dynamic
    except ImportError as e:
        sys.exit(f"Falta una dependencia ({e}). Instala las que figuran en el docstring.")

    nombre = args.nombre or args.modelo.split("/")[-1].lower()

    with tempfile.TemporaryDirectory(prefix="cerlita-modelo-") as tmp_str:
        tmp = Path(tmp_str)

        # 1) Export a ONNX: salida last_hidden_state; el pooling medio lo
        #    hace la app con la máscara de atención.
        print(f"[1/4] Exportando {args.modelo} a ONNX…")
        modelo_fp32 = tmp / "modelo-fp32.onnx"
        exportar_onnx(args.modelo, modelo_fp32)

        # 2) Cuantización INT8 dinámica (MatMul en enteros): ~4x menos
        #    espacio y bastante más rápido en CPU de gama baja.
        print("[2/4] Cuantizando a INT8…")
        modelo_int8 = tmp / "modelo.onnx"
        quantize_dynamic(str(modelo_fp32), str(modelo_int8))

        # 3) Tokenizador + banderas de normalización exactas de transformers.
        print("[3/4] Descargando vocab.txt y configuración del tokenizador…")
        vocab = Path(hf_hub_download(args.modelo, "vocab.txt"))
        try:
            tok_cfg = json.loads(
                Path(hf_hub_download(args.modelo, "tokenizer_config.json")).read_text()
            )
        except Exception:
            tok_cfg = {}
        minusculas = bool(tok_cfg.get("do_lower_case", False))
        # transformers: si strip_accents es None, hereda de do_lower_case.
        strip = tok_cfg.get("strip_accents")
        quitar_acentos = bool(strip) if strip is not None else minusculas

        cfg = json.loads(Path(hf_hub_download(args.modelo, "config.json")).read_text())
        dimensiones = int(cfg.get("hidden_size", 768))

        # 4) Manifiesto + zip (los nombres internos son parte del contrato
        #    con la app; no los cambies sin subir el campo "formato").
        metadatos = {
            "formato": 1,
            "nombre": nombre,
            "version": args.version,
            "dimensiones": dimensiones,
            "tokenizador": "wordpiece",
            "archivoModelo": "modelo.onnx",
            "archivoTokenizador": "vocab.txt",
            "maxLen": args.max_len,
            "normalizar": True,
            "pooling": "media",
            "minusculas": minusculas,
            "quitarAcentos": quitar_acentos,
            "plantilla": "ninguna",
        }
        (tmp / "metadatos.json").write_text(
            json.dumps(metadatos, ensure_ascii=False, indent=2), encoding="utf-8"
        )

        print("[4/4] Comprimiendo paquete…")
        salida = Path(args.salida)
        with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            zf.write(tmp / "metadatos.json", "metadatos.json")
            zf.write(modelo_int8, "modelo.onnx")
            zf.write(vocab, "vocab.txt")

        mb = salida.stat().st_size / 1e6
        print(f"\nOK → {salida} ({mb:.1f} MB)")
        print(json.dumps(metadatos, ensure_ascii=False, indent=2))

        verificar(modelo_int8, vocab, metadatos)


if __name__ == "__main__":
    main()
