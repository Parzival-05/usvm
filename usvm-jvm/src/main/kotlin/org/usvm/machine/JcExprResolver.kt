package org.usvm.machine

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import io.ksmt.utils.mkFreshConst
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeVariable
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.cfg.JcAddExpr
import org.jacodb.api.cfg.JcAndExpr
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcBinaryExpr
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcByte
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcChar
import org.jacodb.api.cfg.JcClassConstant
import org.jacodb.api.cfg.JcCmpExpr
import org.jacodb.api.cfg.JcCmpgExpr
import org.jacodb.api.cfg.JcCmplExpr
import org.jacodb.api.cfg.JcDivExpr
import org.jacodb.api.cfg.JcDouble
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcExprVisitor
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcFloat
import org.jacodb.api.cfg.JcGeExpr
import org.jacodb.api.cfg.JcGtExpr
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcInstanceOfExpr
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcLambdaExpr
import org.jacodb.api.cfg.JcLeExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcLong
import org.jacodb.api.cfg.JcLtExpr
import org.jacodb.api.cfg.JcMethodConstant
import org.jacodb.api.cfg.JcMethodType
import org.jacodb.api.cfg.JcMulExpr
import org.jacodb.api.cfg.JcNegExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcOrExpr
import org.jacodb.api.cfg.JcPhiExpr
import org.jacodb.api.cfg.JcRemExpr
import org.jacodb.api.cfg.JcShlExpr
import org.jacodb.api.cfg.JcShort
import org.jacodb.api.cfg.JcShrExpr
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcSubExpr
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcUshrExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.cfg.JcXorExpr
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.enumValues
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.float
import org.jacodb.api.ext.ifArrayGetElementType
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.cfg.util.isPrimitive
import org.jacodb.impl.types.FieldInfo
import org.usvm.UArrayIndexLValue
import org.usvm.UArrayLengthLValue
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFieldLValue
import org.usvm.UHeapReading
import org.usvm.UHeapRef
import org.usvm.UInputArrayReading
import org.usvm.UInputFieldReading
import org.usvm.ULValue
import org.usvm.URegisterLValue
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.isStaticInitializedConcreteHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.isTrue
import org.usvm.machine.operator.JcBinaryOperator
import org.usvm.machine.operator.JcUnaryOperator
import org.usvm.machine.operator.ensureBvExpr
import org.usvm.machine.operator.mkNarrow
import org.usvm.machine.operator.wideTo32BitsIfNeeded
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.classType
import org.usvm.machine.state.classTypeSyntheticField
import org.usvm.machine.state.stringType
import org.usvm.machine.state.stringValueField
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.memory.URegionHeap
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.util.extractJcRefType

/**
 * An expression resolver based on JacoDb 3-address code. A result of resolving is `null`, iff
 * the original state is dead, as stated in [JcStepScope].
 */
class JcExprResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    private val applicationGraph: JcApplicationGraph,
    private val localToIdx: (JcMethod, JcLocal) -> Int,
    private val mkTypeRef: (JcType, JcState) -> UConcreteHeapRef,
    private val mkStringConstRef: (String, JcState) -> UConcreteHeapRef,
    private val hardMaxArrayLength: Int = 1_500, // TODO: move to options
) : JcExprVisitor<UExpr<out USort>?> {
    /**
     * Resolves the [expr] and casts it to match the desired [type].
     *
     * @return a symbolic expression, with the sort corresponding to the [type].
     */
    @Suppress("UNCHECKED_CAST")
    fun resolveJcExpr(expr: JcExpr, type: JcType = expr.type): UExpr<out USort>? {
        val resolvedExpr = if (expr.type != type) {
            resolveCast(expr, type)
        } else {
            expr.accept(this)
        } ?: return null

        if ((type as? JcClassType)?.jcClass?.isEnum == true && (resolvedExpr.sort == ctx.addressSort)) {
            return ensureStaticFieldsInitialized(type, resolvedExpr as UHeapRef) {
                resolvedExpr
            }
        }

        return resolvedExpr
    }

    /**
     * Builds a [ULValue] from a [value].
     *
     * @return `null` if the symbolic state is dead, as stated in the [JcStepScope] documentation.
     *
     * @see JcStepScope
     */
    fun resolveLValue(value: JcValue): ULValue? =
        when (value) {
            is JcFieldRef -> resolveFieldRef(value.instance, value.field)
            is JcArrayAccess -> resolveArrayAccess(value.array, value.index)
            is JcLocal -> resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    override fun visitExternalJcExpr(expr: JcExpr): UExpr<out USort> = with(ctx) {
        error("Unexpected expression: $expr")
    }

    // region binary operators

    override fun visitJcAddExpr(expr: JcAddExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Add, expr)

    override fun visitJcSubExpr(expr: JcSubExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Sub, expr)

    override fun visitJcMulExpr(expr: JcMulExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Mul, expr)

    override fun visitJcDivExpr(expr: JcDivExpr): UExpr<out USort>? =
        resolveDivisionOperator(JcBinaryOperator.Div, expr)

    override fun visitJcRemExpr(expr: JcRemExpr): UExpr<out USort>? =
        resolveDivisionOperator(JcBinaryOperator.Rem, expr)

    override fun visitJcShlExpr(expr: JcShlExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Shl, expr)

    override fun visitJcShrExpr(expr: JcShrExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Shr, expr)

    override fun visitJcUshrExpr(expr: JcUshrExpr): UExpr<out USort>? =
        resolveShiftOperator(JcBinaryOperator.Ushr, expr)

    override fun visitJcOrExpr(expr: JcOrExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Or, expr)

    override fun visitJcAndExpr(expr: JcAndExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.And, expr)

    override fun visitJcXorExpr(expr: JcXorExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Xor, expr)

    override fun visitJcEqExpr(expr: JcEqExpr): UExpr<out USort>? = with(ctx) {
        if (expr.lhv.type is JcRefType) {
            resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort))
            }
        } else {
            resolveBinaryOperator(JcBinaryOperator.Eq, expr)
        }
    }

    override fun visitJcNeqExpr(expr: JcNeqExpr): UExpr<out USort>? = with(ctx) {
        if (expr.lhv.type is JcRefType) {
            resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
                mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort)).not()
            }
        } else {
            resolveBinaryOperator(JcBinaryOperator.Neq, expr)
        }
    }

    override fun visitJcGeExpr(expr: JcGeExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Ge, expr)

    override fun visitJcGtExpr(expr: JcGtExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Gt, expr)

    override fun visitJcLeExpr(expr: JcLeExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Le, expr)

    override fun visitJcLtExpr(expr: JcLtExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Lt, expr)

    override fun visitJcCmpExpr(expr: JcCmpExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmp, expr)

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmpg, expr)

    override fun visitJcCmplExpr(expr: JcCmplExpr): UExpr<out USort>? =
        resolveBinaryOperator(JcBinaryOperator.Cmpl, expr)

    override fun visitJcNegExpr(expr: JcNegExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.operand) { operand ->
            val wideOperand = if (operand.sort != operand.ctx.boolSort) {
                operand wideWith expr.operand.type
            } else {
                operand
            }
            JcUnaryOperator.Neg(wideOperand)
        }

    // endregion

    // region constants

    override fun visitJcBool(value: JcBool): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visitJcChar(value: JcChar): UExpr<out USort> = with(ctx) {
        mkBv(value.value.code, charSort)
    }

    override fun visitJcByte(value: JcByte): UExpr<out USort> = with(ctx) {
        mkBv(value.value, byteSort)
    }

    override fun visitJcShort(value: JcShort): UExpr<out USort> = with(ctx) {
        mkBv(value.value, shortSort)
    }

    override fun visitJcInt(value: JcInt): UExpr<out USort> = with(ctx) {
        mkBv(value.value, integerSort)
    }

    override fun visitJcLong(value: JcLong): UExpr<out USort> = with(ctx) {
        mkBv(value.value, longSort)
    }

    override fun visitJcFloat(value: JcFloat): UExpr<out USort> = with(ctx) {
        mkFp(value.value, floatSort)
    }

    override fun visitJcDouble(value: JcDouble): UExpr<out USort> = with(ctx) {
        mkFp(value.value, doubleSort)
    }

    override fun visitJcNullConstant(value: JcNullConstant): UExpr<out USort> = with(ctx) {
        nullRef
    }

    override fun visitJcStringConstant(value: JcStringConstant): UExpr<out USort>? = with(ctx) {
        scope.calcOnState {
            val stringValueField = stringValueField()

            // String.value type depends on the JVM version
            val charValues = when (stringValueField.fieldType.ifArrayGetElementType) {
                cp.char -> value.value.asSequence().map { mkBv(it.code, charSort) }
                cp.byte -> value.value.encodeToByteArray().asSequence().map { mkBv(it, byteSort) }
                else -> error("Unexpected string values type: ${stringValueField.fieldType}")
            }

            val valuesArrayDescriptor = arrayDescriptorOf(stringValueField.fieldType as JcArrayType)
            val elementType = requireNotNull(stringValueField.fieldType.ifArrayGetElementType)
            val charArrayRef = memory.malloc(valuesArrayDescriptor, typeToSort(elementType), charValues)

            // overwrite array type because descriptor is element type
            memory.types.allocate(charArrayRef, stringValueField.fieldType)

            // Equal string constants always have equal references
            val ref = mkStringConstRef(value.value, this)

            // String constants are immutable. Therefore, it is correct to overwrite value and type.
            val stringValueLValue = UFieldLValue(addressSort, ref, stringValueField.field)
            memory.write(stringValueLValue, charArrayRef)
            memory.types.allocate(ref, stringType())

            ref
        }
    }

    override fun visitJcMethodConstant(value: JcMethodConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcMethodType(value: JcMethodType): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    private fun JcState.resolveClassRef(type: JcType): UConcreteHeapRef {
        val ref = mkTypeRef(type, this)

        // Ref type is java.lang.Class
        val classType = ctx.classType()
        memory.types.allocate(ref, classType)

        // Save ref original class type
        val classRefType = memory.alloc(type)
        val classRefTypeLValue = UFieldLValue(ctx.addressSort, ref, ctx.classTypeSyntheticField())
        memory.write(classRefTypeLValue, classRefType)

        return ref
    }

    override fun visitJcClassConstant(value: JcClassConstant): UExpr<out USort>? = with(ctx) {
        scope.calcOnState {
            resolveClassRef(value.klass)
        }
    }

    // endregion

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort>? = resolveCast(expr.operand, expr.type)

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.operand)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            val notEqualsNull = mkHeapRefEq(ref, memory.heap.nullRef()).not()
            val isExpr = memory.types.evalIsSubtype(ref, expr.targetType)
            mkAnd(notEqualsNull, isExpr)
        }
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort>? = with(ctx) {
        val ref = resolveJcExpr(expr.array)?.asExpr(addressSort) ?: return null
        checkNullPointer(ref) ?: return null
        val arrayDescriptor = arrayDescriptorOf(expr.array.type as JcArrayType)
        val lengthRef = UArrayLengthLValue(ref, arrayDescriptor)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) } ?: return null
        assertHardMaxArrayLength(length) ?: return null
        scope.assert(mkBvSignedLessOrEqualExpr(mkBv(0), length)) ?: return null
        length
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val size = resolveCast(expr.dimensions[0], ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        // TODO: other dimensions ( > 1)
        checkNewArrayLength(size) ?: return null
        val ref = scope.calcOnState {
            val r = memory.malloc(arrayDescriptorOf(expr.type as JcArrayType), size, callStack.none { it.method.name == "<clinit>" })
            memory.types.allocate(r, expr.type)
            r
        } ?: return null
        ref
    }

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort>? =
        ensureStaticFieldsInitialized(expr.type as JcRefType, instance = null) {
            scope.calcOnState {
                memory.alloc(expr.type, callStack.none { it.method.name == "<clinit>" })
            }
        }

    override fun visitJcPhiExpr(expr: JcPhiExpr): UExpr<out USort> =
        error("Unexpected expr: $expr")

    // region invokes

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): UExpr<out USort>? =
        resolveInstanceCallExpr(expr)

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort>? =
        resolveInstanceCallExpr(expr) // TODO resolve actual method for interface invokes

    private fun resolveInstanceCallExpr(expr: JcInstanceCallExpr): UExpr<out USort>? {
        val instance = resolveJcExpr(expr.instance)?.asExpr(ctx.addressSort)

        return approximateClassNativeCalls(expr) {
            resolveInvoke(expr.method, instance, expr.instance.type as? JcRefType) {
                instance ?: return@resolveInvoke null
                checkNullPointer(instance) ?: return@resolveInvoke null
                val arguments = mutableListOf<UExpr<out USort>>(instance)

                val argsWithTypes = expr.args.zip(expr.method.parameters.map { it.type })

                argsWithTypes.mapTo(arguments) { (expr, type) ->
                    resolveJcExpr(expr, type) ?: return@resolveInvoke null
                }
                arguments
            }
        }
    }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort>? =
        approximateClassNativeCalls(expr) {
            resolveInvoke(expr.method) {
                val argsWithTypes = expr.args.zip(expr.method.parameters.map { it.type })

                argsWithTypes.map { (expr, type) ->
                    resolveJcExpr(expr, type) ?: return@resolveInvoke null
                }
            }
        }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort>? =
        resolveInvoke(expr.method) {
            val argsWithTypes = expr.args.zip(expr.method.parameters.map { it.type })

            argsWithTypes.map { (expr, type) ->
                resolveJcExpr(expr, type) ?: return@resolveInvoke null
            }
        }

    private fun resolveInvoke(
        method: JcTypedMethod,
        instance: UHeapRef? = null,
        instanceType: JcRefType? = null,
        resolveArguments: () -> List<UExpr<out USort>>?,
    ): UExpr<out USort>? = ensureStaticFieldsInitialized(instanceType ?: method.enclosingType, instance) {
        resolveInvokeNoStaticInitializationCheck(method, resolveArguments)
    }

    private fun resolveInvokeNoStaticInitializationCheck(
        method: JcTypedMethod,
        resolveArguments: () -> List<UExpr<out USort>>?,
    ): UExpr<out USort>? {
        val result = scope.calcOnState { methodResult } ?: return null
        return when (result) {
            is JcMethodResult.Success -> {
                check(result.method == method.method) {
                    "Expected result from ${method.method} but actual is ${result.method}"
                }
                scope.doWithState { methodResult = JcMethodResult.NoCall }
                result.value
            }

            is JcMethodResult.NoCall -> {
                val arguments = resolveArguments() ?: return null
                scope.doWithState { addNewMethodCall(applicationGraph, method.method, arguments) }
                null
            }

            is JcMethodResult.JcException -> error("Exception should be handled earlier")
        }
    }

    // endregion

    // region jc locals

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcThis(value: JcThis): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitJcArgument(value: JcArgument): UExpr<out USort>? = with(ctx) {
        val ref = resolveLocal(value)
        scope.calcOnState { memory.read(ref) }
    }

    // endregion

    // region jc complex values

    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort>? {
        val ref = resolveFieldRef(value.instance, value.field) ?: return null
        return scope.calcOnState { memory.read(ref) }
    }


    override fun visitJcArrayAccess(value: JcArrayAccess): UExpr<out USort>? {
        val ref = resolveArrayAccess(value.array, value.index) ?: return null
        return scope.calcOnState { memory.read(ref) }
    }

    // endregion

    // region lvalue resolving

    private fun resolveFieldRef(instance: JcValue?, field: JcTypedField): ULValue? {
        with(ctx) {
            val instanceRef = instance?.let { resolveJcExpr(it)?.asExpr(addressSort) }

            return ensureStaticFieldsInitialized(field.enclosingType, instanceRef) {
                if (instance != null) {
                    if (instanceRef == null) {
                        return null
                    }

                    checkNullPointer(instanceRef) ?: return null
                    val sort = ctx.typeToSort(field.fieldType)
                    UFieldLValue(sort, instanceRef, field.field)
                } else {
                    val sort = ctx.typeToSort(field.fieldType)
                    val classRef = scope.calcOnState {
                        resolveClassRef(field.enclosingType)
                    } ?: return null
                    UFieldLValue(sort, classRef, field.field)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "NAME_SHADOWING")
    private fun ensureEnumInstanceCorrectness(
        enumInstance: UHeapRef?,
        type: JcClassOrInterface,
    ) {
        if (enumInstance == null || !type.isEnum) {
            return
        }

        if (enumInstance !is USymbolicHeapRef) {
            return
        }



        /*if (callStack.any {
            val method = it.method

                method.enclosingClass == type && method.name == "<clinit>"
        }) {
            return@calcOnState
        }

        // TODO try to extract it using jacodb
        val enumInstanceNames = Class.forName(type.name).enumConstants.map { "${type.name}#${(it as Enum<*>).name}" }
        val enumInstanceConcreteRefs = (memory.heap as URegionHeap<*, *>).allocatedFields().filter {
            enumInstanceNames.any { name -> name in it.key.second.toString() }
        }.values

        with(ctx) {
            val oneOfInstance = enumInstanceConcreteRefs.map { mkHeapRefEq(enumInstance, it as UConcreteHeapRef) }

            mkOr(oneOfInstance)
        }*/
        scope.calcOnState {
            if (callStack.any {
                    val method = it.method

                    method.enclosingClass == type && method.name == "<clinit>"
                }) {
                return@calcOnState
            }

            val enumValues = type.enumValues

            with(ctx) {
                val maxOrdinalValue = enumValues!!.size.toBv()
                val ordinalField =
                    (cp.findTypeOrNull("java.lang.Enum") as JcRefType).jcClass.findFieldOrNull("ordinal")!!
                val ordinalFieldLValue = UFieldLValue(sizeSort, enumInstance, ordinalField)
                val ordinalFieldValue = memory.read(ordinalFieldLValue).asExpr(sizeSort)

                val ordinalCorrectnessConstraints =
                    mkBvSignedLessOrEqualExpr(mkBv(0), ordinalFieldValue) and mkBvSignedLessExpr(
                        ordinalFieldValue,
                        maxOrdinalValue
                    )

                val enumValuesField = type.findFieldOrNull("\$VALUES")!!
                val enumClassRef = resolveClassRef(type.toType())
                val enumValuesFieldLValue = UFieldLValue(addressSort, enumClassRef, enumValuesField)
                val enumValuesRef = memory.read(enumValuesFieldLValue) as UHeapRef


                val oneOfEnumInstancesLValue =
                    UArrayIndexLValue(addressSort, enumValuesRef, ordinalFieldValue, ctx.cp.objectType)
                val oneOfEnumInstancesRef = memory.read(oneOfEnumInstancesLValue) as UHeapRef
                val oneOfEnumInstancesEqualityConstraint = mkHeapRefEq(enumInstance, oneOfEnumInstancesRef)

                val oneOfEnumInstances = ordinalCorrectnessConstraints and oneOfEnumInstancesEqualityConstraint
                val isEnumNull = mkHeapRefEq(enumInstance, nullRef)
                val constraint = mkIteNoSimplify(isEnumNull, trueExpr, oneOfEnumInstances)
                scope.assert(constraint)


                enumValues.indices.forEach { ordinal ->
                    // {0x1 <- 1}{0x2 <- 2}ordinal
                    // read(ordinal, 0x1) = 1
                    // read(ordinal, 0x2) = 2
                    val enumConstantLValue =
                        UArrayIndexLValue(ctx.addressSort, enumValuesRef, ctx.mkSizeExpr(ordinal), ctx.cp.objectType)
                    val enumConstantRef = memory.read(enumConstantLValue) as UHeapRef

                    val ordinalFieldLValue =
                        UFieldLValue(ctx.sizeSort, enumConstantRef, ordinalField)
                    val ordinalFieldValueAfterClinit = memory.read(ordinalFieldLValue).asExpr(ctx.sizeSort)

                    val unexistedOrdinalLValue = UFieldLValue(ctx.sizeSort, ctx.mkConcreteHeapRef(Int.MIN_VALUE), ordinalField)
                    val ordinalEmptyRegion = (memory.read(unexistedOrdinalLValue) as UInputFieldReading<*, *>).region
                    val ordinalFieldValueBeforeClinit = ordinalEmptyRegion.read(enumConstantRef).asExpr(ctx.sizeSort)

                    scope.assert(ctx.mkEq(ordinalFieldValueAfterClinit, ordinalFieldValueBeforeClinit))
                }
            }
        }
    }

    /**
     * Run a class static initializer for [type] if it didn't run before the current state.
     * The class static initialization state is tracked by the synthetic [staticFieldsInitializedFlagField] field.
     * */
    private inline fun <T> ensureStaticFieldsInitialized(type: JcRefType, instance: UHeapRef?, body: () -> T): T? {
        // java.lang.Object has no static fields, but has non-trivial initializer
        if (type == ctx.cp.objectType) {
            return body()
        }

        val initializer = type.jcClass.declaredMethods.firstOrNull { it.isClassInitializer }

        // Class has no static initializer
        if (initializer == null) {
            return body()
        }

        val classRef = scope.calcOnState { resolveClassRef(type) } ?: return null

        val initializedFlag = staticFieldsInitializedFlag(type, classRef)

        val staticFieldsInitialized = scope.calcOnState {
            memory.read(initializedFlag).asExpr(ctx.booleanSort)
        } ?: return null


        if (staticFieldsInitialized.isTrue) {
            scope.doWithState {
                // Handle static initializer result
                val result = methodResult
                if (result is JcMethodResult.Success && result.method == initializer) {
                    methodResult = JcMethodResult.NoCall
                }
            }

            ensureEnumInstanceCorrectness(instance, type.jcClass)

            return body()
        }

        // Run static initializer before the current statement
        scope.doWithState {
            memory.write(initializedFlag, ctx.trueExpr)
            addNewMethodCall(applicationGraph, initializer, emptyList())
        }
        return null
    }

    private fun staticFieldsInitializedFlag(type: JcRefType, classRef: UHeapRef) =
        UFieldLValue(
            fieldSort = ctx.booleanSort,
            field = JcFieldImpl(type.jcClass, staticFieldsInitializedFlagField),
            ref = classRef
        )

    private fun resolveArrayAccess(array: JcValue, index: JcValue): UArrayIndexLValue<JcType>? = with(ctx) {
        val arrayRef = resolveJcExpr(array)?.asExpr(addressSort) ?: return null
        checkNullPointer(arrayRef) ?: return null

        val arrayDescriptor = arrayDescriptorOf(array.type as JcArrayType)

        val idx = resolveCast(index, ctx.cp.int)?.asExpr(bv32Sort) ?: return null
        val lengthRef = UArrayLengthLValue(arrayRef, arrayDescriptor)
        val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) } ?: return null

        assertHardMaxArrayLength(length) ?: return null

        checkArrayIndex(idx, length) ?: return null

        val elementType = requireNotNull(array.type.ifArrayGetElementType)
        val cellSort = typeToSort(elementType)

        return UArrayIndexLValue(cellSort, arrayRef, idx, arrayDescriptor)
    }

    private fun resolveLocal(local: JcLocal): URegisterLValue {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterLValue(sort, localIdx)
    }

    // endregion

    // region implicit exceptions

    private fun allocateException(type: JcRefType): (JcState) -> Unit = { state ->
        val address = state.memory.alloc(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun checkArrayIndex(idx: USizeExpr, length: USizeExpr) = with(ctx) {
        val inside = (mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and (mkBvSignedLessExpr(idx, length))

        scope.fork(
            inside,
            blockOnFalseState = allocateException(arrayIndexOutOfBoundsExceptionType)
        )
    }

    private fun checkNewArrayLength(length: UExpr<USizeSort>) = with(ctx) {
        assertHardMaxArrayLength(length) ?: return null

        val lengthIsNonNegative = mkBvSignedLessOrEqualExpr(mkBv(0), length)

        scope.fork(
            lengthIsNonNegative,
            blockOnFalseState = allocateException(negativeArraySizeExceptionType)
        )
    }

    private fun checkDivisionByZero(expr: UExpr<out USort>) = with(ctx) {
        val sort = expr.sort
        if (sort !is UBvSort) {
            return Unit
        }
        val neqZero = mkEq(expr.cast(), mkBv(0, sort)).not()
        scope.fork(
            neqZero,
            blockOnFalseState = allocateException(arithmeticExceptionType)
        )
    }

    private fun checkNullPointer(ref: UHeapRef) = with(ctx) {
        val neqNull = mkHeapRefEq(ref, nullRef).not()
        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(nullPointerExceptionType)
        )
    }

    // endregion

    // region hard assertions

    private fun assertHardMaxArrayLength(length: USizeExpr): Unit? = with(ctx) {
        val lengthLeThanMaxLength = mkBvSignedLessOrEqualExpr(length, mkBv(hardMaxArrayLength))
        scope.assert(lengthLeThanMaxLength) ?: return null
        return Unit
    }

    // endregion

    private fun resolveBinaryOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        // we don't want to have extra casts on booleans
        if (lhs.sort == ctx.boolSort && rhs.sort == ctx.boolSort) {
            resolvePrimitiveCast(operator(lhs, rhs), ctx.cp.boolean, expr.type as JcPrimitiveType)
        } else {
            operator(lhs wideWith expr.lhv.type, rhs wideWith expr.rhv.type)
        }
    }

    private fun resolveShiftOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        val wideLhs = lhs wideWith expr.lhv.type
        val preWideRhs = rhs wideWith expr.rhv.type

        val lhsSize = (wideLhs.sort as UBvSort).sizeBits
        val rhsSize = (preWideRhs.sort as UBvSort).sizeBits

        val wideRhs = if (lhsSize == rhsSize) {
            preWideRhs
        } else {
            check(lhsSize == Long.SIZE_BITS.toUInt() && rhsSize == Int.SIZE_BITS.toUInt()) {
                "Unexpected shift arguments: $lhs, $rhs"
            }
            // Wide rhs up to 64 bits to match lhs sort
            preWideRhs.ensureBvExpr().mkNarrow(Long.SIZE_BITS, signed = true)
        }

        operator(wideLhs, wideRhs)
    }

    private fun resolveDivisionOperator(
        operator: JcBinaryOperator,
        expr: JcBinaryExpr,
    ) = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        checkDivisionByZero(rhs) ?: return null
        operator(lhs wideWith expr.lhv.type, rhs wideWith expr.rhv.type)
    }

    private fun resolveCast(
        operand: JcExpr,
        type: JcType,
    ) = resolveAfterResolved(operand) { expr ->
        when (type) {
            is JcRefType -> resolveReferenceCast(expr, operand.type as JcRefType, type)
            is JcPrimitiveType -> resolvePrimitiveCast(expr, operand.type as JcPrimitiveType, type)
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveReferenceCast(
        expr: UExpr<out USort>,
        typeBefore: JcRefType,
        type: JcRefType,
    ): UExpr<out USort>? {
        @Suppress("NAME_SHADOWING") val type = if (type is JcTypeVariable) typeBefore else type

        return if (!typeBefore.isAssignable(type)) {
            val isExpr = scope.calcOnState { memory.types.evalIsSubtype(expr.asExpr(ctx.addressSort), type) }
                ?: return null
            scope.fork(
                isExpr,
                blockOnFalseState = allocateException(classCastExceptionType)
            ) ?: return null
            expr
        } else {
            expr
        }
    }

    private fun resolvePrimitiveCast(
        expr: UExpr<out USort>,
        typeBefore: JcPrimitiveType,
        type: JcPrimitiveType,
    ): UExpr<out USort> {
        // we need this, because char is unsigned, so it should be widened before a cast
        val wideExpr = if (typeBefore == ctx.cp.char) {
            expr wideWith typeBefore
        } else {
            expr
        }

        return when (type) {
            ctx.cp.boolean -> JcUnaryOperator.CastToBoolean(wideExpr)
            ctx.cp.short -> JcUnaryOperator.CastToShort(wideExpr)
            ctx.cp.int -> JcUnaryOperator.CastToInt(wideExpr)
            ctx.cp.long -> JcUnaryOperator.CastToLong(wideExpr)
            ctx.cp.float -> JcUnaryOperator.CastToFloat(wideExpr)
            ctx.cp.double -> JcUnaryOperator.CastToDouble(wideExpr)
            ctx.cp.byte -> JcUnaryOperator.CastToByte(wideExpr)
            ctx.cp.char -> JcUnaryOperator.CastToChar(wideExpr)
            else -> error("Unexpected cast expression: ($type) $expr of $typeBefore")
        }
    }

    private infix fun UExpr<out USort>.wideWith(
        type: JcType,
    ): UExpr<out USort> {
        require(type is JcPrimitiveType)
        return wideTo32BitsIfNeeded(type.isSigned)
    }

    private val JcPrimitiveType.isSigned
        get() = this != classpath.char

    private fun <T> resolveAfterResolved(
        dependency: JcExpr,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolveJcExpr(dependency) ?: return null
        return block(result)
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: JcExpr,
        dependency1: JcExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolveJcExpr(dependency0) ?: return null
        val result1 = resolveJcExpr(dependency1) ?: return null
        return block(result0, result1)
    }

    private val arrayIndexOutOfBoundsExceptionType by lazy {
        ctx.extractJcRefType(IndexOutOfBoundsException::class)
    }

    private val negativeArraySizeExceptionType by lazy {
        ctx.extractJcRefType(NegativeArraySizeException::class)
    }

    private val arithmeticExceptionType by lazy {
        ctx.extractJcRefType(ArithmeticException::class)
    }

    private val nullPointerExceptionType by lazy {
        ctx.extractJcRefType(NullPointerException::class)
    }

    private val classCastExceptionType by lazy {
        ctx.extractJcRefType(ClassCastException::class)
    }

    /**
     * TODO: use approximations.
     *
     * Approximate java.lang.Class methods that usually appears in static initializers.
     * */
    private inline fun approximateClassNativeCalls(
        expr: JcCallExpr,
        body: () -> UExpr<out USort>?
    ): UExpr<out USort>? {
        val method = expr.method.method

        // The special case for cloning arrays
        if (method.name == "clone" && expr is JcInstanceCallExpr && expr.instance.type is JcArrayType) {
            val instance = resolveJcExpr(expr.instance)?.asExpr(ctx.addressSort) ?: return null

            return scope.calcOnState {
                with(ctx) {
                    val arrayType = expr.instance.type as JcArrayType
                    val arrayDescriptor = arrayDescriptorOf(arrayType)
                    val elementType = requireNotNull(arrayType.ifArrayGetElementType)

                    val lengthRef = UArrayLengthLValue(instance, arrayDescriptor)
                    val length = scope.calcOnState { memory.read(lengthRef).asExpr(sizeSort) } ?: return@calcOnState null

                    val arrayRef = memory.malloc(arrayDescriptor, length, callStack.none { it.method.name == "<clinit>" })

                    memory.types.allocate(arrayRef, arrayType)
                    memory.memcpy(
                        src = instance,
                        dst = arrayRef,
                        arrayType,
                        elementSort = typeToSort(elementType),
                        fromSrc = mkBv(0),
                        fromDst = mkBv(0),
                        length
                    )

                    arrayRef
                }
            }
        }

        if (method.enclosingClass.name != "java.lang.Class") {
            return body()
        }

        return when (method.name) {
            /**
             * Approximate assertions enabled check.
             * It is correct to enable assertions during analysis.
             * */
            "desiredAssertionStatus" -> {
                ctx.trueExpr
            }

            /**
             * Approximate retrieval of class instance for primitives.
             * */
            "getPrimitiveClass" -> {
                val primitiveNameConst = expr.operands.singleOrNull() as? JcStringConstant ?: return body()
                val primitive = PredefinedPrimitives.of(primitiveNameConst.value, ctx.cp) ?: return body()
                scope.calcOnState {
                    resolveClassRef(primitive)
                }
            }

            else -> body()
        }
    }

    fun mutatePrimitiveFieldValuesToSymbolic(scope: JcStepScope) {
        scope.calcOnState {
            with(ctx) {
                val allocatedMutablePrimitiveStaticFields =
                    (memory.heap as URegionHeap<JcField, JcType>).allocatedFields().filterKeys {
                        val field = it.second
                        field.type.isPrimitive && field.isStatic && !field.isFinal
                    }.keys.map { it.second }

                val symbolicValues = allocatedMutablePrimitiveStaticFields.associateWith {
                    typeToSort(cp.findTypeOrNull(it.type)!!).mkFreshConst(it.name)
                }

                symbolicValues.forEach { (field, rvalue) ->
                    val classRef = resolveClassRef(cp.findTypeOrNull(field.type)!!)
                    val lvalue = UFieldLValue(rvalue.sort, classRef, field)

                    memory.write(lvalue, rvalue)
                }
            }
        }
    }

    companion object {
        /**
         * Synthetic field to track static field initialization state.
         * */
        private val staticFieldsInitializedFlagField by lazy {
            FieldInfo(
                name = "__initialized__",
                signature = null,
                access = 0,
                type = PredefinedPrimitives.Boolean,
                annotations = emptyList()
            )
        }
    }
}
