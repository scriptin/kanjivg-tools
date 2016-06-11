package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object WidthAndHeight : Validation("width and height", "<svg> element must have attributes width=109 and height=109") {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        return if (svg.width.value == 109 && svg.height.value == 109) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("width=${svg.width.value}, height=${svg.height.value}")
        }
    }
}
