package com.trustdesk.domain;

import com.trustdesk.domain.converter.LocalDateConverter;
import com.trustdesk.domain.converter.OrderItemListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved SQL word
public class Order {
    @Id @Column(length = 64)
    private String orderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Column(nullable = false, length = 32) private String status;
    @Convert(converter = LocalDateConverter.class) @Column(nullable = false, length = 10)
    private LocalDate placedAt;
    @Convert(converter = LocalDateConverter.class) @Column(length = 10)
    private LocalDate deliveredAt;
    @Convert(converter = LocalDateConverter.class) @Column(length = 10)
    private LocalDate eligibleReturnUntil;
    @Column(nullable = false) private long total;
    @Column(nullable = false, length = 8) private String currency;
    @Column(nullable = false, length = 32) private String paymentStatus;
    private String trackingNumber;
    @Convert(converter = OrderItemListConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(String orderId, Customer customer, String status, LocalDate placedAt, LocalDate deliveredAt,
                 LocalDate eligibleReturnUntil, long total, String currency, String paymentStatus,
                 String trackingNumber, List<OrderItem> items) {
        this.orderId = orderId;
        this.customer = customer;
        this.status = status;
        this.placedAt = placedAt;
        this.deliveredAt = deliveredAt;
        this.eligibleReturnUntil = eligibleReturnUntil;
        this.total = total;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.trackingNumber = trackingNumber;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public String getOrderId() { return orderId; }
    public Customer getCustomer() { return customer; }
    public String getStatus() { return status; }
    public LocalDate getPlacedAt() { return placedAt; }
    public LocalDate getDeliveredAt() { return deliveredAt; }
    public LocalDate getEligibleReturnUntil() { return eligibleReturnUntil; }
    public long getTotal() { return total; }
    public String getCurrency() { return currency; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getTrackingNumber() { return trackingNumber; }
    public List<OrderItem> getItems() { return items; }
}
