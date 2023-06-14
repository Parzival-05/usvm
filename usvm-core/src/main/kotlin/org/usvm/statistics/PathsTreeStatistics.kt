package org.usvm.statistics

import org.usvm.UState
import java.util.concurrent.ConcurrentHashMap

interface PathsTreeNode<State> {
    val children: Collection<PathsTreeNode<State>>
    val state: State?
    val parent: PathsTreeNode<State>?
    val ignoreTokens: Set<Int>
    fun addIgnoreToken(token: Int)
}

private class PathsTreeNodeImpl<State> (
    val childrenImpl: MutableSet<PathsTreeNodeImpl<State>>,
    override val parent: PathsTreeNodeImpl<State>?,
    var stateValue: State?
) : PathsTreeNode<State> {

    val locker = Any()
    override val ignoreTokens = HashSet<Int>()

    override val children: Collection<PathsTreeNode<State>>
        get() = synchronized(locker) { childrenImpl }

    override val state: State?
        get() = synchronized(locker) { stateValue }

    override fun addIgnoreToken(token: Int) {
        synchronized(locker) {
            ignoreTokens.add(token)
        }
    }
}

class PathsTreeStatistics<Method, Statement, State : UState<*, *, Method, Statement>>(initialState: State) : StatisticsObserver<Method, Statement, State> {

    private val stateIdToLeaf = ConcurrentHashMap<UInt, PathsTreeNodeImpl<State>>()

    val root: PathsTreeNode<State>

    init {
        val initialStateLeaf = PathsTreeNodeImpl(HashSet(), null, initialState)
        root = initialStateLeaf
        stateIdToLeaf[initialState.id] = initialStateLeaf
    }

    override fun onStateForked(
        parent: State,
        forks: Collection<State>
    ) {
        val parentNode = stateIdToLeaf[parent.id] ?: throw IllegalArgumentException("Trying to track fork of state not recorded in path tree statistics")
        require(forks.all { !stateIdToLeaf.contains(it.id) }) { "One of the forks is already recorded in paths tree statistics" }
        synchronized(parentNode.locker) {
            if (parentNode != stateIdToLeaf.getValue(parent.id)) {
                return
            }
            val newParentLeaf = PathsTreeNodeImpl(HashSet(), parentNode, parentNode.stateValue)
            parentNode.childrenImpl.add(newParentLeaf)
            parentNode.stateValue = null
            forks.forEach {
                val forkLeaf = PathsTreeNodeImpl(HashSet(), parentNode, it)
                stateIdToLeaf[it.id] = forkLeaf
                parentNode.childrenImpl.add(forkLeaf)
            }
            stateIdToLeaf[parent.id] = newParentLeaf
        }
    }
}
