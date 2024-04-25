package org.usvm.jacodb.type

import org.usvm.generated.types_Basic
import org.usvm.jacodb.GoType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    override val typeOperationsTimeout: Duration
) : UTypeSystem<GoType> {
    private val goAnyType = types_Basic()
    private val topTypeStream by lazy { USupportTypeStream.from(this, goAnyType) }

    override fun topTypeStream(): UTypeStream<GoType> {
        return topTypeStream
    }

    override fun findSubtypes(type: GoType): Sequence<GoType> {
        return emptySequence()
    }

    override fun isInstantiable(type: GoType): Boolean {
        return false
    }

    override fun isFinal(type: GoType): Boolean {
        return false
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        return false
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        return false
    }
}