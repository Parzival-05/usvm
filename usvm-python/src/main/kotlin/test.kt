import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonMachine
import org.usvm.language.Callable
import org.usvm.language.PythonProgram

fun main() {
    //val globals = ConcretePythonInterpreter.getNewNamespace()
    //ConcretePythonInterpreter.concreteRun(globals, "x = 10 ** 100")
    //ConcretePythonInterpreter.concreteRun(globals, "print('Hello from Python!\\nx is', x, flush=True)")

    val program = PythonProgram(
        """
        def f(x, y, z):
            if x + y > 100:
                return 0
            y += 10 ** 9
            if 0 < x + z + 1 < 100 and y > 0:
                return 1
            elif x + 3 < -2 - z and x < y:
                return 2
            elif x * 100 % 7 == 0 and z + y % 100 == 0:
                return 3
            elif x % 155 == 0 and x + y - z < 0:
                return 4
            elif (x + z) % 10 == 0 and x + y > 0:
                return 5
            elif (x - 10 ** 8 + y) * 50 % 9 == 0 and y // 88 == 5:
                return 6
            elif z == 15789 and y + x > 10 ** 9:
                return 7
            elif x + y + z == -10 ** 9 and x != 0 and z == 2598:
                return 8
            else:
                return 9
        """.trimIndent()
    )
    val function = Callable.constructCallableFromName(3, "f")
    val machine = PythonMachine(program)
    val start = System.currentTimeMillis()
    val iterations = machine.use { it.analyze(function) }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
    println("${machine.solver.cnt}")
}
