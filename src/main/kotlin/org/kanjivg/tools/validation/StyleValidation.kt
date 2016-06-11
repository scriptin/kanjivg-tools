package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

abstract class StyleValidation(
    override val name: String,
    override val description: String,
    val requiredStyle: Map<String, String>
) : Validation(name, description) {
    abstract fun getStyle(svg: KVGTag.SVG): Map<String, String>

    abstract fun getElementPath(): String

    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val style = getStyle(svg)
        val missing = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val extra = mutableListOf<String>()
        requiredStyle.keys.forEach { key ->
            if (style[key] == null) missing.add(key)
            else if (style[key] != requiredStyle[key]) invalid.add(key)
        }
        style.keys.forEach { key -> if (requiredStyle[key] == null) extra.add(key) }
        val errors = mutableListOf<String>()
        if (missing.isNotEmpty()) errors.add("missing keys: $missing")
        if (invalid.isNotEmpty()) errors.add("wrong values: ${invalid.map { key ->
            "$key='${style[key]}' (expected '${requiredStyle[key]}')"
        }}")
        if (extra.isNotEmpty()) errors.add("extra keys: $extra")
        return if (errors.isEmpty()) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("invalid style on element [${getElementPath()}]: ${errors.joinToString(", ")}")
        }
    }
}
