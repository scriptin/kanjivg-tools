package org.kanjivg.tools.tasks.config

import com.typesafe.config.Config
import org.kanjivg.tools.validation.*

/**
 * Wrapper for configuration of validations
 */
class ValidationsConfig {
    final val allValidations = listOf(
        WidthAndHeight,
        ViewBox,
        StrokeRootGroupId,
        StrokeRootGroupStyle,
        StrokeGroupsIds,
        StrokeIds,
        NumberRootGroupId,
        NumberRootGroupStyle,
        StrokeNumbersCount,
        NumberOrder,
        NumberPositions
    )
    final val enabledValidations: List<Validation>

    constructor(config: Config) {
        val enabledString = config.getString("enabled")
        if (enabledString.toLowerCase() == "all") {
            enabledValidations = allValidations
        } else {
            // Comparison is case-sensitive!
            val enabledList = enabledString.split(",")
            val allowedList = allValidations.map { it.javaClass.simpleName }
            if (enabledList.intersect(allowedList).size != enabledList.size) {
                val extra = enabledList.filter { ! allowedList.contains(it) }
                throw RuntimeException(
                    "List of enabled validations contains invalid values: $extra; allowed values: $allowedList"
                )
            }
            enabledValidations = allValidations.filter {
                enabledList.contains(it.javaClass.simpleName)
            }
        }
    }
}
