package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeNumbersCount : Validation(
    "stroke numbers count",
    "amount of strokes and amount of stroke numbers must be equal"
) {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val nStrokes = strokeCount(svg.strokePathsGroup.rootGroup)
        val nNumbers = svg.strokeNumbersGroup.children.size
        return if (nStrokes == nNumbers) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("#strokes = $nStrokes, #numbers = $nNumbers")
        }
    }

    private fun strokeCount(group: KVGTag.StrokePathsSubGroup): Int {
        return group.children.map { child ->
            when (child) {
                is KVGTag.StrokePathsSubGroup -> strokeCount(child)
                is KVGTag.Path -> 1
                else -> throw IllegalStateException("Found a child of type ${child.javaClass.name} inside stroke group")
            }
        }.sum()
    }
}
