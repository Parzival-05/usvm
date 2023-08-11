/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_usvm_interpreter_CPythonAdapter */

#ifndef _Included_org_usvm_interpreter_CPythonAdapter
#define _Included_org_usvm_interpreter_CPythonAdapter
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    initializePython
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializePython
  (JNIEnv *, jobject);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    finalizePython
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_finalizePython
  (JNIEnv *, jobject);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getNewNamespace
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getNewNamespace
  (JNIEnv *, jobject);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    addName
 * Signature: (JJLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_addName
  (JNIEnv *, jobject, jlong, jlong, jstring);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    concreteRun
 * Signature: (JLjava/lang/String;Z)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRun
  (JNIEnv *, jobject, jlong, jstring, jboolean);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    eval
 * Signature: (JLjava/lang/String;Z)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_eval
  (JNIEnv *, jobject, jlong, jstring, jboolean);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    concreteRunOnFunctionRef
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRunOnFunctionRef
  (JNIEnv *, jobject, jlong, jlongArray);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    concolicRun
 * Signature: (J[J[J[Lorg/usvm/language/SymbolForCPython;Lorg/usvm/interpreter/ConcolicRunContext;Z)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_concolicRun
  (JNIEnv *, jobject, jlong, jlongArray, jlongArray, jobjectArray, jobject, jboolean);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    printPythonObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_printPythonObject
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getIterableElements
 * Signature: (J)[J
 */
JNIEXPORT jlongArray JNICALL Java_org_usvm_interpreter_CPythonAdapter_getIterableElements
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getPythonObjectRepr
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectRepr
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getPythonObjectTypeName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectTypeName
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getPythonObjectType
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectType
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getNameOfPythonType
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getNameOfPythonType
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getInstructionFromFrame
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_getInstructionFromFrame
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    getFunctionFromFrame
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getFunctionFromFrame
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    allocateVirtualObject
 * Signature: (Lorg/usvm/language/VirtualPythonObject;)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_allocateVirtualObject
  (JNIEnv *, jobject, jobject);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    makeList
 * Signature: ([J)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_makeList
  (JNIEnv *, jobject, jlongArray);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasNbBool
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbBool
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasNbInt
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbInt
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasNbAdd
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbAdd
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasNbMultiply
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbMultiply
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasSqLength
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasSqLength
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasMpLength
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpLength
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasMpSubscript
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpSubscript
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasMpAssSubscript
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpAssSubscript
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasTpRichcmp
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpRichcmp
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasTpIter
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpIter
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    typeHasStandardNew
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasStandardNew
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    callStandardNew
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_callStandardNew
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    extractException
 * Signature: (J)Ljava/lang/Throwable;
 */
JNIEXPORT jthrowable JNICALL Java_org_usvm_interpreter_CPythonAdapter_extractException
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    decref
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_decref
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif