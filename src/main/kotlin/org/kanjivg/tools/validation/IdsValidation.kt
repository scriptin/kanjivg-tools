package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

abstract class IdsValidation(
    override val name: String,
    override val description: String
) : Validation(name, description) {
    abstract protected fun getIds(group: KVGTag.StrokePathsSubGroup): List<String>

    abstract protected fun suffix(idx: Int): String

    override fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult {
        val ids = getIds(svg.strokePathsGroup.rootGroup)
        val mismatches = ids.mapIndexed { idx, id -> Pair(id, "kvg:$fileId${suffix(idx)}") }
            .filter { pair -> pair.first != pair.second }
        return if (mismatches.isEmpty()) {
            ValidationResult.Passed
        } else {
            ValidationResult.Failed(
                "mismatched ids (actual != expected): ${mismatches.map { "${it.first} != ${it.second}" }}"
            )
        }
    }
}
