package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeIds : IdsValidation(
    "stroke ids",
    "strokes must have properly set ids, numbered by depth-first traversal order"
) {
    override fun suffix(idx: Int): String = "-s${idx + 1}"

    override fun getIds(group: KVGTag.StrokePathsSubGroup): List<String> = KVGTag.getStrokes(group).map { it.id.value }
}
