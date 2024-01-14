package org.usvm.machine

import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USort
import org.usvm.api.UnknownSortException
import org.usvm.machine.type.Type

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: MutableMap<Long, Int> = mutableMapOf()
    private var allocationIndex: MutableMap<Long, MutableMap<Int, Int>> = mutableMapOf()
    private var allocationOffset: MutableMap<Long, Int> = mutableMapOf()

    fun getArgsCount(method: Long): Int = argsCount[method]!!

    fun getAllocationIndex(method: Long, index: Int): Int {
        val offset = allocationOffset[method]!!
        val indices = allocationIndex[method]!!
        if (index in indices) {
            return indices[index]!!
        }
        indices[index] = offset
        allocationOffset[method] = offset + 1
        return offset
    }

    fun setMethodInfo(method: Long, info: GoMethodInfo) {
        argsCount[method] = info.parametersCount
        allocationIndex[method] = mutableMapOf()
        allocationOffset[method] = info.parametersCount + info.variablesCount
    }

    fun typeToSort(type: Type): USort = when (type) {
        Type.BOOL -> boolSort
        Type.INT8, Type.UINT8 -> bv8Sort
        Type.INT16, Type.UINT16 -> bv16Sort
        Type.INT32, Type.UINT32 -> bv32Sort
        Type.INT64, Type.UINT64 -> bv64Sort
        Type.FLOAT32 -> fp32Sort
        Type.FLOAT64 -> fp64Sort
        Type.ARRAY, Type.SLICE, Type.MAP, Type.STRUCT, Type.INTERFACE -> addressSort
        Type.POINTER -> pointerSort
        else -> throw UnknownSortException()
    }
}
