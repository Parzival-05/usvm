package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.utils.cast
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.memory.*
import org.usvm.memory.collections.UAllocatedArrayId
import org.usvm.memory.collections.UInputArrayId
import org.usvm.memory.collections.UMemoryUpdatesVisitor
import org.usvm.memory.collections.USymbolicArrayId
import org.usvm.memory.collections.USymbolicArrayIndex
import org.usvm.memory.collections.USymbolicCollection
import org.usvm.memory.collections.USymbolicCollectionId
import org.usvm.model.UMemory2DArray
import org.usvm.sampleUValue
import org.usvm.uctx
import java.util.IdentityHashMap

/**
 * [URegionTranslator] defines a template method that translates a region reading to a specific [KExpr] with a sort
 * [Sort].
 */
//class URegionTranslator<CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort, Result>(
//    private val updateTranslator: UMemoryUpdatesVisitor<Key, Sort, Result>,
//) {
//    fun translateReading(region: USymbolicCollection<CollectionId, Key, Sort>, key: Key): KExpr<Sort> {
//        val translated = translate(region)
//        return updateTranslator.visitSelect(translated, key)
//    }
//
//    private val visitorCache = IdentityHashMap<Any?, Result>()
//
//    private fun translate(region: USymbolicCollection<CollectionId, Key, Sort>): Result =
//        region.updates.accept(updateTranslator, visitorCache)
//}

interface URegionTranslator<CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort> {

    fun translateReading(region: USymbolicCollection<CollectionId, Key, Sort>, key: Key): KExpr<Sort>

    fun decodeCollection(model: KModel, mapping: Map<UHeapRef, UConcreteHeapRef>): USymbolicCollection<CollectionId, Key, Sort>
}

class UInputArrayRegionTranslator<ArrayType, Sort : USort>(
    private val collectionId: UInputArrayId<ArrayType, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
    val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, sizeSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>>()
    private val updatesTranslator = U2DArrayUpdateVisitor(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
        val modelArray = UMemory2DArray<UAddressSort, USizeSort, Sort>(initialValue, model, mapping)
        var region: UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> = UArrayRegion<ArrayType, Sort>(defaultValue = modelArray.constValue)
        modelArray.values.forEach { key, value ->
            region = region.write(UArrayIndexRef(collectionId.sort, key.first, key.second, collectionId.arrayType), value, guard = value.uctx.trueExpr)
        }
        return region
    }

}

/**
 * A region translator for 1-dimensional symbolic regions.
 *
 * @param exprTranslator defines how to perform translation on inner values.
 * @param initialValue defines an initial value for a translated array.
 */
internal class U1DArrayUpdateTranslate<KeySort : USort, Sort : USort>(
    private val exprTranslator: UExprTranslator<*>,
    private val initialValue: KExpr<KArraySort<KeySort, Sort>>,
) : UMemoryUpdatesVisitor<UExpr<KeySort>, Sort, KExpr<KArraySort<KeySort, Sort>>> {

    /**
     * [key] is already translated, so we don't have to call it explicitly.
     */
    override fun visitSelect(result: KExpr<KArraySort<KeySort, Sort>>, key: KExpr<KeySort>): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key)

    override fun visitInitialValue(): KExpr<KArraySort<KeySort, Sort>> = initialValue

    override fun visitUpdate(
        previous: KExpr<KArraySort<KeySort, Sort>>,
        update: UUpdateNode<UExpr<KeySort>, Sort>,
    ): KExpr<KArraySort<KeySort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key = update.key.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key, value), previous)
//                 or
//                val keyDecl = mkFreshConst("k", previous.sort.domain)
//                mkArrayLambda(keyDecl.decl, mkIte(keyDecl eq key and guard, value, previous.select(keyDecl)))
//                 or
//                previous.store(key, mkIte(guard, value, previous.select(key)))
            }

            is URangedUpdateNode<*, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<USymbolicArrayId<Any?, Sort, *>, Any?, UExpr<KeySort>, Sort>)
                        val key = mkFreshConst("k", previous.sort.domain)

                        val from = update.sourceCollection

                        val keyMapper = from.collectionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key))
                        val isInside = update.includesSymbolically(key).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(from, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key))
                        mkArrayLambda(key.decl, ite)
                    }
                }
            }

            is UMergeUpdateNode<*, *, *, *, *, *> -> {
                when(update.guard){
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        update as UMergeUpdateNode<USymbolicMapId<Any?, KeySort, *, Sort, *>, Any?, Any?, KeySort, *, Sort>

                        val key = mkFreshConst("k", previous.sort.domain)

                        val from = update.sourceCollection

                        val keyMapper = from.collectionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key))
                        val isInside = update.includesSymbolically(key).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(from, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key))
                        mkArrayLambda(key.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}

/**
 * A region translator for 2-dimensional symbolic regions.
 *
 * @param exprTranslator defines how to perform translation on inner values.
 * @param initialValue defines an initial value for a translated array.
 */
internal class U2DArrayUpdateVisitor<
    Key1Sort : USort,
    Key2Sort : USort,
    Sort : USort,
    >(
    private val exprTranslator: UExprTranslator<*, *>,
    private val initialValue: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
) : UMemoryUpdatesVisitor<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort, KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>> {

    /**
     * [key] is already translated, so we don't have to call it explicitly.
     */
    override fun visitSelect(
        result: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>,
    ): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key.first, key.second)

    override fun visitInitialValue(): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = initialValue

    override fun visitUpdate(
        previous: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        update: UUpdateNode<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>,
    ): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key1 = update.key.first.translated
                val key2 = update.key.second.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key1, key2, value), previous)
            }

            is URangedUpdateNode<*, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<USymbolicArrayId<Any?, Sort, *>, Any?, Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>)
                        val key1 = mkFreshConst("k1", previous.sort.domain0)
                        val key2 = mkFreshConst("k2", previous.sort.domain1)

                        val region = update.sourceCollection
                        val keyMapper = region.collectionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key1 to key2))
                        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(region, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key1, key2))
                        mkArrayLambda(key1.decl, key2.decl, ite)
                    }
                }
            }

            is UMergeUpdateNode<*, *, *, *, *, *> -> {
                when(update.guard){
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        update as UMergeUpdateNode<USymbolicMapId<Any?, *, *, Sort, *>, Any?, Any?, *, *, Sort>

                        val key1 = mkFreshConst("k1", previous.sort.domain0)
                        val key2 = mkFreshConst("k2", previous.sort.domain1)

                        val region = update.sourceCollection
                        val keyMapper = region.collectionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key1 to key2))
                        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(region, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key1, key2))
                        mkArrayLambda(key1.decl, key2.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}
