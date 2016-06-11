package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object NumberRootGroupId : Validation(
    "number root group id",
    "root group for number must have an id of format 'kvg:StrokeNumbers_XXXXX', where 'XXXXX' is a file name w/o extension"
) {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val id = svg.strokeNumbersGroup.id.value
        return if (id == "kvg:StrokeNumbers_$fileId") {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("id=$id, expected 'kvg:StrokeNumbers_$fileId'")
        }
    }
}
