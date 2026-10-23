package com.cerlita.core.ai

import java.io.File
import java.text.Normalizer

/**
 * Tokenizador WordPiece (familia BERT/BETO/e5/MiniLM) con vocabulario plano
 * (`vocab.txt`, una pieza por línea; el número de línea es el id).
 *
 * Reproduce la tokenización básica de `transformers.BertTokenizer`:
 *  - minúsculas y acentos según lo declare el manifiesto del paquete
 *    (los lee de `tokenizer_config.json` al empaquetar)
 *  - puntuación aislada como token propio (categorías Unicode `P` más los
 *    símbolos ASCII que BERT considera puntuación: $ + < = > ^ ` | ~)
 *  - emparejamiento voraz de la pieza más larga con prefijo `##`
 *
 * Las preguntas son frases cortas: se tokeniza a longitud dinámica (sin
 * relleno hasta maxLen), lo que acelera la inferencia ~10x.
 */
class TokenizadorWordPiece private constructor(
    private val vocab: Map<String, Int>,
    private val metadatos: MetadatosModelo,
) {

    private val idUnk: Int = vocab[TOKEN_UNK] ?: error("vocab.txt sin [UNK]")

    data class Codificacion(val ids: LongArray, val mascara: LongArray)

    fun codificar(texto: String): Codificacion {
        var normalizado = texto.trim()
        if (metadatos.minusculas) normalizado = normalizado.lowercase()
        if (metadatos.quitarAcentos) {
            normalizado = Normalizer.normalize(normalizado, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
        }
        val tokens = mutableListOf(TOKEN_CLS)
        tokens += tokenizacionBasica(normalizado).flatMap { piezasWordPiece(it) }
        tokens += TOKEN_SEP

        // Truncado conservando el [SEP] final (convención BERT).
        val recorte = if (tokens.size > metadatos.maxLen) {
            tokens.subList(0, metadatos.maxLen - 1) + TOKEN_SEP
        } else {
            tokens
        }
        val ids = LongArray(recorte.size) { i -> (vocab[recorte[i]] ?: idUnk).toLong() }
        val mascara = LongArray(recorte.size) { 1L }
        return Codificacion(ids, mascara)
    }

    /** Divide por espacios aislando la puntuación (incluye ¿ ¡ … « »). */
    private fun tokenizacionBasica(texto: String): List<String> =
        texto.replace(REGEX_PUNTUACION, " $1 ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    /** Emparejamiento voraz de la pieza más larga contra el vocabulario. */
    private fun piezasWordPiece(palabra: String): List<String> {
        if (palabra.isEmpty()) return emptyList()
        if (palabra.length > MAX_CARACTERES_PALABRA) return listOf(TOKEN_UNK)
        val piezas = mutableListOf<String>()
        var inicio = 0
        while (inicio < palabra.length) {
            var fin = palabra.length
            var encontrada: String? = null
            while (inicio < fin) {
                val candidata =
                    (if (inicio > 0) PREFIJO_CONTINUACION else "") + palabra.substring(inicio, fin)
                if (vocab.containsKey(candidata)) {
                    encontrada = candidata
                    break
                }
                fin--
            }
            if (encontrada == null) return listOf(TOKEN_UNK) // palabra completa fuera de vocabulario
            piezas += encontrada
            inicio = fin
        }
        return piezas
    }

    companion object {
        private const val TOKEN_CLS = "[CLS]"
        private const val TOKEN_SEP = "[SEP]"
        private const val TOKEN_UNK = "[UNK]"
        private const val TOKEN_PAD = "[PAD]"
        private const val PREFIJO_CONTINUACION = "##"
        private const val MAX_CARACTERES_PALABRA = 100

        /**
         * Categorías Unicode de puntuación + los símbolos ASCII que
         * BertTokenizer trata como puntuación. Difiere de \p{S} a propósito:
         * emojis o ★ no parten la palabra en transformers.
         */
        private val REGEX_PUNTUACION = Regex("([\\p{P}\\$\\+<=>\\^`|~])")

        fun desdeArchivo(archivo: File, metadatos: MetadatosModelo): TokenizadorWordPiece {
            val vocab = archivo.bufferedReader(Charsets.UTF_8).readLines()
                .mapIndexed { indice, linea -> linea.trim() to indice }
                .toMap()
            listOf(TOKEN_PAD, TOKEN_UNK, TOKEN_CLS, TOKEN_SEP).forEach { token ->
                require(vocab.containsKey(token)) { "vocab.txt no contiene el token especial $token" }
            }
            return TokenizadorWordPiece(vocab, metadatos)
        }

        /** Para pruebas: construir directamente desde una lista de piezas. */
        internal fun desdePiezas(piezas: List<String>, metadatos: MetadatosModelo): TokenizadorWordPiece {
            val vocab = piezas.mapIndexed { indice, pieza -> pieza to indice }.toMap()
            return TokenizadorWordPiece(vocab, metadatos)
        }
    }
}
