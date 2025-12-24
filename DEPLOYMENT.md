# AWS Deployment Guide

This guide provides step-by-step instructions for deploying the Inventory Service to AWS using ECS (Elastic Container Service).

## Prerequisites

- AWS Account with appropriate IAM permissions
- AWS CLI installed and configured
- Docker installed locally
- Application tested and working locally

## Architecture Overview

```
┌─────────────┐
│   Route 53  │
│     DNS     │
└──────┬──────┘
       │
┌──────▼──────────┐
│ Application     │
│ Load Balancer   │
└──────┬──────────┘
       │
┌──────▼──────────┐
│   ECS Service   │
│  (Auto-scaling) │
└──────┬──────────┘
       │
┌──────▼──────────┐     ┌─────────────┐
│   ECS Tasks     │────▶│  RDS        │
│ (Containers)    │     │ PostgreSQL  │
└─────────────────┘     └─────────────┘
       │
       │
┌──────▼──────────┐
│   Amazon MQ     │
│   (RabbitMQ)    │
└─────────────────┘
```

## Step 1: Create RDS PostgreSQL Instance

### Using AWS Console

1. Navigate to RDS → Create database
2. Choose PostgreSQL 16
3. Select production template
4. Configure:
   - DB instance identifier: `inventory-db`
   - Master username: `postgres`
   - Master password: (store in Secrets Manager)
   - DB instance class: `db.t3.micro` (or larger for production)
   - Storage: 20 GB SSD
   - Enable storage autoscaling
5. Connectivity:
   - VPC: Select your VPC
   - Public access: No
   - VPC security group: Create new (allow port 5432 from ECS security group)
6. Create database

### Using AWS CLI

```bash
aws rds create-db-instance \
    --db-instance-identifier inventory-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 16.1 \
    --master-username postgres \
    --master-user-password <your-secure-password> \
    --allocated-storage 20 \
    --vpc-security-group-ids sg-xxxxxxxx \
    --db-subnet-group-name my-db-subnet-group \
    --backup-retention-period 7 \
    --no-publicly-accessible
```

## Step 2: Create Amazon MQ (RabbitMQ)

### Using AWS Console

1. Navigate to Amazon MQ → Create brokers
2. Select RabbitMQ
3. Configure:
   - Broker name: `inventory-rabbitmq`
   - Broker instance type: `mq.t3.micro`
   - Deployment mode: Single-instance (or Active/standby for production)
   - Username: `admin`
   - Password: (store in Secrets Manager)
4. Network and security:
   - VPC: Select your VPC
   - Subnet(s): Select private subnets
   - Security group: Allow port 5672 from ECS
5. Create broker

## Step 3: Create ECR Repository

```bash
# Create repository
aws ecr create-repository \
    --repository-name inventory-service \
    --region us-east-1

# Get repository URI (save this)
aws ecr describe-repositories \
    --repository-names inventory-service \
    --query 'repositories[0].repositoryUri' \
    --output text
```

## Step 4: Build and Push Docker Image

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build image
docker build -t inventory-service:latest .

# Tag image
docker tag inventory-service:latest \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com/inventory-service:latest

# Push to ECR
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/inventory-service:latest
```

## Step 5: Create ECS Cluster

```bash
aws ecs create-cluster \
    --cluster-name inventory-cluster \
    --capacity-providers FARGATE FARGATE_SPOT \
    --default-capacity-provider-strategy \
        capacityProvider=FARGATE,weight=1 \
        capacityProvider=FARGATE_SPOT,weight=4
