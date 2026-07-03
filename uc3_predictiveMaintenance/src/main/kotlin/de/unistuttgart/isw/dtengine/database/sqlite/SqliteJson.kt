package de.unistuttgart.isw.dtengine.database.sqlite

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal object SqliteJson {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val mapType = object : TypeReference<Map<String, String>>() {}
    private val anyMapType = object : TypeReference<Map<String, Any?>>() {}
    private val stringListType = object : TypeReference<List<String>>() {}

    fun write(value: Any?): String = mapper.writeValueAsString(value)

    fun readAny(json: String?): Any? {
        if (json.isNullOrBlank() || json == "null") return null
        return mapper.readValue(json, Any::class.java)
    }

    fun readStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return mapper.readValue(json, mapType)
    }

    fun readAnyMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) return emptyMap()
        return mapper.readValue(json, anyMapType)
    }

    fun readStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return mapper.readValue(json, stringListType)
    }

    fun readStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return mapper.readValue(json, stringListType).toSet()
    }
}

