package com.taskowolf.integrations.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
    private val mapper = ObjectMapper()
    private val typeRef = object : TypeReference<List<String>>() {}

    override fun convertToDatabaseColumn(attr: List<String>?): String =
        mapper.writeValueAsString(attr ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList() else mapper.readValue(dbData, typeRef)
}
