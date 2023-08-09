@file:Suppress("UNCHECKED_CAST")
package org.usvm.instrumentation.testcase.executor

import ReflectionUtils
import getFieldValue
import invokeWithAccessibility
import newInstanceWithAccessibility
import org.jacodb.api.JcField
import org.jacodb.api.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.collector.trace.MockCollector.MockValueArrayWrapper
import org.usvm.instrumentation.util.*
import setFieldValue
import java.lang.ClassCastException
import java.lang.IllegalArgumentException

class UTestExpressionExecutor(
    private val workerClassLoader: WorkerClassLoader,
    private val accessedStatics: MutableSet<Pair<JcField, StaticFieldAccessType>>,
    private val mockHelper: MockHelper
) {

    private val jcClasspath = workerClassLoader.jcClasspath


    private val executedUTestExpressions: MutableMap<UTestExpression, Any?> = hashMapOf()

    fun removeFromCache(uTestExpression: UTestExpression) = executedUTestExpressions.remove(uTestExpression)

    fun clearCache() = executedUTestExpressions.clear()

    fun executeUTestExpression(uTestExpression: UTestExpression): Result<Any?> =
        try {
            MockCollector.inExecution = true
            Result.success(exec(uTestExpression))
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            MockCollector.inExecution = false
        }

    fun executeUTestExpressions(uTestExpressions: List<UTestExpression>): Result<Any?>? {
        var lastResult: Result<Any?>? = null
        for (uTestExpression in uTestExpressions) {
            lastResult = executeUTestExpression(uTestExpression)
            if (lastResult.isFailure) return lastResult
        }
        return lastResult
    }


    private fun exec(uTestExpression: UTestExpression) = executedUTestExpressions.getOrPut(uTestExpression) {
        when (uTestExpression) {
            is UTestConstExpression<*> -> executeUTestConstant(uTestExpression)
            is UTestArrayLengthExpression -> executeUTestArrayLengthExpression(uTestExpression)
            is UTestArrayGetExpression -> executeUTestArrayGetExpression(uTestExpression)
            is UTestArraySetStatement -> executeUTestArraySetStatement(uTestExpression)
            is UTestCreateArrayExpression -> executeUTestArrayCreateExpression(uTestExpression)
            is UTestAllocateMemoryCall -> executeUTestAllocateMemoryCall(uTestExpression)
            is UTestConstructorCall -> executeConstructorCall(uTestExpression)
            is UTestMethodCall -> executeMethodCall(uTestExpression)
            is UTestStaticMethodCall -> executeUTestStaticMethodCall(uTestExpression)
            is UTestCastExpression -> executeUTestCastExpression(uTestExpression)
            is UTestGetFieldExpression -> executeUTestGetFieldExpression(uTestExpression)
            is UTestGetStaticFieldExpression -> executeUTestGetStaticFieldExpression(uTestExpression)
            is UTestMock -> executeUTestMock(uTestExpression)
            is UTestConditionExpression -> executeUTestConditionExpression(uTestExpression)
            is UTestSetFieldStatement -> executeUTestSetFieldStatement(uTestExpression)
            is UTestSetStaticFieldStatement -> executeUTestSetStaticFieldStatement(uTestExpression)
            is UTestArithmeticExpression -> executeUTestArithmeticExpression(uTestExpression)
        }
    }


    private fun executeUTestConstant(uTestConstExpression: UTestConstExpression<*>): Any? = uTestConstExpression.value

    private fun executeUTestArrayLengthExpression(uTestArrayLengthExpression: UTestArrayLengthExpression): Any? {
        val arrayInstance = exec(uTestArrayLengthExpression.arrayInstance) ?: return null
        return when (arrayInstance) {
            is BooleanArray -> arrayInstance.size
            is ByteArray -> arrayInstance.size
            is ShortArray -> arrayInstance.size
            is IntArray -> arrayInstance.size
            is LongArray -> arrayInstance.size
            is FloatArray -> arrayInstance.size
            is DoubleArray -> arrayInstance.size
            is CharArray -> arrayInstance.size
            else -> (arrayInstance as Array<*>).size
        }
    }

    private fun executeUTestArrayGetExpression(uTestArrayGetExpression: UTestArrayGetExpression): Any? {
        val arrayInstance = exec(uTestArrayGetExpression.arrayInstance)
        val index = exec(uTestArrayGetExpression.index) as Int
        return when (uTestArrayGetExpression.type) {
            jcClasspath.boolean -> (arrayInstance as BooleanArray)[index]
            jcClasspath.byte -> (arrayInstance as ByteArray)[index]
            jcClasspath.short -> (arrayInstance as ShortArray)[index]
            jcClasspath.int -> (arrayInstance as IntArray)[index]
            jcClasspath.long -> (arrayInstance as LongArray)[index]
            jcClasspath.double -> (arrayInstance as DoubleArray)[index]
            jcClasspath.float -> (arrayInstance as FloatArray)[index]
            jcClasspath.char -> (arrayInstance as CharArray)[index]
            else -> (arrayInstance as Array<*>)[index]
        }
    }

    private fun executeUTestArraySetStatement(uTestArraySetStatement: UTestArraySetStatement) {
        val arrayInstance = exec(uTestArraySetStatement.arrayInstance)
        val index = exec(uTestArraySetStatement.index) as Int
        val setValue = exec(uTestArraySetStatement.setValueExpression)

        when (uTestArraySetStatement.type) {
            jcClasspath.boolean -> (arrayInstance as BooleanArray).set(index, setValue as Boolean)
            jcClasspath.byte -> (arrayInstance as ByteArray).set(index, setValue as Byte)
            jcClasspath.short -> (arrayInstance as ShortArray).set(index, setValue as Short)
            jcClasspath.int -> (arrayInstance as IntArray).set(index, setValue as Int)
            jcClasspath.long -> (arrayInstance as LongArray).set(index, setValue as Long)
            jcClasspath.double -> (arrayInstance as DoubleArray).set(index, setValue as Double)
            jcClasspath.float -> (arrayInstance as FloatArray).set(index, setValue as Float)
            jcClasspath.char -> (arrayInstance as CharArray).set(index, setValue as Char)
            else -> (arrayInstance as Array<Any?>).set(index, setValue)
        }
    }


    private fun executeUTestArrayCreateExpression(uTestCreateArrayExpression: UTestCreateArrayExpression): Any {
        val size = exec(uTestCreateArrayExpression.size) as Int
        return when (uTestCreateArrayExpression.elementType) {
            jcClasspath.boolean -> BooleanArray(size)
            jcClasspath.byte -> ByteArray(size)
            jcClasspath.short -> ShortArray(size)
            jcClasspath.int -> IntArray(size)
            jcClasspath.long -> LongArray(size)
            jcClasspath.double -> DoubleArray(size)
            jcClasspath.float -> FloatArray(size)
            jcClasspath.char -> CharArray(size)
            else -> java.lang.reflect.Array.newInstance(
                uTestCreateArrayExpression.elementType.toJavaClass(
                    workerClassLoader
                ), size
            )
        }
    }

    private fun executeUTestAllocateMemoryCall(uTestAllocateMemoryCall: UTestAllocateMemoryCall): Any {
        val jClass = uTestAllocateMemoryCall.type.toJavaClass(workerClassLoader)
        return ReflectionUtils.UNSAFE.allocateInstance(jClass)
    }

    private fun executeUTestArithmeticExpression(uTestArithmeticExpression: UTestArithmeticExpression): Any? {
        val lhv = exec(uTestArithmeticExpression.lhv) ?: return null
        val rhv = exec(uTestArithmeticExpression.rhv) ?: return null
        if (lhv::class.java != rhv::class.java || lhv !is Number || rhv !is Number) {
            throw IllegalArgumentException("Wrong argument types for arithmetic operation")
        }
        val lhvAsDouble =
            if (lhv is Double || lhv is Float) {
                lhv.toDouble()
            } else {
                null
            }
        val rhvAsDouble = lhvAsDouble?.let { rhv.toDouble() }
        val lhvAsLong = lhv.toLong()
        val rhvAsLong = rhv.toLong()
        val res = when (uTestArithmeticExpression.operationType) {
            ArithmeticOperationType.PLUS -> lhvAsDouble?.let { it + rhvAsDouble!! } ?: (lhvAsLong + rhvAsLong)
            ArithmeticOperationType.SUB -> lhvAsDouble?.let { it - rhvAsDouble!! } ?: (lhvAsLong - rhvAsLong)
            ArithmeticOperationType.MUL -> lhvAsDouble?.let { it * rhvAsDouble!! } ?: (lhvAsLong * rhvAsLong)
            ArithmeticOperationType.DIV -> lhvAsDouble?.let { it / rhvAsDouble!! } ?: (lhvAsLong / rhvAsLong)
            ArithmeticOperationType.REM -> lhvAsDouble?.let { it % rhvAsDouble!! } ?: (lhvAsLong % rhvAsLong)
            ArithmeticOperationType.EQ -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.NEQ -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.GT -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.GEQ -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.LT -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.LTQ -> lhvAsDouble?.compareTo(rhvAsDouble!!) ?: (lhvAsLong.compareTo(rhvAsLong))
            ArithmeticOperationType.OR -> lhvAsDouble?.let { error("Bit operation on double impossible") } ?: (lhvAsLong or rhvAsLong)
            ArithmeticOperationType.AND -> lhvAsDouble?.let { error("Bit operation on double impossible") } ?: (lhvAsLong and rhvAsLong)
            ArithmeticOperationType.XOR -> lhvAsDouble?.let { error("Bit operation on double impossible") } ?: (lhvAsLong xor rhvAsLong)
        }
        return when (lhv::class) {
            Byte::class -> res.toByte()
            Short::class -> res.toShort()
            Int::class -> res.toInt()
            Long::class -> res
            Float::class -> res.toFloat()
            Double::class -> res.toDouble()
            else -> error("Wrong type for bit operation")
        }
    }

    private fun executeUTestMock(uTestMockObject: UTestMock): Any? {
        val jcType = uTestMockObject.type
        val jcClass = jcClasspath.findClass(jcType.typeName)
        val methodsToMock = uTestMockObject.methods.keys.toList()
        val isGlobalMock = uTestMockObject is UTestGlobalMock
        //Modify bytecode according to mocks
        val newClass = mockHelper.addMockInfoInBytecode(jcClass, methodsToMock, isGlobalMock)
        val mockInstance = ReflectionUtils.UNSAFE.allocateInstance(newClass)

        for ((jcMethod, mockUTestExpressions) in uTestMockObject.methods) {
            val methodId = mockHelper.mockCache[jcMethod] ?: error("Method should be mocked")
            val mockValuesArray = MockValueArrayWrapper(mockUTestExpressions.size)
            mockUTestExpressions.map { exec(it) }.forEach { mockValuesArray.add(it) }
            val instance = if (jcMethod.isStatic || isGlobalMock) null else mockInstance
            MockCollector.addMock(MockCollector.MockInfo(methodId, instance, mockValuesArray))
        }
        //Set mocked fields
        for ((jcField, jcFieldUTestExpression) in uTestMockObject.fields) {
            val jField = jcField.toJavaField(workerClassLoader) ?: error("Cant find field")
            val fieldValue = exec(jcFieldUTestExpression)
            jField.setFieldValue(mockInstance, fieldValue)
        }
        return mockInstance
    }

    private fun executeUTestSetFieldStatement(uTestSetFieldStatement: UTestSetFieldStatement) {
        val instance = exec(uTestSetFieldStatement.instance)
        val field = uTestSetFieldStatement.field.toJavaField(workerClassLoader)
        val fieldValue = exec(uTestSetFieldStatement.value)
        field?.setFieldValue(instance, fieldValue)
    }

    private fun executeUTestSetStaticFieldStatement(uTestSetFieldStatement: UTestSetStaticFieldStatement) {
        val field = uTestSetFieldStatement.field.toJavaField(workerClassLoader)
        val fieldValue = exec(uTestSetFieldStatement.value)
        accessedStatics.add(uTestSetFieldStatement.field to StaticFieldAccessType.SET)
        field?.setFieldValue(null, fieldValue)
    }

    private fun executeUTestConditionExpression(uTestConditionExpression: UTestConditionExpression): Any? {
        val lCond = exec(uTestConditionExpression.lhv)
        val rCond = exec(uTestConditionExpression.rhv)
        val res =
            when (uTestConditionExpression.conditionType) {
                ConditionType.EQ -> lCond == rCond
                ConditionType.NEQ -> lCond != rCond
                ConditionType.GEQ -> (lCond as Comparable<Any?>) >= rCond
                ConditionType.GT -> (lCond as Comparable<Any?>) > rCond
            }
        return if (res) {
            executeUTestExpressions(uTestConditionExpression.trueBranch)
        } else {
            executeUTestExpressions(uTestConditionExpression.elseBranch)
        }
    }

    private fun executeUTestGetFieldExpression(uTestGetFieldExpression: UTestGetFieldExpression): Any? {
        val instance = exec(uTestGetFieldExpression.instance)
        val jField = uTestGetFieldExpression.field.toJavaField(workerClassLoader)
        return jField?.getFieldValue(instance)
    }

    private fun executeUTestGetStaticFieldExpression(uTestGetStaticFieldExpression: UTestGetStaticFieldExpression): Any? {
        val jField = uTestGetStaticFieldExpression.field.toJavaField(workerClassLoader)
        accessedStatics.add(uTestGetStaticFieldExpression.field to StaticFieldAccessType.GET)
        return jField?.getFieldValue(null)
    }

    private fun executeUTestStaticMethodCall(uTestStaticMethodCall: UTestStaticMethodCall): Any? {
        val jMethod = uTestStaticMethodCall.method.toJavaMethod(workerClassLoader)
        val args = uTestStaticMethodCall.args.map { exec(it) }
        return jMethod.invokeWithAccessibility(null, args)
    }

    private fun executeUTestCastExpression(uTestCastExpression: UTestCastExpression): Any? {
        val castExpr = exec(uTestCastExpression.expr)
        val toTypeJClass = uTestCastExpression.type.toJavaClass(workerClassLoader)
        return try {
            toTypeJClass.cast(castExpr)
        } catch (e: ClassCastException) {
            throw TestExecutorException("Cant cast object of type ${uTestCastExpression.expr.type} to ${uTestCastExpression.type}")
        }
    }

    private fun executeConstructorCall(uConstructorCall: UTestConstructorCall): Any {
        val jConstructor = uConstructorCall.method.toJavaConstructor(workerClassLoader)
        val args = uConstructorCall.args.map { exec(it) }
        return jConstructor.newInstanceWithAccessibility(args)
    }

    private fun executeMethodCall(uMethodCall: UTestMethodCall): Any? {
        val instance = exec(uMethodCall.instance)
        val args = uMethodCall.args.map { exec(it) }
        val jMethod = uMethodCall.method.toJavaMethod(workerClassLoader)
        return jMethod.invokeWithAccessibility(instance, args)
    }

}

class TestExecutorException(msg: String) : Exception(msg)