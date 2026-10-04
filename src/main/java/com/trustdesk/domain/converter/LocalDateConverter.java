package com.trustdesk.domain.converter;

import com.trustdesk.common.Dates;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;

/** ISO text ("2026-06-24"): avoids sqlite-jdbc's epoch-millis date handling and timezone drift. */
@Converter
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {
    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return Dates.format(attribute);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return Dates.parseDate(dbData);
    }
}
