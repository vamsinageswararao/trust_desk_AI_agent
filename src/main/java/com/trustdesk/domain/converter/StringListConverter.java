package com.trustdesk.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class StringListConverter extends JsonAttributeConverter<List<String>> {
    public StringListConverter() {
        super(new TypeReference<List<String>>() {});
    }
}
