package org.usvm.interpreter.operations

import io.ksmt.sort.KIntSort
import io.ksmt.sort.KSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructObject
import org.usvm.language.ConcretePythonType
import org.usvm.language.pythonBool
import org.usvm.language.pythonInt

fun <RES_SORT: KSort> createBinaryIntOp(
    resultConcreteType: ConcretePythonType,
    op: (UContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<RES_SORT>?
): (ConcolicRunContext, SymbolicPythonObject, SymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { concolicContext, left, right ->
    with (concolicContext.ctx) {
        if (left.concreteType != pythonInt || right.concreteType != pythonInt)
            null
        else {
            @Suppress("unchecked_cast")
            op(this, left.getIntContent(), right.getIntContent())?.let {
                constructObject(it, resultConcreteType, this, concolicContext.curState.memory)
            }
        }
    }
}

fun handlerGTLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left gt right } } (x, y, z)
fun handlerLTLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left lt right } } (x, y, z)
fun handlerEQLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left eq right } } (x, y, z)
fun handlerNELongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left neq right } } (x, y, z)
fun handlerGELongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left ge right } } (x, y, z)
fun handlerLELongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonBool) { ctx, left, right -> with(ctx) { left le right } } (x, y, z)
fun handlerADDLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonInt) { ctx, left, right -> val res = ctx.mkArithAdd(left, right); res } (x, y, z)
fun handlerSUBLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonInt) { ctx, left, right -> ctx.mkArithSub(left, right) } (x, y, z)
fun handlerMULLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonInt) { ctx, left, right -> ctx.mkArithMul(left, right) } (x, y, z)
fun handlerDIVLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonInt) { ctx, left, right -> ctx.mkArithDiv(left, right) } (x, y, z)
fun handlerREMLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp(pythonInt) { ctx, left, right -> ctx.mkIntMod(left, right) } (x, y, z)
@Suppress("unused_parameter")
fun handlerPOWLongKt(x: ConcolicRunContext, y: SymbolicPythonObject, z: SymbolicPythonObject): UninterpretedSymbolicPythonObject? = null  // TODO
    //createBinaryIntOp { ctx, left, right ->
    //    if (right is KIntNumExpr) ctx.mkArithPower(left, right) else null
    //} (x, y, z)