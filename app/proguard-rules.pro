# Cerlita — reglas R8 (el catálogo se parsea con kotlinx-serialization,
# que trae sus propias consumer rules; no se necesita nada extra por ahora).
# Si en el futuro se añade reflexión manual, documentar aquí cada keep rule.

# ONNX Runtime: carga sus clases Java por JNI; sin esto R8 las elimina
# en release y la sesión nativa revienta en tiempo de ejecución.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
