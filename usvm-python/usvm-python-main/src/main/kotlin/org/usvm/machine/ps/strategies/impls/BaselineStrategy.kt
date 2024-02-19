package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

fun makeBaselineActionStrategy(
    random: Random
): WeightedActionStrategy<DelayedForkState, BaselineDelayedForkGraph> =
    WeightedActionStrategy(
        random,
        listOf(
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork,
            PeekExecutedStateWithConcreteType
        )
    )

sealed class BaselineAction(
    weight: Double
): Action<DelayedForkState, BaselineDelayedForkGraph>(weight) {
    protected fun chooseAvailableVertex(
        available: List<DelayedForkGraphInnerVertex<DelayedForkState>>,
        random: Random
    ): DelayedForkGraphInnerVertex<DelayedForkState> {
        require(available.isNotEmpty())
        val idx = random.nextInt(0, available.size)
        return available[idx]
    }
}

object PeekFromRoot: BaselineAction(0.6) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithoutDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> =
        Peek(graph.pathSelectorWithoutDelayedForks)

    override fun toString(): String = "PeekFromRoot"
}

object ServeNewDelayedFork: BaselineAction(0.3) {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeNewDelayedFork"
}

object PeekFromStateWithDelayedFork: BaselineAction(0.088) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        return Peek(graph.pathSelectorWithDelayedForks)
    }

    override fun toString(): String = "PeekFromStateWithDelayedFork"
}

object ServeOldDelayedFork: BaselineAction(0.012) {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeOldDelayedFork"
}

object PeekExecutedStateWithConcreteType: BaselineAction(100.0) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorForExecutedStatesWithConcreteTypes.isEmpty()

    override fun makeAction(
        graph: BaselineDelayedForkGraph,
        random: Random
    ): PyPathSelectorAction<DelayedForkState> =
        Peek(graph.pathSelectorForExecutedStatesWithConcreteTypes)

}

class BaselineDelayedForkStrategy: DelayedForkStrategy<DelayedForkState> {
    private var lastIdx = -1
    override fun chooseTypeRating(state: DelayedForkState): TypeRating {
        require(state.size > 0) {
            "Cannot choose type rating from empty set"
        }
        lastIdx = (lastIdx + 1) % state.size
        val idx = lastIdx
        return state.getAt(idx)
    }
}

class BaselineDFGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>
): DelayedForkGraphCreation<DelayedForkState, BaselineDelayedForkGraph> {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex<DelayedForkState>): BaselineDelayedForkGraph =
        BaselineDelayedForkGraph(basePathSelectorCreation, root)
}

open class BaselineDelayedForkGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<DelayedForkState>
): DelayedForkGraph<DelayedForkState>(root) {

    internal val pathSelectorWithoutDelayedForks = basePathSelectorCreation()
    internal val pathSelectorWithDelayedForks = basePathSelectorCreation()
    internal val pathSelectorForExecutedStatesWithConcreteTypes = basePathSelectorCreation()
    internal val aliveNodesAtDistanceOne = mutableSetOf<DelayedForkGraphInnerVertex<DelayedForkState>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        super.addVertex(df, vertex)
        if (vertex.parent == root) {
            logger.debug("Adding node to aliveNodesAtDistanceOne")
            aliveNodesAtDistanceOne.add(vertex)
        }
    }

    override fun updateVertex(vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        if (vertex.delayedForkState.isDead)
            aliveNodesAtDistanceOne.remove(vertex)
    }

    override fun addExecutedStateWithConcreteTypes(state: PyState) {
        pathSelectorForExecutedStatesWithConcreteTypes.add(listOf(state))
    }

    override fun addStateToVertex(vertex: DelayedForkGraphVertex<DelayedForkState>, state: PyState) {
        when (vertex) {
            is DelayedForkGraphRootVertex -> pathSelectorWithoutDelayedForks.add(listOf(state))
            is DelayedForkGraphInnerVertex -> {
                pathSelectorWithDelayedForks.add(listOf(state))
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}