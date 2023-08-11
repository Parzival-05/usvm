#include "approximations.h"
#include "wrapper.h"
#include "virtual_objects.h"


PyObject *
Approximation_len(PyObject *o) {
    assert(is_wrapped(o));
    SymbolicAdapter *adapter = get_adapter(o);
    PyObject *concrete = unwrap(o);
    long concrete_result_long = PyObject_Size(o);
    if (concrete_result_long < 0) {
        assert(PyErr_Occurred());
        return 0;
    }
    PyObject *concrete_result = PyLong_FromLong(concrete_result_long);
    PyObject *symbolic = Py_None;
    if (PyList_Check(concrete)) {
        symbolic = adapter->list_get_size(adapter->handler_param, get_symbolic_or_none(o));
        if (!symbolic) return 0;
    } else if (is_virtual_object(concrete)) {
        symbolic = adapter->symbolic_virtual_unary_fun(adapter->handler_param, get_symbolic_or_none(o));
        if (!symbolic) return 0;
    }

    return wrap(concrete_result, symbolic, adapter);
}

PyObject *
Approximation_isinstance(PyObject *obj, PyObject *type_wrapped) {
    assert(is_wrapped(obj));
    PyObject *type = unwrap(type_wrapped);
    SymbolicAdapter *adapter = get_adapter(obj);
    PyObject *concrete = unwrap(obj);
    int concrete_result_long = PyObject_IsInstance(concrete, type);
    if (concrete_result_long < 0) {
        assert(PyErr_Occurred());
        return 0;
    }
    PyObject *concrete_result = PyLong_FromLong(concrete_result_long);
    PyObject *symbolic = Py_None;
    if (PyType_Check(type)) {
        symbolic = adapter->symbolic_isinstance(adapter->handler_param, get_symbolic_or_none(obj), type);
        if (!symbolic) return 0;
    }

    return wrap(concrete_result, symbolic, adapter);
}