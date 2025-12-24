# Inventory Service - AWS Microservice

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Production-ready microservice for managing product inventory stock levels, built with Spring Boot and designed for AWS deployment.

## 📋 Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Docker Deployment](#docker-deployment)
- [AWS Deployment](#aws-deployment)
- [Monitoring](#monitoring)

## ✨ Features

- **RESTful API** for inventory management (check, increase, decrease stock)
- **Spring Security** with CORS configuration
- **PostgreSQL** database with JPA/Hibernate
- **RabbitMQ** integration for messaging
- **Docker** support with multi-stage builds
- **Comprehensive testing** (unit + integration tests)
- **Swagger/OpenAPI** documentation
- **Actuator** endpoints for health checks and metrics
- **Prometheus** metrics export
- **Environment-based configuration** (dev, prod, test)
- **Production-ready** with security, logging, and error handling

## 🔧 Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **Docker** and **Docker Compose** (for containerized deployment)
- **PostgreSQL 16** (if running locally without Docker)
- **RabbitMQ 3** (if running locally without Docker)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd inventory_aws
```

### 2. Run with Docker Compose (Recommended)

```bash
# Start all services (PostgreSQL, RabbitMQ, Inventory Service)
docker-compose up -d

# View logs
docker-compose logs -f inventory-service

# Stop all services
docker-compose down
```

The application will be available at:
- **API**: http://localhost:8081/api/inventory
- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **Health Check**: http://localhost:8081/actuator/health
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

### 3. Run Locally with Maven

```bash
# Start PostgreSQL and RabbitMQ first
docker-compose up -d inventory-db rabbitmq

# Run the application
./mvnw spring-boot:run

# Or with a specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## ⚙️ Configuration

### Environment Variables

All configuration can be customized via environment variables. See [`.env.example`](.env.example) for all available options.

**Key Variables:**

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Application port | `8081` |
| `DATABASE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5433/inventory_db` |
| `DATABASE_USERNAME` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | `postgres` |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `SPRING_PROFILES_ACTIVE` | Active profile (dev/prod/test) | `dev` |

### Profiles

- **`dev`**: Development profile with debug logging
- **`prod`**: Production profile with strict validation and minimal logging
- **test**: Test profile with H2 in-memory database

## 📚 API Documentation

### Swagger UI

Access interactive API documentation at: **http://localhost:8081/swagger-ui.html**

### Endpoints

#### Check Stock
```http
GET /api/inventory/check/{productId}
```
Returns `true` if product has available stock, `false` otherwise.

#### Increase Stock
```http
POST /api/inventory/increase?productId={id}&amount={amount}
```
Increases stock for a product. Creates product if it doesn't exist.

#### Decrease Stock
```http
POST /api/inventory/decrease?productId={id}&amount={amount}
```
Decreases stock for a product. Returns `false` if insufficient stock.

### Example Usage

```bash
# Check stock for product 1
curl http://localhost:8081/api/inventory/check/1

# Increase stock
curl -X POST "http://localhost:8081/api/inventory/increase?productId=1&amount=100"

# Decrease stock
curl -X POST "http://localhost:8081/api/inventory/decrease?productId=1&amount=30"
```

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test Classes

```bash
# Unit tests
./mvnw test -Dtest=ProductStockServiceTest

# Integration tests
./mvnw test -Dtest=InventoryControllerIntegrationTest
```

### Test Coverage

- **Unit Tests**: Service layer with mocked dependencies
- **Integration Tests**: Full Spring context with test database
- **Test Profile**: H2 in-memory database for fast execution

## 🐳 Docker Deployment

### Build Docker Image

```bash
docker build -t inventory-service:latest .
```

### Run with Docker

```bash
docker run -p 8081:8081 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5433/inventory_db \
  -e RABBITMQ_HOST=host.docker.internal \
  inventory-service:latest
```

### Docker Compose

The `compose.yaml` includes:
- **PostgreSQL** with persistent volume
- **RabbitMQ** with management UI
- **Inventory Service** with health checks

```bash
# Start full stack
docker-compose up -d

# Scale inventory service
docker-compose up -d --scale inventory-service=3

# View service status
docker-compose ps

# Stop and remove volumes
docker-compose down -v
```

## ☁️ AWS Deployment

### Prerequisites

1. AWS Account with appropriate permissions
2. AWS CLI configured
3. ECR repository created
4. RDS PostgreSQL instance
5. Amazon MQ (RabbitMQ) or self-hosted RabbitMQ

### Deployment Steps

#### 1. Push Image to ECR

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Tag image
docker tag inventory-service:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/inventory-service:latest

# Push to ECR
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/inventory-service:latest
```

#### 2. Configure Environment Variables

Set these in ECS task definition or environment:

```bash
DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/inventory_db
DATABASE_USERNAME=<rds-username>
DATABASE_PASSWORD=<rds-password>
RABBITMQ_HOST=<amazon-mq-endpoint>
SPRING_PROFILES_ACTIVE=prod
JPA_DDL_AUTO=validate
```

#### 3. Deploy to ECS

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for detailed AWS deployment guide including:
- RDS setup
- ECS cluster configuration
- Load balancer setup
- Auto-scaling configuration

## 📊 Monitoring

### Health Checks

```bash
# Liveness probe
curl http://localhost:8081/actuator/health/liveness

# Readiness probe
curl http://localhost:8081/actuator/health/readiness

# Full health details
curl http://localhost:8081/actuator/health
```

### Metrics

Prometheus metrics available at:
```
http://localhost:8081/actuator/prometheus
```

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health status |
| `/actuator/info` | Application information |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus-formatted metrics |

## 🏗️ Architecture

```
inventory-service/
├── src/
│   ├── main/
│   │   ├── java/com/sbaldasso/ecommerce_aws/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controllers/     # REST controllers
│   │   │   ├── entities/        # JPA entities
│   │   │   ├── exception/       # Exception handling
│   │   │   ├── repositories/    # Data repositories
│   │   │   └── services/        # Business logic
│   │   └── resources/
│   │       ├── application.yml       # Main configuration
│   │       ├── application-dev.yml   # Dev profile
│   │       └── application-prod.yml  # Prod profile
│   └── test/                    # Test classes
├── Dockerfile                   # Multi-stage Docker build
├── compose.yaml                 # Docker Compose configuration
└── pom.xml                      # Maven dependencies
```

## 🔒 Security

- **Spring Security** enabled with CORS configuration
- **Non-root user** in Docker container
- **Environment variables** for sensitive data
- **Input validation** on all endpoints
- **Global exception handling**

## 📝 License

This project is licensed under the MIT License.

## 👥 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Project Link: [https://github.com/samuelbaldasso/Java-Inventory-Microservice-AWS](https://github.com/samuelbaldasso/Java-Inventory-Microservice-AWS)
