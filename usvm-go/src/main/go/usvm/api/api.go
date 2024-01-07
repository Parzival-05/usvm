package api

import "C"
import (
	"go/constant"
	"go/token"
	"strconv"

	"golang.org/x/tools/go/ssa"

	"usvm/graph"
	typeslocal "usvm/types"
	"usvm/util"
)

type Api interface {
	MkUnOp(inst *ssa.UnOp)
	MkBinOp(inst *ssa.BinOp)
	MkCall(inst *ssa.Call)
	MkCallBuiltin(inst *ssa.Call, name string)
	MkIf(inst *ssa.If)
	MkReturn(inst *ssa.Return)
	MkPhi(inst *ssa.Phi)
	MkPointerArrayReading(inst *ssa.IndexAddr)
	MkArrayReading(inst *ssa.Index)
	MkMapLookup(inst *ssa.Lookup)
	MkMapUpdate(inst *ssa.MapUpdate)

	SetLastBlock(block int)
	WriteLastBlock()

	Log(values ...any)
}

func SetProgram(program *ssa.Program) {
	apiInstance.program = program
}

func NewApi(lastBlock int, buf *util.ByteBuffer) Api {
	apiInstance.lastBlock = lastBlock
	apiInstance.buf = buf
	return apiInstance
}

var apiInstance = &api{}

type Method byte

const (
	_ Method = iota
	MethodMkUnOp
	MethodMkBinOp
	MethodMkCall
	MethodMkCallBuiltin
	MethodMkIf
	MethodMkReturn
	MethodMkVariable
	MethodMkPointerArrayReading
	MethodMkArrayReading
	MethodMkMapLookup
	MethodMkMapUpdate
)

type UnOp byte

const (
	_ UnOp = iota
	UnOpRecv
	UnOpNeg
	UnOpDeref
	UnOpNot
	UnOpInv
)

var unOpMapping = []UnOp{
	token.ARROW: UnOpRecv,
	token.SUB:   UnOpNeg,
	token.MUL:   UnOpDeref,
	token.NOT:   UnOpNot,
	token.XOR:   UnOpInv,
}

type BinOp byte

const (
	_ BinOp = iota
	BinOpAdd
	BinOpSub
	BinOpMul
	BinOpDiv
	BinOpMod
	BinOpAnd
	BinOpOr
	BinOpXor
	BinOpShl
	BinOpShr
	BinOpAndNot
	BinOpEq
	BinOpLt
	BinOpGt
	BinOpNeq
	BinOpLe
	BinOpGe
)

var binOpMapping = []BinOp{
	token.ADD:     BinOpAdd,
	token.SUB:     BinOpSub,
	token.MUL:     BinOpMul,
	token.QUO:     BinOpDiv,
	token.REM:     BinOpMod,
	token.AND:     BinOpAnd,
	token.OR:      BinOpOr,
	token.XOR:     BinOpXor,
	token.SHL:     BinOpShl,
	token.SHR:     BinOpShr,
	token.AND_NOT: BinOpAndNot,
	token.EQL:     BinOpEq,
	token.LSS:     BinOpLt,
	token.GTR:     BinOpGt,
	token.NEQ:     BinOpNeq,
	token.LEQ:     BinOpLe,
	token.GEQ:     BinOpGe,
}

type VarKind byte

const (
	_ VarKind = iota
	VarKindConst
	VarKindParameter
	VarKindLocal
)

type api struct {
	lastBlock int
	buf       *util.ByteBuffer
	program   *ssa.Program
}

func (a *api) MkUnOp(inst *ssa.UnOp) {
	name := resolveRegister(inst)
	u := byte(unOpMapping[inst.Op])
	t := byte(typeslocal.GetType(inst))

	a.buf.Write(byte(MethodMkUnOp))
	a.buf.Write(u)
	a.buf.Write(t)
	a.buf.WriteInt32(name)
	a.writeVar(inst.X)
}

func (a *api) MkBinOp(inst *ssa.BinOp) {
	name := resolveRegister(inst)
	b := byte(binOpMapping[inst.Op])
	t := byte(typeslocal.GetType(inst))

	a.buf.Write(byte(MethodMkBinOp))
	a.buf.Write(b)
	a.buf.Write(t)
	a.buf.WriteInt32(name)
	a.writeVar(inst.X)
	a.writeVar(inst.Y)
}

func (a *api) MkCall(inst *ssa.Call) {
	a.buf.Write(byte(MethodMkCall))
	a.buf.WriteInt32(resolveRegister(inst))

	call := inst.Common()
	args := make([]ssa.Value, 0, len(call.Args)+1)
	if call.IsInvoke() {
		args = append(args, call.Value)
	}

	function := graph.Callee(a.program, call)
	a.buf.WriteInt64(int64(util.ToPointer(function)))
	a.buf.WriteInt64(int64(util.ToPointer(&function.Blocks[0].Instrs[0])))

	a.buf.WriteInt32(int32(graph.LocalsCount(function)))
	a.buf.WriteInt32(int32(len(function.Params)))

	args = append(args, call.Args...)
	for i := range args {
		a.writeVar(args[i])
	}
}

type BuiltinFunc byte

const (
	_ BuiltinFunc = iota
	Len
)

