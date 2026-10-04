package com.trustdesk.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import java.util.List;
import java.util.Map;

@Converter
public class MapListConverter extends JsonAttributeConverter<List<Map<String, Object>>> {
    public MapListConverter() {
        super(new TypeReference<List<Map<String, Object>>>() {});
    }
}
