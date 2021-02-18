package com.kizitonwose.calendarview.ui

import android.util.Range
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.kizitonwose.calendarview.model.*
import java.time.LocalDate

internal class WeekHolder(
    private val dayHolders: List<DayHolder>,
    private val eventListHolder: EventListHolder
) {

    private lateinit var container: LinearLayout

    fun inflateWeekView(parent: LinearLayout, isLastWeek: Boolean): View {
        container = LinearLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL

            addView(
                LinearLayout(parent.context).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = dayHolders.count().toFloat()
                    for (holder in dayHolders) {
                        addView(holder.inflateDayView(this))
                    }
                }
            )
            addView(eventListHolder.inflateEventListView(this, isLastWeek))
        }
        return container
    }

    fun bindWeekView(daysOfWeek: List<CalendarDay>, events: List<InternalEvent>, calendarMonth: CalendarMonth) {

        val wrappers: List<InternalEventWrapper> = events
            .groupBy {
                when (it) {
                    is InternalEvent.Single -> it
                    is InternalEvent.AllDay.Original -> it
                    is InternalEvent.AllDay.Partial -> it.original
                }
            }
            .map { (event, events: List<InternalEvent>) ->
                when (event) {
                    is InternalEvent.Single -> InternalEventWrapper.Single(event)
                    is InternalEvent.AllDay.Original -> InternalEventWrapper.Multiple(events.mapToAllDayEvents())
                    is InternalEvent.AllDay.Partial -> throw IllegalStateException()
                }
            }

        val sortedWrappersIntoRows = mutableListOf<MutableList<InternalEventWrapper>>()
        wrappers
            .forEach { notSortedWrapper ->
                val nonCollidingRow = sortedWrappersIntoRows.find { row ->
                    row.none { wrapper -> doCollide(notSortedWrapper.getStartingEvent(), wrapper.getStartingEvent()) }
                }

                if (nonCollidingRow != null) {
                    nonCollidingRow.add(notSortedWrapper)
                } else {
                    sortedWrappersIntoRows.add(mutableListOf(notSortedWrapper)) // add new row
                }
            }

        val filteredNonNormalized: List<EventModel> =
            sortedWrappersIntoRows
                .asSequence()
                .mapIndexed { rowIndex, list ->
                    list.map { wrapper ->
                        when (wrapper) {
                            is InternalEventWrapper.Single -> {
                                val columnIndex = daysOfWeek.indexOfFirst { it.date.isEqual(wrapper.event.start) }

                                EventModel.Single(
                                    name = wrapper.event.name,
                                    rowIndex = rowIndex,
                                    columnIndex = columnIndex,
                                    active = wrapper.event.active,
                                    original = wrapper.event,
                                    apiModel = wrapper.event.apiEvent
                                )
                            }
                            is InternalEventWrapper.Multiple -> {
                                val startWeekEvent = wrapper.getStartingEventThisWeek()
                                val columnIndex = daysOfWeek.indexOfFirst {
                                    it.date.isEqual(startWeekEvent.start)
                                }
                                val original = wrapper.getOriginalEvent()

                                val leftBoundaryStart: Boolean = startWeekEvent == original
                                val rightBoundaryEnd: Boolean =
                                    wrapper.getEndingEventThisWeek().start.isEqual(original.end)

                                EventModel.AllDay(
                                    name = original.name,
                                    rowIndex = rowIndex,
                                    columnIndex = columnIndex,
                                    active = original.active,
                                    original = original,
                                    daySpan = wrapper.events.size,
                                    apiModel = wrapper.getOriginalEvent().apiEvent,
                                    leftBoundaryStart = leftBoundaryStart,
                                    rightBoundaryEnd = rightBoundaryEnd
                                )
                            }
                        }
                    }
                }
                .flatten()
                .toList()

        val filteredOtherThanSquashed: List<EventModel> = filteredNonNormalized.filter { it.rowIndex < 3 }

        val filteredSquashed: List<EventModel.Squashed> = filteredNonNormalized
            .filter { it.rowIndex >= 3 }
            .flatMap {
                when (it) {
                    is EventModel.Single -> listOf(
                        EventModel.Squashed(
                            columnIndex = it.columnIndex,
                            rowIndex = -1,
                            apiModels = listOf(it.apiModel)
                        )
                    )
                    is EventModel.AllDay -> {
                        val column = it.columnIndex
                        val span = it.daySpan

                        (column..(column + span)).map { columnIndex ->
                            EventModel.Squashed(
                                columnIndex = columnIndex,
                                rowIndex = -1,
                                apiModels = listOf(it.apiModel)
                            )
                        }
                    }
                    is EventModel.Squashed -> throw IllegalStateException()
                }
            }
            .groupBy {
                it.columnIndex
            }
            .map { (column, eventModels) ->
                val apiModels = eventModels.flatMap { it.apiModels }

                EventModel.Squashed(
                    columnIndex = column,
                    rowIndex = -1,
                    apiModels = apiModels
                )
            }

        val filtered: List<EventModel> = filteredOtherThanSquashed + filteredSquashed

        dayHolders.forEachIndexed { index, holder ->
            // Indices can be null if OutDateStyle is NONE. We set the
            // visibility for the views at these indices to INVISIBLE.
            val day = daysOfWeek.getOrNull(index)
            holder.bindDayView(day)
        }

        eventListHolder.bindEventList(daysOfWeek, filtered, calendarMonth)
    }

    fun reloadDay(day: CalendarDay): Boolean {
        val reloaded = dayHolders.any { it.reloadViewIfNecessary(day) }
        eventListHolder.reloadViewIfNecessary()
        return reloaded
    }

    fun recycle() {
        dayHolders.forEach { it.recycle() }
        eventListHolder.recycle()
    }
}

