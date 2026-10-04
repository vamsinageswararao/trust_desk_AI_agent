package com.trustdesk.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter
public class ObjectMapConverter extends JsonAttributeConverter<Map<String, Object>> {
    public ObjectMapConverter() {
        super(new TypeReference<Map<String, Object>>() {});
    }
}
