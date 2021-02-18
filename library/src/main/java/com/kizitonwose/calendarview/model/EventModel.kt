package com.kizitonwose.calendarview.model

/**
 * Wrapper around [InternalEvent] specifying event cells.
 */
internal sealed class EventModel {
    abstract val columnIndex: Int
    abstract val rowIndex: Int
    abstract val daySpan: Int
    abstract val active: Boolean
    abstract val leftBoundaryStart: Boolean
    abstract val rightBoundaryEnd: Boolean

    data class Single(
        val name: String?,
        override val columnIndex: Int,
        override val rowIndex: Int,
        override val active: Boolean,
        val apiModel: Event,
        val original: InternalEvent.Single
    ) : EventModel() {
        override val daySpan: Int = 1
        override val leftBoundaryStart: Boolean = true
        override val rightBoundaryEnd: Boolean = true
    }

    data class AllDay(
        val name: String?,
        override val columnIndex: Int,
        override val rowIndex: Int,
        override val daySpan: Int,
        override val active: Boolean,
        override val leftBoundaryStart: Boolean,
        override val rightBoundaryEnd: Boolean,
        val apiModel: Event,
        val original: InternalEvent.AllDay.Original
    ) : EventModel()

    data class Squashed(
        override val columnIndex: Int,
        override val rowIndex: Int,
        val apiModels: List<Event>
    ): EventModel() {
        override val active: Boolean = true
        override val daySpan: Int = 1
        override val leftBoundaryStart: Boolean = true
        override val rightBoundaryEnd: Boolean = true
    }
}