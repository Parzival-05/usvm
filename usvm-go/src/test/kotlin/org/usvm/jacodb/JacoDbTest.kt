package org.usvm.jacodb

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.generated.StartDeserializer
import org.usvm.generated.ssa_Program
import java.io.File
import kotlin.system.measureTimeMillis

class JacoDbTest {
    @Test
    fun testMax2() {
        var project: GoProject

        val buffReader = File("./src/main/kotlin/org/usvm/generated/filled.txt").bufferedReader()

        val stopwatch = measureTimeMillis {
            val res = StartDeserializer(buffReader) as ssa_Program

            project = res.createJacoDBProject()
        }

        println(stopwatch)

        val machine = GoMachine(project, UMachineOptions())
        println(machine.analyzeAndResolve(project.methods.find { it.metName == "Max2" }!!))
    }
}