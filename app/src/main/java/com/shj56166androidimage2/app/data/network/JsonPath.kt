package com.shj56166androidimage2.app.data.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun getByPath(source: JsonElement?, path: String?): JsonElement? {
    if (source == null || path.isNullOrBlank()) return source
    var current: JsonElement? = source
    path.split('.').filter { it.isNotBlank() }.forEach { key ->
        current =
            when {
                current == null -> null
                key == "*" -> current
                current is JsonObject -> current[key]
                current is JsonArray && key.toIntOrNull() != null -> current.getOrNull(key.toInt())
                else -> null
            }
    }
    return current
}

fun getAllByPath(source: JsonElement?, path: String?): List<JsonElement> {
    if (source == null) return emptyList()
    if (path.isNullOrBlank()) return listOf(source)
    var current: List<JsonElement> = listOf(source)
    path.split('.').filter { it.isNotBlank() }.forEach { key ->
        val next = mutableListOf<JsonElement>()
        current.forEach { element ->
            when {
                key == "*" && element is JsonArray -> next.addAll(element)
                key == "*" && element is JsonObject -> next.addAll(element.values)
                element is JsonObject -> element[key]?.let(next::add)
                element is JsonArray && key.toIntOrNull() != null -> element.getOrNull(key.toInt())?.let(next::add)
            }
        }
        current = next
    }
    return current
}

fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.content
