package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeRootGroupStyle : StyleValidation(
    "stroke root group style",
    "root group for strokes must have a style with default properties set",
    mapOf(
        "fill" to "none",
        "stroke" to "#000000",
        "stroke-width" to "3",
        "stroke-linecap" to "round",
        "stroke-linejoin" to "round"
    )
) {
    override fun getStyle(svg: KVGTag.SVG): Map<String, String> = svg.strokePathsGroup.style.attributes

    override fun getElementPath(): String = "svg > g:first-child"
}
