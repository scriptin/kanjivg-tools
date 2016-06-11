package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeIds : IdsValidation(
    "stroke ids",
    "strokes must have properly set ids, numbered by depth-first traversal order"
) {
    override fun suffix(idx: Int): String = "-s${idx - 1}"

    override fun getIds(group: KVGTag.StrokePathsSubGroup): List<String> {
        val ids = mutableListOf<String>()
        group.children.forEach { child ->
            when (child) {
                is KVGTag.StrokePathsSubGroup -> ids.addAll(getIds(child))
                is KVGTag.Path -> ids.add(child.id.value)
                else -> throw IllegalStateException("Found a child of type ${child.javaClass.name} inside stroke group")
            }
        }
        return ids
    }
}
