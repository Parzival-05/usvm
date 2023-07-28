package org.usvm.interpreter.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.interpreter.*
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.*

class ConverterToPythonObject(
    private val ctx: UContext,
    private val model: PyModel
) {
    val forcedConcreteTypes = mutableMapOf<UHeapRef, PythonType>()
    private val constructedObjects = mutableMapOf<UHeapRef, PythonObject>()
    private val virtualObjects = mutableSetOf<Pair<VirtualPythonObject, PythonObject>>()
    private var numberOfGeneratedVirtualObjects: Int = 0
    init {
        restart()
    }
    fun restart() {
        constructedObjects.clear()
        virtualObjects.clear()
        val nullRef = model.eval(ctx.nullRef) as UConcreteHeapRef
        val defaultObject = constructVirtualObject(InterpretedInputSymbolicPythonObject(nullRef, model))
        constructedObjects[ctx.nullRef] = defaultObject
        numberOfGeneratedVirtualObjects = 0
    }
    fun getPythonVirtualObjects(): Collection<PythonObject> = virtualObjects.map { it.second }
    fun getUSVMVirtualObjects(): Set<VirtualPythonObject> = virtualObjects.map { it.first }.toSet()
    fun numberOfVirtualObjectUsages(): Int = numberOfGeneratedVirtualObjects

    fun convert(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        require(obj.model == model)
        val cached = constructedObjects[obj.address]
        if (cached != null)
            return cached
        val result = when (obj.getFirstType()) {
            null -> error("Type stream for interpreted object is empty")
            TypeOfVirtualObject -> constructVirtualObject(obj)
            pythonInt -> convertInt(obj)
            pythonBool -> convertBool(obj)
            pythonObjectType -> ConcretePythonInterpreter.eval(emptyNamespace, "object()")
            pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            pythonList -> convertList(obj)
            else -> TODO()
        }
        constructedObjects[obj.address] = result
        return result
    }

    private fun constructVirtualObject(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val default = forcedConcreteTypes[obj.address]?.let { TypeDefaultValueProvider.provide(it) }
        if (default != null)
            return default

        numberOfGeneratedVirtualObjects += 1
        val virtual = VirtualPythonObject(obj)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects.add(virtual to result)
        return result
    }

    private fun convertInt(obj: InterpretedInputSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent(ctx).toString())

    private fun convertBool(obj: InterpretedInputSymbolicPythonObject): PythonObject =
        when (obj.getBoolContent(ctx)) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> error("Not reachable")
        }

    private fun convertList(obj: InterpretedInputSymbolicPythonObject): PythonObject = with(ctx) {
        val size = obj.model.uModel.heap.readArrayLength(obj.address, pythonList) as KInt32NumExpr
        val listOfPythonObjects = List(size.value) { index ->
            val indexExpr = mkSizeExpr(index)
            val element = obj.model.uModel.heap.readArrayIndex(obj.address, indexExpr, pythonList, addressSort) as UConcreteHeapRef
            val elemInterpretedObject = InterpretedInputSymbolicPythonObject(element, obj.model)
            convert(elemInterpretedObject)
        }
        return ConcretePythonInterpreter.makeList(listOfPythonObjects)
    }
}