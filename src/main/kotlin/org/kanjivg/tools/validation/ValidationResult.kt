package org.kanjivg.tools.validation

/**
 * Validation result is similar to Either<Failure, Success>
 */
sealed class ValidationResult {
    object Passed : ValidationResult() {
        override fun toString(): String  = "PASSED"
    }

    class Failed(val reason: String) : ValidationResult() {
        override fun toString(): String = "FAILED: $reason"
    }
}