```

## Step 6: Create Task Definition

Create `task-definition.json`:

```json
{
  "family": "inventory-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::<account-id>:role/ecsTaskRole",
  "containerDefinitions": [
    {
      "name": "inventory-service",
      "image": "<account-id>.dkr.ecr.us-east-1.amazonaws.com/inventory-service:latest",
      "portMappings": [
        {
          "containerPort": 8081,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },
        {
          "name": "SERVER_PORT",
          "value": "8081"
        },
        {
          "name": "JPA_DDL_AUTO",
          "value": "validate"
        }
      ],
      "secrets": [
        {
          "name": "DATABASE_URL",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:inventory/db-url"
        },
        {
          "name": "DATABASE_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:inventory/db-username"
        },
        {
          "name": "DATABASE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:inventory/db-password"
        },
        {
          "name": "RABBITMQ_HOST",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<account-id>:secret:inventory/rabbitmq-host"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/inventory-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

Register task definition:

```bash
aws ecs register-task-definition \
    --cli-input-json file://task-definition.json
```

## Step 7: Create Application Load Balancer

```bash
# Create ALB
aws elbv2 create-load-balancer \
    --name inventory-alb \
    --subnets subnet-xxxxxx subnet-yyyyyy \
    --security-groups sg-xxxxxxxx \
    --scheme internet-facing \
    --type application

# Create target group
aws elbv2 create-target-group \
    --name inventory-tg \
    --protocol HTTP \
    --port 8081 \
    --vpc-id vpc-xxxxxxxx \
    --target-type ip \
    --health-check-path /actuator/health \
    --health-check-interval-seconds 30 \
    --health-check-timeout-seconds 5 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3

# Create listener
aws elbv2 create-listener \
    --load-balancer-arn <alb-arn> \
    --protocol HTTP \
    --port 80 \
    --default-actions Type=forward,TargetGroupArn=<target-group-arn>
```

## Step 8: Create ECS Service

```bash
aws ecs create-service \
    --cluster inventory-cluster \
    --service-name inventory-service \
    --task-definition inventory-service:1 \
    --desired-count 2 \
    --launch-type FARGATE \
    --platform-version LATEST \
    --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxxx,subnet-yyyyyy],securityGroups=[sg-xxxxxxxx],assignPublicIp=DISABLED}" \
    --load-balancers "targetGroupArn=<target-group-arn>,containerName=inventory-service,containerPort=8081" \
    --health-check-grace-period-seconds 60
```

## Step 9: Configure Auto-Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --resource-id service/inventory-cluster/inventory-service \
    --scalable-dimension ecs:service:DesiredCount \
    --min-capacity 2 \
    --max-capacity 10

# Create scaling policy
aws application-autoscaling put-scaling-policy \
    --service-namespace ecs \
    --resource-id service/inventory-cluster/inventory-service \
    --scalable-dimension ecs:service:DesiredCount \
    --policy-name cpu-scaling-policy \
    --policy-type TargetTrackingScaling \
    --target-tracking-scaling-policy-configuration file://scaling-policy.json
```

`scaling-policy.json`:
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

## Step 10: Configure CloudWatch Logs

```bash
# Create log group
aws logs create-log-group \
    --log-group-name /ecs/inventory-service

# Set retention
aws logs put-retention-policy \
    --log-group-name /ecs/inventory-service \
    --retention-in-days 30
```

## Step 11: Store Secrets in Secrets Manager

```bash
# Database URL
aws secretsmanager create-secret \
    --name inventory/db-url \
    --secret-string "jdbc:postgresql://<rds-endpoint>:5432/inventory_db"

# Database username
aws secretsmanager create-secret \
    --name inventory/db-username \
    --secret-string "postgres"

# Database password
aws secretsmanager create-secret \
    --name inventory/db-password \
    --secret-string "<your-secure-password>"

# RabbitMQ host
aws secretsmanager create-secret \
    --name inventory/rabbitmq-host \
    --secret-string "<amazon-mq-endpoint>"
```

## Verification

### Check Service Status

```bash
aws ecs describe-services \
    --cluster inventory-cluster \
    --services inventory-service
```

### Check Task Health

```bash
aws ecs list-tasks \
    --cluster inventory-cluster \
    --service-name inventory-service

aws ecs describe-tasks \
    --cluster inventory-cluster \
    --tasks <task-arn>
```

### Test Application

```bash
# Get ALB DNS name
aws elbv2 describe-load-balancers \
    --names inventory-alb \
    --query 'LoadBalancers[0].DNSName' \
    --output text

# Test health endpoint
curl http://<alb-dns-name>/actuator/health

# Test API
curl http://<alb-dns-name>/api/inventory/check/1
```

## Monitoring

### CloudWatch Dashboards

Create a dashboard to monitor:
- ECS service CPU/Memory utilization
- ALB request count and latency
- RDS connections and performance
- Application-specific metrics from Prometheus endpoint

### CloudWatch Alarms

```bash
# High CPU alarm
aws cloudwatch put-metric-alarm \
    --alarm-name inventory-high-cpu \
    --alarm-description "Alert when CPU exceeds 80%" \
    --metric-name CPUUtilization \
    --namespace AWS/ECS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --evaluation-periods 2

# Unhealthy target alarm
aws cloudwatch put-metric-alarm \
    --alarm-name inventory-unhealthy-targets \
    --alarm-description "Alert when targets are unhealthy" \
    --metric-name UnHealthyHostCount \
    --namespace AWS/ApplicationELB \
    --statistic Average \
    --period 60 \
    --threshold 1 \
    --comparison-operator GreaterThanOrEqualToThreshold \
    --evaluation-periods 2
```

## Cost Optimization

- Use Fargate Spot for non-critical workloads (up to 70% savings)
- Enable RDS auto-scaling for storage
- Use reserved capacity for predictable workloads
- Implement proper auto-scaling to avoid over-provisioning
- Use CloudWatch Logs Insights instead of exporting all logs

## Security Best Practices

1. **Never hardcode credentials** - Use Secrets Manager
2. **Use IAM roles** for ECS tasks
3. **Enable encryption** for RDS and secrets
4. **Use private subnets** for ECS tasks and RDS
5. **Implement WAF** on ALB for production
6. **Enable VPC Flow Logs** for network monitoring
7. **Regular security updates** - rebuild and redeploy images

## Troubleshooting

### Service Won't Start

```bash
# Check service events
aws ecs describe-services \
    --cluster inventory-cluster \
    --services inventory-service \
    --query 'services[0].events'

# Check task logs
aws logs tail /ecs/inventory-service --follow
```

### Health Checks Failing

- Verify security groups allow traffic on port 8081
- Check application logs for startup errors
- Ensure RDS and RabbitMQ are accessible from ECS tasks
- Verify health check path is correct

### Database Connection Issues

- Check RDS security group allows connections from ECS security group
- Verify DATABASE_URL format is correct
- Ensure RDS is in same VPC as ECS tasks
- Check Secrets Manager permissions

## Continuous Deployment

For CI/CD pipeline integration:

1. Build and test in CI pipeline
2. Push image to ECR with git commit SHA as tag
3. Update ECS task definition with new image
4. Deploy new task definition to ECS service
5. Monitor deployment and rollback if needed

Example GitHub Actions workflow available in `.github/workflows/deploy.yml`

## Additional Resources

- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS RDS Documentation](https://docs.aws.amazon.com/rds/)
- [Amazon MQ Documentation](https://docs.aws.amazon.com/amazon-mq/)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/)
