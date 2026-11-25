package com.miayang.ecommerce.product.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Check-and-decrement in ONE statement. The database locks the row, so if two orders race
     * for the last item, the second one re-checks "available >= qty" after the first commits
     * and updates 0 rows. Returns the number of rows updated: 1 = reserved, 0 = not enough stock.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.available = i.available - :qty,
                   i.reserved  = i.reserved  + :qty
             where i.productId = :productId
               and i.available >= :qty
            """)
    int reserve(@Param("productId") Long productId, @Param("qty") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.available = i.available + :qty,
                   i.reserved  = i.reserved  - :qty
             where i.productId = :productId
               and i.reserved >= :qty
            """)
    int release(@Param("productId") Long productId, @Param("qty") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Inventory i set i.available = i.available + :qty where i.productId = :productId")
    int addStock(@Param("productId") Long productId, @Param("qty") int quantity);
}
