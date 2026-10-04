package com.trustdesk.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trustdesk.domain.OrderItem;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class OrderItemListConverter extends JsonAttributeConverter<List<OrderItem>> {
    public OrderItemListConverter() {
        super(new TypeReference<List<OrderItem>>() {});
    }
}
