package com.intellij.stats.personalization.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.stats.personalization.*
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import java.util.*

abstract class UserFactorStorageBase
    : UserFactorStorage, PersistentStateComponent<UserFactorStorageBase.CollectorState> {

    private var state = CollectorState()

    override fun <U : FactorUpdater> getFactorUpdater(description: UserFactorDescription<U, *>): U =
            description.updaterFactory.invoke(getAggregateFactor(description.factorId))

    override fun <R : FactorReader> getFactorReader(description: UserFactorDescription<*, R>): R =
            description.readerFactory.invoke(getAggregateFactor(description.factorId))

    override fun getState(): CollectorState {
        // TODO[bibaev]: return only actual data for aggregates
        return state
    }

    override fun loadState(newState: CollectorState) {
        state = newState
    }

    private fun getAggregateFactor(factorId: String): MutableDoubleFactor =
            state.aggregateFactors.computeIfAbsent(factorId, { DailyAggregateFactor() })

    class CollectorState {
        @MapAnnotation(surroundValueWithTag = false, keyAttributeName = "name", sortBeforeSave = true, surroundWithTag = false)
        var aggregateFactors: MutableMap<String, DailyAggregateFactor> = HashMap()
    }

    @Tag("AggregateFactor")
    class DailyAggregateFactor : MutableDoubleFactor {
        // todo[bibaev]: avoid using String to store date
        @MapAnnotation(surroundValueWithTag = false, surroundWithTag = false, keyAttributeName = "date", sortBeforeSave = true)
        var aggregates: SortedMap<String, DailyData> = sortedMapOf()

        override fun availableDates(): List<String> =
                aggregates.keys.map { DateUtil.parse(it) }.sorted().map { DateUtil.byDate(it) }

        override fun updateOnToday(key: String, value: Double) {
            aggregates.onToday()[key] = value
        }

        override fun incrementOnToday(key: String) {
            aggregates.onToday().compute(key, { _, oldValue -> if (oldValue == null) 1.0 else oldValue + 1.0 })
        }

        override fun onDate(date: String): Map<String, Double>? = aggregates[date]?.data

        override fun setOnDate(date: String, key: String, value: Double) = aggregates.onDate(date).set(key, value)

        override fun updateOnDate(date: String, updater: MutableMap<String, Double>.() -> Unit) {
            aggregates.compute(date) { _, data ->
                if (data == null) {
                    val dailyData = DailyData()
                    updater.invoke(dailyData.data)
                    dailyData
                } else {
                    updater.invoke(data.data)
                    data
                }
            }
        }

        private fun MutableMap<String, DailyData>.onDate(date: String): MutableMap<String, Double> =
                this.computeIfAbsent(date, { DailyData() }).data

        private fun MutableMap<String, DailyData>.onToday(): MutableMap<String, Double> =
                this.onDate(DateUtil.today())
    }

    @Tag("DailyCollectedData")
    class DailyData {
        @MapAnnotation(surroundWithTag = false, keyAttributeName = "key", valueAttributeName = "value", entryTagName = "observation", sortBeforeSave = true)
        var data: MutableMap<String, Double> = HashMap()
    }
}