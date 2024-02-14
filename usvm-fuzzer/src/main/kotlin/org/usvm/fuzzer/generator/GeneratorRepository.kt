package org.usvm.fuzzer.generator

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.fuzzer.generator.arrays.ArrayGenerator
import org.usvm.fuzzer.generator.collections.StackGenerator
import org.usvm.fuzzer.generator.collections.deque.ArrayDequeGenerator
import org.usvm.fuzzer.generator.collections.list.ArrayListGenerator
import org.usvm.fuzzer.generator.collections.set.HashSetGenerator
import org.usvm.fuzzer.generator.collections.set.LinkedHashSetGenerator
import org.usvm.fuzzer.generator.collections.list.LinkedListGenerator
import org.usvm.fuzzer.generator.collections.map.ConcurrentHashMapGenerator
import org.usvm.fuzzer.generator.collections.map.HashMapGenerator
import org.usvm.fuzzer.generator.collections.map.LinkedHashMapGenerator
import org.usvm.fuzzer.generator.collections.map.TreeMapGenerator
import org.usvm.fuzzer.generator.collections.queue.PriorityQueueGenerator
import org.usvm.fuzzer.generator.collections.set.TreeSetGenerator
import org.usvm.fuzzer.generator.lambdas.LambdaGenerator
import org.usvm.fuzzer.generator.`object`.*
import org.usvm.fuzzer.generator.other.ObjectGenerator
import org.usvm.fuzzer.generator.other.StringGenerator
import org.usvm.fuzzer.generator.primitives.*
import org.usvm.fuzzer.generator.reflection.ClassGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.*
import org.usvm.instrumentation.util.stringType

class GeneratorRepository {

    lateinit var context: GeneratorContext
    lateinit var jcClasspath: JcClasspath

    fun registerGeneratorContext(generatorContext: GeneratorContext) {
        context = generatorContext
        jcClasspath = generatorContext.jcClasspath
    }

    private fun Generator.appendContext(): Generator = this.also { it.ctx = context }

    fun getGeneratorForType(jcType: JcTypeWrapper): Generator = with(jcType.type.unboxIfNeeded()) {
        when (this) {
            //TODO repair
            is JcArrayType -> ArrayGenerator(jcType)
            is JcPrimitiveType -> getPrimitiveGeneratorForType(this, jcClasspath)
            is JcClassType -> getBuiltInOrUserGenerator(jcType, jcClasspath)
            else -> TODO()
        }.appendContext()
    }

    fun getMockGeneratorForType(jcType: JcTypeWrapper) = MockClassGenerator(jcType).appendContext()

    private fun getBuiltInOrUserGenerator(jcType: JcTypeWrapper, jcClasspath: JcClasspath) =
        when (jcType.type.typeNameWOGenerics) {
            jcClasspath.classType().typeNameWOGenerics -> ClassGenerator()
            jcClasspath.stringType().typeNameWOGenerics -> StringGenerator()
            jcClasspath.arrayListType().typeNameWOGenerics -> ArrayListGenerator(jcType)
            jcClasspath.collectionType().typeNameWOGenerics -> getRandomFrom(
                ArrayListGenerator(jcType), LinkedListGenerator(jcType), HashSetGenerator(jcType), LinkedHashSetGenerator(jcType)
            )
            jcClasspath.iterableType().typeNameWOGenerics -> getRandomFrom(
                ArrayListGenerator(jcType), LinkedListGenerator(jcType), HashSetGenerator(jcType), LinkedHashSetGenerator(jcType)
            )
            jcClasspath.listType().typeNameWOGenerics -> getRandomFrom(
                ArrayListGenerator(jcType), LinkedListGenerator(jcType), HashSetGenerator(jcType), LinkedHashSetGenerator(jcType)
            )
            jcClasspath.setType().typeNameWOGenerics -> getRandomFrom(
                HashSetGenerator(jcType), LinkedListGenerator(jcType), TreeSetGenerator(jcType)
            )
            jcClasspath.hashSetType().typeNameWOGenerics -> HashSetGenerator(jcType)
            jcClasspath.linkedHashMapType().typeNameWOGenerics -> LinkedListGenerator(jcType)
            jcClasspath.treeSetType().typeNameWOGenerics -> TreeSetGenerator(jcType)
            jcClasspath.sortedSetType().typeNameWOGenerics -> TreeSetGenerator(jcType)
            jcClasspath.mapType().typeNameWOGenerics -> getRandomFrom(
                HashMapGenerator(jcType), LinkedHashMapGenerator(jcType), TreeMapGenerator(jcType), ConcurrentHashMapGenerator(jcType)
            )
            jcClasspath.hashMapType().typeNameWOGenerics -> HashMapGenerator(jcType)
            jcClasspath.linkedHashMapType().typeNameWOGenerics -> LinkedHashMapGenerator(jcType)
            jcClasspath.treeMapType().typeNameWOGenerics -> TreeMapGenerator(jcType)
            jcClasspath.dequeType().typeNameWOGenerics -> getRandomFrom(
                ArrayDequeGenerator(jcType), LinkedListGenerator(jcType)
            )
            jcClasspath.arrayDequeType().typeNameWOGenerics -> ArrayDequeGenerator(jcType)
            jcClasspath.queueType().typeNameWOGenerics -> getRandomFrom(
                ArrayDequeGenerator(jcType), LinkedListGenerator(jcType), PriorityQueueGenerator(jcType)
            )
            jcClasspath.stackType().typeNameWOGenerics -> StackGenerator(jcType)
            jcClasspath.priorityQueueType().typeNameWOGenerics -> PriorityQueueGenerator(jcType)
            jcClasspath.navigableSetType().typeNameWOGenerics -> TreeSetGenerator(jcType)
            jcClasspath.objectType.typeNameWOGenerics -> ObjectGenerator()
            else -> getSuitableGeneratorForType(jcType)
        }

    private fun getSuitableGeneratorForType(jcType: JcTypeWrapper): Generator {
        val jcClass = (jcType.type as JcClassType).jcClass
        return when {
            jcClass.isFunctionalInterface -> LambdaGenerator(jcType)
            jcClass.isAbstract || jcClass.isInterface -> SafeAbstractClassGenerator(jcType)
            jcClass.isEnum -> EnumClassGenerator(jcType)
            GeneratorSettings.generationMode == GenerationMode.SAFE -> SafeUserClassGenerator(jcType)
            GeneratorSettings.generationMode == GenerationMode.UNSAFE -> UnsafeUserClassGenerator(jcType)
            else -> error("Cant find suitable generator for type ${jcType.type.typeName}")
        }
    }

    private fun getRandomFrom(vararg generators: Generator) = generators.random()

    private val JcType.typeNameWOGenerics
        get() = typeName.substringBefore('<')

    private fun getPrimitiveGeneratorForType(jcType: JcType, jcClasspath: JcClasspath) =
        when (jcType) {
            jcClasspath.boolean -> BooleanGenerator()
            jcClasspath.byte -> ByteGenerator()
            jcClasspath.int -> IntegerGenerator()
            jcClasspath.short -> ShortGenerator()
            jcClasspath.long -> LongGenerator()
            jcClasspath.float -> FloatGenerator()
            jcClasspath.double -> DoubleGenerator()
            jcClasspath.char -> CharGenerator()
            else -> error("Not primitive type")
        }


}