package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.arrayListType

class ArrayListGenerator(type: JcTypeWrapper): ListGenerator(type.type.classpath.arrayListType(), type.typeArguments) {}