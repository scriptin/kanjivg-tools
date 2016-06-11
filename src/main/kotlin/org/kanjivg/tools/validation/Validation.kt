package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

/**
 * Validations of SVG files
 */
sealed class Validation(val name: String, val description: String) {
    abstract fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult

    object WidthAndHeight : Validation(
        "width and height",
        "<svg> element must have attributes width=109 and height=109"
    ) {
        override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
            return if (svg.width.value == 109 && svg.height.value == 109) {
                ValidationResult.Passed
            } else {
                ValidationResult.Failed("width=${svg.width.value}, height=${svg.height.value}")
            }
        }
    }

    object ViewBox : Validation(
        "viewbox",
        "<svg> element must have attribute viewBox='0 0 109 109'"
    ) {
        override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
            val vb = svg.viewBox
            return if (vb.x == 0 && vb.y == 0 && vb.width == 109 && vb.height == 109) {
                ValidationResult.Passed
            } else {
                ValidationResult.Failed("viewBox='${vb.x} ${vb.y} ${vb.width} ${vb.height}'")
            }
        }
    }
}
