package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

/**
 * Base validation class
 */
abstract class Validation(open val name: String, open val description: String) {
    abstract fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult
}
