package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeRootGroupId : Validation(
    "stroke root group id",
    "root group for strokes must have an id of format 'kvg:StrokePaths_XXXXX', where 'XXXXX' is a file name w/o extension"
) {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val id = svg.strokePathsGroup.id.value
        return if (id == "kvg:StrokePaths_$fileId") {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("id=$id, expected 'kvg:StrokePaths_$fileId'")
        }
    }
}
