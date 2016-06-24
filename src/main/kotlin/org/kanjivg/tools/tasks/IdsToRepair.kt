package org.kanjivg.tools.tasks

/**
 * DTO for repair flags of "id" attributes
 */
class IdsToRepair(
    val strokeRootGroupId: Boolean,
    val numberRootGroupId: Boolean,
    val strokeGroupIds: Boolean,
    val strokeIds: Boolean
) : ThingsToRepair {
    override fun needsRepair(): Boolean = strokeRootGroupId || numberRootGroupId || strokeGroupIds || strokeIds
}
