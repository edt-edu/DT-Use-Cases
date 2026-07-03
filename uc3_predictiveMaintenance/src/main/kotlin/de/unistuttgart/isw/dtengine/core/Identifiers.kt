package de.unistuttgart.isw.dtengine.core

@JvmInline
value class ComponentId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class DataPointId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ModelPropertyId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class MappingId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ServiceId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class CorrelationId(val value: String) {
    override fun toString(): String = value
}
