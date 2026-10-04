package com.trustdesk.domain;

import com.trustdesk.domain.converter.LocalDateConverter;
import com.trustdesk.domain.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @Column(length = 64)
    private String customerId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String email;
    @Column(nullable = false, length = 32) private String tier;
    @Column(nullable = false, length = 8) private String country;
    @Convert(converter = LocalDateConverter.class)
    @Column(nullable = false, length = 10)
    private LocalDate createdAt;
    @Column(nullable = false) private boolean verified;
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> tags = new ArrayList<>();

    protected Customer() {}

    public Customer(String customerId, String name, String email, String tier, String country,
                    LocalDate createdAt, boolean verified, List<String> tags) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.country = country;
        this.createdAt = createdAt;
        this.verified = verified;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getTier() { return tier; }
    public String getCountry() { return country; }
    public LocalDate getCreatedAt() { return createdAt; }
    public boolean isVerified() { return verified; }
    public List<String> getTags() { return tags; }
}
