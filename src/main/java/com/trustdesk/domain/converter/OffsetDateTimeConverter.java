package com.trustdesk.domain.converter;

import com.trustdesk.common.Dates;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.OffsetDateTime;

/** ISO text with the original offset ("2026-06-28T10:15:00+05:30"), preserved exactly. */
@Converter
public class OffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, String> {
    @Override
    public String convertToDatabaseColumn(OffsetDateTime attribute) {
        return Dates.format(attribute);
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(String dbData) {
        return Dates.parseOffset(dbData);
    }
}
