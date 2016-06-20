package org.kanjivg.tools.tasks.config

import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Wrapper for configuration listing included and excluded files
 */
class FilesConfig {
    private final val logger = LoggerFactory.getLogger(javaClass)
    final val directory: String
    final val includedFileFilters: List<String>
    final val excludedFileFilters: List<String>

    /**
     * @param directory Directory for scanning
     * @param config Configuration with two keys: 'included' and 'excluded'. See 'application.conf'
     */
    constructor(directory: String, config: Config) {
        this.directory = directory
        this.includedFileFilters = config.getString("included").split(",")
        this.excludedFileFilters = if (config.getIsNull("excluded")) {
            emptyList()
        } else {
            config.getString("excluded").split(",")
        }
    }

    /**
     * Get filtered list of files from given directory
     */
    fun getFiles(): List<File> {
        val included = prepareFilters(includedFileFilters)
        val excluded = prepareFilters(excludedFileFilters)
        val files =  File(directory).walk()
            .onFail { file, ioException ->
                throw RuntimeException("Error processing a file '$file'", ioException)
            }
            .filter { it.isFile }
            .filter { file -> included.indexOfFirst { it.matches(file.nameWithoutExtension) } >= 0 }
            .filter { file -> excluded.indexOfFirst { it.matches(file.nameWithoutExtension) } == -1 }
            .toList()
        logger.info(
            "Found {} files matching patterns {} and not matching {}",
            files.size, includedFileFilters, excludedFileFilters
        )
        return files
    }

    /**
     * Escape filter strings: '*' becomes '.*', everything else is treated as literal
     */
    private fun prepareFilters(filters: List<String>): List<Regex> {
        return filters.map { filter ->
            Regex(filter.split("*").map { Regex.escape(it) }.joinToString(".*"))
        }
    }
}
