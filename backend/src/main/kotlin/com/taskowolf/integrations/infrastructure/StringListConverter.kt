package com.taskowolf.integrations.infrastructure

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attr: List<String>?): String =
        mapper.writeValueAsString(attr ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList() else mapper.readValue(dbData, typeRef)

    private companion object {
        // JPA instantiates converters itself (no Spring injection), so the converter owns one immutable mapper.
        // Jackson-2 defaults match the app-wide `spring.jackson.use-jackson2-defaults: true`.
        val mapper: JsonMapper = JsonMapper.builderWithJackson2Defaults().build()
        val typeRef = object : TypeReference<List<String>>() {}
    }
}
