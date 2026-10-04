package com.trustdesk.repository;

import com.trustdesk.domain.Customer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    List<Customer> findByCustomerIdNot(String customerId);
}
