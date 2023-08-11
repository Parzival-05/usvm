package org.usvm.utils

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import java.io.File

fun <T> withAdditionalPaths(additionalPaths: Collection<File>, block: () -> T): T {
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(
        namespace,
        ConcretePythonInterpreter.initialSysPath,
        "initial_sys_path"
    )
    ConcretePythonInterpreter.addObjectToNamespace(
        namespace,
        ConcretePythonInterpreter.initialSysModulesKeys,
        "initial_sys_modules"
    )
    ConcretePythonInterpreter.concreteRun(
        namespace,
        """
            import sys, copy
            sys.path += ${additionalPaths.joinToString(prefix = "[", separator = ", ", postfix = "]") { "\"${it.canonicalPath}\"" }}
        """.trimIndent()
    )

    val result = block()

    // returning paths back to initial state
    ConcretePythonInterpreter.concreteRun(
        namespace,
        """
            sys.path = copy.copy(initial_sys_path)
            current_modules = list(sys.modules.keys())
            for module in current_modules:
                if module not in initial_sys_modules:
                    print("module", module, flush=True)
                    sys.modules.pop(module)
            sys.path_importer_cache = {}
        """.trimIndent()
    )
    ConcretePythonInterpreter.decref(namespace)

    return result
}