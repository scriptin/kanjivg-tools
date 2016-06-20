package org.kanjivg.tools.tasks

import org.kanjivg.tools.tasks.config.FilesConfig
import org.kanjivg.tools.tasks.config.ValidationsConfig
import org.kanjivg.tools.validation.*

object ValidationTask : Task() {
    /**
     * Entry point for parsing and validation
     */
    fun validate(filesConfig: FilesConfig, validationsConfig: ValidationsConfig): Unit {
        printValidationsInfo(validationsConfig.enabledValidations)
        filesConfig.getFiles().forEach { file ->
            val svg = parse(file)
            val fileId = file.nameWithoutExtension
            val kanji = svg.strokePathsGroup.rootGroup.element?.value ?: "NA"
            val validationResults = validationsConfig.enabledValidations.map { validation ->
                Pair(validation.name, validation.validate(fileId, svg))
            }.toMap()
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

    private fun printValidationsInfo(enabledValidations: List<Validation>) {
        val newLine = "\n  "
        val info = enabledValidations.map { "${it.name}: ${it.description}" }.joinToString(newLine)
        logger.info("Enabled validations:$newLine$info")
    }
}
