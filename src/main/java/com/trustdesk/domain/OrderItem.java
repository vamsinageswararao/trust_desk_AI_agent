package com.trustdesk.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Line item embedded (as JSON) in an order; mirrors the seed/API shape. */
public record OrderItem(String sku, String name, int quantity, String category,
                        @JsonProperty("final_sale") boolean finalSale) {}
