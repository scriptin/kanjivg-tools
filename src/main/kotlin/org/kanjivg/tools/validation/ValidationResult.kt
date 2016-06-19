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

    /**
     * When validation cannot be performed or some unexpected error occurs,
     * result must be reported as "error" rather than just "failed"
     */
    class Error : ValidationResult {
        final val message: String
        final val exception: Exception?

        constructor(message: String) {
            this.message = message
            this.exception = null
        }

        constructor(exception: Exception) {
            this.message = exception.message ?: exception.toString()
            this.exception = exception
        }

        override fun toString(): String {
            val stackTrace = exception?.let { ":\n" + exception.stackTrace } ?: ""
            return "ERROR: $message$stackTrace"
        }
    }
}
