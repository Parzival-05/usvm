package org.usvm.util

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.AnnotationConsumer
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import java.util.stream.Stream
import kotlin.test.assertTrue

annotation class Options(
    val strategies: Array<PathSelectionStrategy>,
    val combinationStrategy: PathSelectorCombinationStrategy = PathSelectorCombinationStrategy.INTERLEAVED,
    val stopOnCoverage: Int = 100,
    val timeout: Long = 20_000,
    val coverageZone: CoverageZone = CoverageZone.METHOD,
    val solverType: SolverType = SolverType.YICES,
)

@ParameterizedTest
@ArgumentsSource(MachineOptionsArgumentsProvider::class)
annotation class UsvmTest(val options: Array<Options>)

class MachineOptionsArgumentsProvider : ArgumentsProvider, AnnotationConsumer<UsvmTest> {

    private var options = listOf<UMachineOptions>()

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
        return options.map { Arguments.of(it) }.stream()
    }

    override fun accept(t: UsvmTest?) {
        requireNotNull(t)
        options = t.options.map {
            UMachineOptions(
                pathSelectionStrategies = it.strategies.toList(),
                pathSelectorCombinationStrategy = it.combinationStrategy,
                stopOnCoverage = it.stopOnCoverage,
                timeoutMs = it.timeout,
                coverageZone = it.coverageZone,
                solverType = it.solverType
            )
        }
    }
}

inline fun disableTest(message: String, body: () -> Unit) =
    checkErrorNotChanged(message, body) {
        it.isEmpty()
                || it.startsWith("Some properties were not discovered")
//                || it.startsWith("Expected")
    }

inline fun checkErrorNotChanged(message: String, body: () -> Unit, predicate: (String) -> Boolean) {
    val needCheck = predicate(message)
    Assumptions.assumeTrue(needCheck, message)

    body()

//    try {
//        body()
//        error("Passed but was disabled: $message")
//    } catch (ex: Throwable) {
//        if (message.trim() != ex.message?.trim()) {
//            throw IllegalStateException(message, ex)
//        }
//
//        assertTrue(false, message)
//    }
}
