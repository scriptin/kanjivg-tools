package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

object StrokeGroupsIds : IdsValidation(
    "stroke groups ids",
    "stroke groups must have properly set ids, numbered by depth-first traversal order"
) {
    override fun suffix(idx: Int): String = if (idx == 0) "" else "-g$idx"

    override fun getIds(group: KVGTag.StrokePathsSubGroup): List<String> {
        val ids = mutableListOf(group.id.value)
        group.children.forEach { child ->
            when (child) {
                is KVGTag.StrokePathsSubGroup -> ids.addAll(getIds(child))
                is KVGTag.Path -> {}
                else -> throw IllegalStateException("Found a child of type ${child.javaClass.name} inside stroke group")
            }
        }
        return ids
    }
}
