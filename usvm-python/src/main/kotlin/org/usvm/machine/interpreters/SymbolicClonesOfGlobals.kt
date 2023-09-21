package org.usvm.machine.interpreters

import org.usvm.language.NamedSymbolForCPython
import org.usvm.language.SymbolForCPython

object SymbolicClonesOfGlobals {
    private val clonesMap: MutableMap<String, SymbolForCPython> = mutableMapOf()
    init {
        clonesMap["int"] = SymbolForCPython(null, ConcretePythonInterpreter.intConctructorRef)
    }

    fun getNamedSymbols(): Array<NamedSymbolForCPython> =
        clonesMap.map { NamedSymbolForCPython(it.key, it.value) }.toTypedArray()
}