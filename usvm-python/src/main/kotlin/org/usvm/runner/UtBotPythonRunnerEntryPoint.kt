package org.usvm.runner

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.venv.VenvConfig
import org.usvm.python.ps.PyPathSelectorType
import java.io.File

fun main(args: Array<String>) {
    var prefixNumberOfArgs = 9
    require(args.size >= prefixNumberOfArgs + 1) { "Incorrect number of arguments" }
    val mypyDirPath = args[0]
    val socketPort = args[1].toIntOrNull() ?: error("Second argument must be integer")
    val moduleName = args[2]
    val functionName = args[3]
    val clsName = if (args[4] == "<no_class>") null else args[4]
    val timeoutPerRunMs = args[5].toLongOrNull() ?: error("Sixth argument must be integer")
    val timeoutMs = args[6].toLongOrNull() ?: error("Seventh argument must be integer")
    val pathSelectorName = args[7]
    val pathSelector = PyPathSelectorType.valueOf(pathSelectorName)
    if (args[8] != "<no_venv>") {
        prefixNumberOfArgs += 2
        require(args.size >= prefixNumberOfArgs + 1) { "Incorrect number of arguments" }
        val venvConfig = VenvConfig(
            basePath = File(args[8]),
            libPath = File(args[9]),
            binPath = File(args[10])
        )
        ConcretePythonInterpreter.setVenv(venvConfig)
        System.err.println("VenvConfig: $venvConfig")
    } else {
        System.err.println("No VenvConfig.")
    }
    val programRoots = args.drop(prefixNumberOfArgs)
    val runner = PyMachineSocketRunner(
        File(mypyDirPath),
        programRoots.map { File(it) }.toSet(),
        "localhost",
        socketPort,
        pathSelector
    )
    runner.use {
        if (clsName == null) {
            it.analyzeFunction(moduleName, functionName, timeoutPerRunMs, timeoutMs)
        } else {
            it.analyzeMethod(moduleName, functionName, clsName, timeoutPerRunMs, timeoutMs)
        }
    }
}
