package com.sbaldasso.ecommerce_aws.config;

import com.sbaldasso.ecommerce_aws.dto.ProductCreatedEvent; // Ajuste o pacote se necessário
import com.sbaldasso.ecommerce_aws.entities.ProductStock;
import com.sbaldasso.ecommerce_aws.repositories.ProductStockRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RabbitMQListener {

    private final Logger logger = LoggerFactory.getLogger(RabbitMQListener.class);
    private final ProductStockRepository productStockRepository;

    public RabbitMQListener(ProductStockRepository productStockRepository) {
        this.productStockRepository = productStockRepository;
    }

    @RabbitListener(queues = "product.created.queue")
    public void handleProductCreated(ProductCreatedEvent event) {
        logger.info("Recebido evento de criação de produto: {}", event);

        if (!productStockRepository.existsByProductId(event.getId())) {
            ProductStock stock = new ProductStock();
            stock.setProductId(event.getId());
            stock.setAvailable(0); // Estoque inicial
            stock.setReserved(0);
            productStockRepository.save(stock);
            logger.info("Estoque inicializado para o produto ID: {}", event.getId());
        }
    }
}