package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

class NumberPositions(val maxDistance: Double) : Validation(
    "number positions",
    "numbers must be placed near starting points of their corresponding strokes"
) {
    private final val NUMBER_PATTERN = "[-+]?([0-9]*\\.)?[0-9]+([eE][-+]?[0-9]+)?"
    private final val PATH_START = Regex("^\\s*[Mm]\\s*(?<x>$NUMBER_PATTERN)[\\s,]*(?<y>$NUMBER_PATTERN)").toPattern()

    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val strokeStartingPoints = KVGTag.getStrokes(svg.strokePathsGroup.rootGroup).map { stroke ->
            val path = stroke.path.value
            val matcher = PATH_START.matcher(path)
            if ( ! matcher.find()) {
                return ValidationResult.Error(
                    "Path '$path' has invalid starting segment: must start with a 'move-to' instruction"
                )
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
        return if (distances.all { it <= maxDistance }) {
            ValidationResult.Passed
        } else {
            val tooFar = distances.mapIndexed { ind, dist -> Pair(ind, dist) }
                .filter { it.second > maxDistance }
                .map { "${it.first + 1} has distance ${(it.second * 100).toInt().toDouble() / 100.00}" }
                .joinToString("; ")
            ValidationResult.Failed(
                "These number are too far from starting points of their strokes: $tooFar. " +
                    "Maximum allowed distance is $maxDistance"
            )
        }
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        return Math.sqrt(
            Math.pow(a.first - b.first, 2.0) + Math.pow(a.second - b.second, 2.0)
        )
    }
}
