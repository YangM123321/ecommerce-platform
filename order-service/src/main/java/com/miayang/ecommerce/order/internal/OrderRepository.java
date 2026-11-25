package com.miayang.ecommerce.order.internal;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Page<CustomerOrder> findByUserId(Long userId, Pageable pageable);

    /**
     * SELECT ... FOR UPDATE: locks the order row until the transaction ends, so two
     * simultaneous "cancel" requests can't both release the stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);
}
