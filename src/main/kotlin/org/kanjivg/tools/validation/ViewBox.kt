package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object ViewBox : Validation("viewbox", "<svg> element must have attribute viewBox='0 0 109 109'") {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val vb = svg.viewBox
        return if (vb.x == 0 && vb.y == 0 && vb.width == 109 && vb.height == 109) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("viewBox='${vb.x} ${vb.y} ${vb.width} ${vb.height}'")
        }
    }
}
