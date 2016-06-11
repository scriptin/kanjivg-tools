package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object NumberRootGroupStyle : StyleValidation(
    "number root group style",
    "root group for numbers must have a style with default properties set",
    mapOf("font-size" to "8", "fill" to "#808080")
) {
    override fun getStyle(svg: KVGTag.SVG): Map<String, String> = svg.strokeNumbersGroup.style.attributes

    override fun getElementPath(): String = "svg > g:last-child"
}
