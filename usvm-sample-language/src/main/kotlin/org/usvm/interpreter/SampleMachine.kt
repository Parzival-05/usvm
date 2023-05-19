package org.usvm.interpreter

import kotlinx.collections.immutable.persistentListOf
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.language.argumentsCount
import org.usvm.language.localsCount
import org.usvm.ps.DfsPathSelector

/**
 * The sample language machine.
 */
class SampleMachine(
    program: Program,
    private val maxStates: Int = 40,
) : UMachine<SampleState, Method<*>>() {
    private val applicationGraph = SampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem()
    private val components = SampleLanguageComponents(typeSystem)
    private val ctx = UContext(components)
    private val solver = ctx.solver<Field<*>, SampleType, Method<*>>()

    private val stateAccessor = SampleStateOperations.create(
        applicationGraph,
        methodToArgumentsCount = { it.argumentsCount },
        methodToLocalsCount = { it.localsCount }
    )
    private val interpreter = with(stateAccessor) { SampleInterpreter(ctx) }
    private val resultModelConverter = ResultModelConverter(ctx)

    fun analyze(method: Method<*>): Collection<ProgramExecutionResult> {
        val collectedStates = mutableListOf<SampleState>()
        run(
            method,
            onState = { state ->
                if (!isInterestingState(state)) {
                    collectedStates += state
                }
            },
            continueAnalyzing = ::isInterestingState,
            shouldStop = { collectedStates.size >= maxStates }
        )
        return collectedStates.map { resultModelConverter.convert(it, method) }
    }

    override fun getInterpreter(target: Method<*>) = interpreter

    override fun getPathSelector(target: Method<*>): UPathSelector<SampleState> {
        val ps = DfsPathSelector<SampleState>()
        val initialState = getInitialState(target)
        ps.add(sequenceOf(initialState))
        return ps
    }

    private fun getInitialState(method: Method<*>): SampleState =
        SampleState.create(ctx).apply {
            with(stateAccessor) { callInitialMethod(method) }
            val model = solver.emptyModel()
            models = persistentListOf(model)
        }

    private fun isInterestingState(state: SampleState): Boolean {
        return state.callStack.isNotEmpty() && state.exceptionRegister == null
    }
}