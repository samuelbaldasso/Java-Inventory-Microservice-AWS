package com.sbaldasso.ecommerce_aws.controllers;

import com.sbaldasso.ecommerce_aws.entities.ProductStock;
import com.sbaldasso.ecommerce_aws.services.ProductStockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "API de Gestão de Estoque")
public class InventoryController {

    private final ProductStockService service;

    public InventoryController(ProductStockService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ProductStock>> getAllInventory() {
        return service.getAll().isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(service.getAll());
    }

    // GET /api/inventory/{productId} -> Usado pelo useSWR no frontend
    @GetMapping("/{productId}")
    public ResponseEntity<ProductStock> getInventory(@PathVariable Long productId) {
        return service.getProductStock(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/inventory/{productId}/reserve -> Usado ao clicar "Add to Cart"
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<?> reserve(@PathVariable Long productId, @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 1);
        try {
            service.reserveStock(productId, quantity);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /api/inventory/{productId}/release -> Usado ao remover do carrinho
    @PostMapping("/{productId}/release")
    public ResponseEntity<?> release(@PathVariable Long productId, @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 1);
        service.releaseStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    // Endpoint auxiliar para você colocar estoque manualmente (via Postman)
    @PostMapping("/increase")
    public ResponseEntity<?> increase(@RequestParam Long productId, @RequestParam int amount) {
        service.increaseStock(productId, amount);
        return ResponseEntity.ok().build();
    }
}
