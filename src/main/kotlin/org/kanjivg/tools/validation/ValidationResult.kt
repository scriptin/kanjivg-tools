package org.kanjivg.tools.validation

/**
 * Validation result is similar to Either/Try monad: [Passed] represents success, [Failed] represents failure
 */
sealed class ValidationResult {
    object Passed : ValidationResult() {
        override fun toString(): String  = "PASSED"
    }

    class Failed(val reason: String) : ValidationResult() {
        override fun toString(): String = "FAILED: $reason"
    }
}