private fun doCollide(lhs: InternalEvent, rhs: InternalEvent): Boolean {
    val firstRange = Range.create(lhs.start, lhs.end)
    val secondRange = Range.create(rhs.start, rhs.end)

    return doCollideWithRange(firstRange, secondRange)
}

private fun doCollideWithRange(first: Range<LocalDate>, second: Range<LocalDate>): Boolean {
    return if (first.contains(second) || second.contains(first)) {
        true
    } else if (first.lower.isEqual(second.lower) || first.lower.isEqual(second.upper) || first.upper.isEqual(second.upper)) {
        true
    } else if (first.lower.isBefore(second.lower) && first.upper.isAfter(second.lower)) {
        true
    } else {
        first.upper.isAfter(second.upper) && first.lower.isBefore(second.upper)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun List<InternalEvent>.mapToAllDayEvents(): List<InternalEvent.AllDay> {
    return map { event ->
        when (event) {
            is InternalEvent.Single -> throw IllegalStateException()
            is InternalEvent.AllDay.Original -> event
            is InternalEvent.AllDay.Partial -> event
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun InternalEventWrapper.getStartingEvent(): InternalEvent {
    return when (this) {
        is InternalEventWrapper.Single -> this.event
        is InternalEventWrapper.Multiple -> getOriginalEvent()
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun InternalEventWrapper.Multiple.getOriginalEvent(): InternalEvent.AllDay.Original {
    return this.events.first().let {
        when (it) {
            is InternalEvent.AllDay.Original -> it
            is InternalEvent.AllDay.Partial -> it.original
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun InternalEventWrapper.Multiple.getStartingEventThisWeek(): InternalEvent.AllDay {
    return this.events.firstOrNull { it is InternalEvent.AllDay.Original }
        ?: this.events.minOfWith(compareBy { it.start }, { it })
}

@Suppress("NOTHING_TO_INLINE")
private inline fun InternalEventWrapper.Multiple.getEndingEventThisWeek(): InternalEvent.AllDay {
    return this.events.firstOrNull { it is InternalEvent.AllDay.Original }
        ?: this.events.maxOfWith(compareBy { it.start }, { it })
}
