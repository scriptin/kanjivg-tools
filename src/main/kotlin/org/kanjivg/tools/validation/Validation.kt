package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

/**
 * Validations of SVG files
 */
abstract class Validation(open val name: String, open val description: String) {
    abstract fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult
}
