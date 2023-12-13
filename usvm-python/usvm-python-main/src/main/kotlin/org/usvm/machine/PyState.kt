package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.rendering.ConverterToPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.machine.interpreters.symbolic.operations.tracing.SymbolicHandlerEvent
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.ps.types.SymbolTypeTree
import org.usvm.machine.ps.types.prioritizeTypes
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTarget
import org.usvm.types.UTypeStream
import org.usvm.machine.utils.MAX_CONCRETE_TYPES_TO_CONSIDER
import org.usvm.targets.UTargetsSet
import org.usvm.types.TypesResult

object PythonTarget: UTarget<SymbolicHandlerEvent<Any>, PythonTarget>()
private val targets = UTargetsSet.empty<PythonTarget, SymbolicHandlerEvent<Any>>()

class PyState(
    ctx: PyContext,
    private val pythonCallable: PythonUnpinnedCallable,
    val inputSymbols: List<UninterpretedSymbolicPythonObject>,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemory<PythonType, PythonCallable>,
    uModel: PyModel,
    val typeSystem: PythonTypeSystem,
    val preAllocatedObjects: PreallocatedObjects,
    var possibleTypesForNull: UTypeStream<PythonType> = typeSystem.topTypeStream(),
    callStack: UCallStack<PythonCallable, SymbolicHandlerEvent<Any>> = UCallStack(),
    pathLocation: PathNode<SymbolicHandlerEvent<Any>> = PathNode.root(),
    var delayedForks: PersistentList<DelayedFork> = persistentListOf(),
    private val mocks: MutableMap<MockHeader, UMockSymbol<UAddressSort>> = mutableMapOf(),
    val mockedObjects: MutableSet<UninterpretedSymbolicPythonObject> = mutableSetOf()
): UState<PythonType, PythonCallable, SymbolicHandlerEvent<Any>, PyContext, PythonTarget, PyState>(ctx, callStack, pathConstraints, memory, listOf(uModel), pathLocation, targets) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): PyState {
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        val newMemory = memory.clone(newPathConstraints.typeConstraints)
        return PyState(
            ctx,
            pythonCallable,
            inputSymbols,
            newPathConstraints,
            newMemory,
            pyModel,
            typeSystem,
            preAllocatedObjects.clone(),
            possibleTypesForNull,
            callStack,
            pathNode,
            delayedForks,
            mocks.toMutableMap(),  // copy
            mockedObjects.toMutableSet()  // copy
        )
    }

    override val entrypoint = pythonCallable
    override val isExceptional: Boolean = false  // TODO
    val meta = PythonExecutionStateMeta()
    val pyModel: PyModel
        get() = models.first() as? PyModel ?: error("Model PyState must be PyModel")
    fun buildPathAsList(): List<SymbolicHandlerEvent<Any>> =
        pathNode.allStatements.toList().reversed()

    fun makeTypeRating(delayedFork: DelayedFork): List<PythonType> {
        val candidates = when (val types = delayedFork.possibleTypes.take(MAX_CONCRETE_TYPES_TO_CONSIDER)) {
            is TypesResult.SuccessfulTypesResult -> types.mapNotNull { it as? ConcretePythonType }
            is TypesResult.TypesResultWithExpiredTimeout, is TypesResult.EmptyTypesResult ->
                return emptyList()
        }
        if (typeSystem is PythonTypeSystemWithMypyInfo) {
            val typeGraph = SymbolTypeTree(this, typeSystem.typeHintsStorage, delayedFork.symbol)
            return prioritizeTypes(candidates, typeGraph, typeSystem)
        }
        return candidates
    }

    fun mock(what: MockHeader): MockResult {
        val cached = mocks[what]
        if (cached != null)
            return MockResult(UninterpretedSymbolicPythonObject(cached, typeSystem), false, cached)
        // println("what.args: ${what.args}")
        val result = memory.mocker.call(what.method, what.args.map { it.address }.asSequence(), ctx.addressSort)
        mocks[what] = result
        what.methodOwner?.let { mockedObjects.add(it) }
        return MockResult(UninterpretedSymbolicPythonObject(result, typeSystem), true, result)
    }

    fun getMocksForSymbol(symbol: UninterpretedSymbolicPythonObject): List<Pair<MockHeader, UninterpretedSymbolicPythonObject>> =
        mocks.mapNotNull { (mockHeader, mockResult) ->
            if (mockHeader.methodOwner == symbol)
                mockHeader to UninterpretedSymbolicPythonObject(mockResult, typeSystem)
            else
                null
        }

    fun isTerminated(): Boolean {
        return meta.modelDied || meta.wasInterrupted || meta.wasExecuted && meta.objectsWithoutConcreteTypes == null
    }

    fun isInterestingForPathSelector(): Boolean {
        return !isTerminated() || delayedForks.isNotEmpty()
    }
}

class DelayedFork(
    val state: PyState,
    val symbol: UninterpretedSymbolicPythonObject,
    val possibleTypes: UTypeStream<PythonType>,
    val delayedForkPrefix: PersistentList<DelayedFork>
)

data class MockHeader(
    val method: TypeMethod,
    val args: List<UninterpretedSymbolicPythonObject>,
    var methodOwner: UninterpretedSymbolicPythonObject?
)

data class MockResult(
    val mockedObject: UninterpretedSymbolicPythonObject,
    val isNew: Boolean,
    val mockSymbol: UMockSymbol<UAddressSort>
)

class PythonExecutionStateMeta {
    var extractedFrom: UPathSelector<PyState>? = null
    var wasExecuted: Boolean = false
    var wasInterrupted: Boolean = false
    var modelDied: Boolean = false
    var objectsWithoutConcreteTypes: Set<VirtualPythonObject>? = null
    var lastConverter: ConverterToPythonObject? = null
    var generatedFrom: String = ""  // for debugging only
}