func (a *api) MkCallBuiltin(inst *ssa.Call, name string) {
	a.buf.Write(byte(MethodMkCallBuiltin))
	a.buf.WriteInt32(resolveRegister(inst))

	switch name {
	case "append":
	case "copy":
	case "close":
	case "delete":
	case "print", "println":
	case "len":
		a.buf.Write(byte(Len))
	case "cap":
	case "min":
	case "max":
	case "real":
	case "imag":
	case "complex":
	case "panic":
	case "recover":
	}

	args := inst.Call.Args
	for i := range args {
		a.writeVar(args[i])
	}
}

func (a *api) MkIf(inst *ssa.If) {
	a.buf.Write(byte(MethodMkIf))
	a.writeVar(inst.Cond)
	a.buf.WriteUintptr(util.ToPointer(&inst.Block().Succs[0].Instrs[0]))
	a.buf.WriteUintptr(util.ToPointer(&inst.Block().Succs[1].Instrs[0]))
}

func (a *api) MkReturn(inst *ssa.Return) {
	var value ssa.Value
	switch len(inst.Results) {
	case 0:
		return
	case 1:
		value = inst.Results[0]
	default:
		return
	}

	a.buf.Write(byte(MethodMkReturn))
	a.writeVar(value)
}

func (a *api) MkPhi(inst *ssa.Phi) {
	var edge ssa.Value
	for i, pred := range inst.Block().Preds {
		if a.lastBlock == pred.Index {
			edge = inst.Edges[i]
			break
		}
	}

	a.mkVariable(inst, edge)
}

func (a *api) MkPointerArrayReading(inst *ssa.IndexAddr) {
	a.buf.Write(byte(MethodMkPointerArrayReading))
	a.mkArrayReading(inst, inst.X, inst.Index)
}

func (a *api) MkArrayReading(inst *ssa.Index) {
	a.buf.Write(byte(MethodMkArrayReading))
	a.mkArrayReading(inst, inst.X, inst.Index)
}

func (a *api) MkMapLookup(inst *ssa.Lookup) {
	a.buf.Write(byte(MethodMkMapLookup))
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.Write(byte(typeslocal.GetType(inst)))
	a.writeVar(inst.X)
	a.writeVar(inst.Index)
}

func (a *api) MkMapUpdate(inst *ssa.MapUpdate) {
	a.buf.Write(byte(MethodMkMapUpdate))
	a.writeVar(inst.Map)
	a.writeVar(inst.Key)
	a.writeVar(inst.Value)
}

func (a *api) SetLastBlock(block int) {
	a.lastBlock = block
}

func (a *api) WriteLastBlock() {
	a.buf.WriteInt32(int32(a.lastBlock))
}

func (a *api) Log(values ...any) {
	util.Log(values...)
}

func (a *api) mkArrayReading(inst, array, index ssa.Value) {
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.Write(byte(typeslocal.GetType(inst)))
	a.writeVar(array)
	a.writeVar(index)
}

func (a *api) mkVariable(inst ssa.Value, value ssa.Value) {
	a.buf.Write(byte(MethodMkVariable))
	a.buf.WriteInt32(resolveRegister(inst))
	a.writeVar(value)
}

func (a *api) writeVar(in ssa.Value) {
	switch in := in.(type) {
	case *ssa.Parameter:
		f := in.Parent()
		for i, p := range f.Params {
			if p == in {
				a.buf.Write(byte(VarKindParameter)).Write(byte(typeslocal.GetType(in))).WriteInt32(int32(i))
			}
		}
	case *ssa.Const:
		a.buf.Write(byte(VarKindConst))
		a.writeConst(in)
	default:
		i := resolveRegister(in)
		a.buf.Write(byte(VarKindLocal)).Write(byte(typeslocal.GetType(in))).WriteInt32(i)
	}
}

func (a *api) writeConst(in *ssa.Const) {
	t := typeslocal.GetType(in)
	a.buf.Write(byte(t))
	switch t {
	case typeslocal.TypeBool:
		a.buf.WriteBool(constant.BoolVal(in.Value))
	case typeslocal.TypeInt8:
		a.buf.WriteInt8(int8(in.Int64()))
	case typeslocal.TypeInt16:
		a.buf.WriteInt16(int16(in.Int64()))
	case typeslocal.TypeInt32:
		a.buf.WriteInt32(int32(in.Int64()))
	case typeslocal.TypeInt64:
		a.buf.WriteInt64(in.Int64())
	case typeslocal.TypeUint8:
		a.buf.WriteUint8(uint8(in.Uint64()))
	case typeslocal.TypeUint16:
		a.buf.WriteUint16(uint16(in.Uint64()))
	case typeslocal.TypeUint32:
		a.buf.WriteUint32(uint32(in.Uint64()))
	case typeslocal.TypeUint64:
		a.buf.WriteUint64(in.Uint64())
	case typeslocal.TypeFloat32:
		a.buf.WriteFloat32(float32(in.Float64()))
	case typeslocal.TypeFloat64:
		a.buf.WriteFloat64(in.Float64())
	default:
	}
}

func resolveRegister(in ssa.Value) int32 {
	register, _ := strconv.ParseInt(in.Name()[1:], 10, 32)
	return int32(register)
}
