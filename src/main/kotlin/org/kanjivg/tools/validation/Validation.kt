package org.kanjivg.tools.validation

import org.kanjivg.tools.KVGTag

/**
 * Base validation class
 */
abstract class Validation(open val name: String, open val description: String) {
    abstract fun validate(fileId: String, svg: KVGTag.SVG): ValidationResult

    /**
     * Get a flat list of strokes from a given stroke group
     */
    fun getStrokes(group: KVGTag.StrokePathsSubGroup): List<KVGTag.Path> {
        val strokes = mutableListOf<KVGTag.Path>()
        group.children.forEach { child ->
            when (child) {
                is KVGTag.StrokePathsSubGroup -> strokes.addAll(getStrokes(child))
                is KVGTag.Path -> strokes.add(child)
                else -> throw IllegalStateException("Found a child of type ${child.javaClass.name} inside stroke group")
            }
        }
        return strokes
    }
}
