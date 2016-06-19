package org.kanjivg.tools.tasks

import org.kanjivg.tools.validation.*

object ValidationTask : Task() {
    /**
     * List of available validations.
     * TODO: make it configurable with application.conf and command-line arguments
     */
    private final val validations = listOf(
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

    /**
     * Entry point for parsing and validation
     */
    fun validate(kvgDir: String, fileNameFilters: List<String>): Unit {
        val files = getFiles(kvgDir, fileNameFilters)
        val xmlInputFactory = createXMLInputFactory()
        printValidationsInfo()
        files.forEach { file ->
            val svg = parse(file, xmlInputFactory)
            val fileId = file.nameWithoutExtension
            val kanji = svg.strokePathsGroup.rootGroup.element?.value ?: "NA"
            val validationResults = validations.map { Pair(it.name, it.validate(fileId, svg)) }.toMap()
            val failedValidations = validationResults.filter { it.value !is ValidationResult.Passed }
            if (failedValidations.isEmpty()) {
                logger.info("ALL VALIDATIONS PASSED: {}/{}", kanji, file.name)
            } else {
                logger.warn(
                    "SOME VALIDATIONS FAILED: {}/{}\n{}\n", kanji, file.name,
                    failedValidations.map { "  - ${it.key}: ${it.value}" }.joinToString("\n")
                )
            }
        }
    }

    private fun printValidationsInfo() {
        val newLine = "\n  "
        val info = validations.map { "${it.name}: ${it.description}" }.joinToString(newLine)
        logger.info("Enabled validations:$newLine$info")
    }
}
