package io.monohull.entity;

import io.monohull.dto.ExtraEnvVar;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ExtraEnvVarsConverter implements AttributeConverter<List<ExtraEnvVar>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ExtraEnvVar>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ExtraEnvVar> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize extra env vars", e);
        }
    }

    @Override
    public List<ExtraEnvVar> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize extra env vars", e);
        }
    }
}
