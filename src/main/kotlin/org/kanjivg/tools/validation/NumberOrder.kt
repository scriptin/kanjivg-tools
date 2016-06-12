package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object NumberOrder : Validation("number order", "numbers must be ordered in ascending order, starting from 1") {
    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val numbers = svg.strokeNumbersGroup.children.map { it.value }
        val properOrder = (1..numbers.size).toList()
        return if (numbers == properOrder) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed("number order is $numbers, expected $properOrder")
        }
    }
}
