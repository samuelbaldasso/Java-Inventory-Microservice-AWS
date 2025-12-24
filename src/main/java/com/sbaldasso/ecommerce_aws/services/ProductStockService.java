package com.sbaldasso.ecommerce_aws.services;

import com.sbaldasso.ecommerce_aws.entities.ProductStock;
import com.sbaldasso.ecommerce_aws.repositories.ProductStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductStockService {

    private final ProductStockRepository repository;

    public ProductStockService(ProductStockRepository repository) {
        this.repository = repository;
    }

    public List<ProductStock> getAll() {
        return repository.findAll();
    }

    public Optional<ProductStock> getProductStock(Long productId) {
        return repository.findById(productId);
    }

    // Verifica se há estoque disponível
    public boolean checkStock(Long productId, int amount) {
        return repository.findById(productId)
                .map(ps -> ps.getAvailable() >= amount)
                .orElse(false);
    }

    // Aumenta estoque físico (entrada de nota)
    public void increaseStock(Long productId, int amount) {
        ProductStock stock = repository.findById(productId)
                .orElse(new ProductStock(productId, 0, 0));

        stock.setQuantity(stock.getQuantity() + amount);
        repository.save(stock);
    }

    // Reserva estoque (Adicionar ao carrinho)
    public void reserveStock(Long productId, int amount) {
        ProductStock stock = repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado no estoque"));

        if (stock.getAvailable() < amount) {
            throw new IllegalStateException("Estoque insuficiente");
        }

        stock.setReserved(stock.getReserved() + amount);
        repository.save(stock);
    }

    // Libera reserva (Remover do carrinho ou Cancelar pedido)
    public void releaseStock(Long productId, int amount) {
        ProductStock stock = repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        int newReserved = stock.getReserved() - amount;
        if (newReserved < 0) newReserved = 0;

        stock.setReserved(newReserved);
        repository.save(stock);
    }

    // Baixa definitiva (Compra confirmada)
    public void decreaseStock(Long productId, int amount) {
        ProductStock stock = repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        // Reduz tanto o físico quanto o reservado, pois a reserva virou venda
        stock.setReserved(stock.getReserved() - amount);
        stock.setQuantity(stock.getQuantity() - amount);

        repository.save(stock);
    }
}