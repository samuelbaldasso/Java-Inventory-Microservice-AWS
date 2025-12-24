package com.sbaldasso.ecommerce_aws.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStock {

    @Id
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    private Integer available;

    private Integer reserved;

    public ProductStock(Long productId, int quantity, int reserved) {
        this.productId = productId;
        this.quantity = quantity;
        this.reserved = reserved;
    }
    public ProductStock(Long productId, int amount) {
        this.productId = productId;
        this.quantity = amount;
        this.available = amount;
        this.reserved = 0;
    }

    public Integer getAvailable() {
        return this.quantity - this.reserved;
    }
}
