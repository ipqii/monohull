package io.monohull.entity;

import io.monohull.dto.ExtraBind;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ExtraBindsConverter implements AttributeConverter<List<ExtraBind>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ExtraBind>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ExtraBind> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize extra binds", e);
        }
    }

    @Override
    public List<ExtraBind> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize extra binds", e);
        }
    }
}
