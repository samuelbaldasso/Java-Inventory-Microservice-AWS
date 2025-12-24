package com.sbaldasso.ecommerce_aws.repositories;

import com.sbaldasso.ecommerce_aws.entities.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    boolean existsByProductId(Long id);
}