package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object NumberPositions : Validation(
    "number positions",
    "numbers must be placed near starting points of their corresponding strokes"
) {
    private const val NUMBER_PATTERN = "[-+]?([0-9]*\\.)?[0-9]+([eE][-+]?[0-9]+)?"
    private final val PATH_START = Regex("^\\s*M\\s*(?<x>$NUMBER_PATTERN)[\\s,]*(?<y>$NUMBER_PATTERN)").toPattern()

    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val strokeStartingPoints = getStrokes(svg.strokePathsGroup.rootGroup).map { stroke ->
            val path = stroke.path.value
            val matcher = PATH_START.matcher(path)
            if ( ! matcher.find()) {
                return ValidationResult.Failed("Path '$path' has invalid starting segment: must start with Move-to (absolute)")
            }
            Pair(
                matcher.group("x").toDouble(),
                matcher.group("y").toDouble()
            )
        }
        val numberPositions = svg.strokeNumbersGroup.children.map { number ->
            val matrix = number.transform.matrix.elements
            Pair(matrix[4], matrix[5])
        }
        val distances = numberPositions.mapIndexed { idx, pos ->
            distance(pos, strokeStartingPoints[idx])
        }
        return if (distances.all { it <= MAX_DISTANCE }) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed(
                "Distances b/w stroke starting points and their corresponding numbers: $distances. " +
                    "Maximum allowed distance is $MAX_DISTANCE"
            )
        }
    }

    private const val MAX_DISTANCE = 25.0

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        return Math.sqrt(
            Math.pow(a.first - b.first, 2.0) + Math.pow(a.second - b.second, 2.0)
        )
    }
}
