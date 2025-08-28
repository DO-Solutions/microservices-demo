# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a DigitalOcean-specific adaptation of Google's Online Boutique microservices demo application. It consists of 12+ microservices written in Go, Python, Java, Node.js, and C# that communicate over gRPC, designed to showcase DOKS (DigitalOcean Kubernetes Service) and Database-as-a-Service integrations.

## Repository Structure

- `src/` - Individual microservice implementations
- `helm/` - Helm chart for Kubernetes deployment
- `dev/` - Development configuration values
- `scratch/` - Temporary/scratch files

## Services Architecture

### Service Stack by Language:
- **Go**: frontend, checkoutservice, productcatalogservice, shippingservice
- **Java**: adservice (Gradle-based)
- **C#**: cartservice (.NET 9.0)
- **Node.js**: currencyservice, paymentservice
- **Python**: emailservice, recommendationservice, loadgenerator, shoppingassistantservice

### Key Service Dependencies:
- **adservice** requires PostgreSQL database
- **cartservice** requires Valkey/Redis database
- **frontend** serves the web UI and orchestrates other services
- **loadgenerator** simulates realistic user traffic

## Deployment Configuration

The application supports two deployment modes via Helm:
- **Development mode** (`devDeployment: true`): Uses in-cluster PostgreSQL and Valkey from Bitnami subcharts
- **Production mode** (`devDeployment: false`): Requires external managed database connections via ConfigMaps and Secrets

## Common Development Commands

### Kubernetes/Helm Operations
```bash
# Deploy in development mode
helm install demo oci://ghcr.io/do-solutions/microservices-demo \
  --namespace boutique --create-namespace

# Deploy with custom values
helm install demo oci://ghcr.io/do-solutions/microservices-demo \
  --namespace boutique --create-namespace \
  -f dev/values.yaml

# Package Helm chart locally
helm package ./helm --version <version> --app-version <tag>

# Get frontend external IP
kubectl get svc frontend-external -n boutique -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

### Building Individual Services
```bash
# Java (adservice)
cd src/adservice && ./gradlew build

# C# (cartservice)
cd src/cartservice && dotnet build

# Go services (multiple)
cd src/<service> && go build

# Node.js services
cd src/<service> && npm install

# Python services
cd src/<service> && pip install -r requirements.txt
```

### Running Tests
```bash
# Java tests
cd src/adservice && ./gradlew test

# C# tests
cd src/cartservice && dotnet test

# Go tests (where available)
cd src/<service> && go test ./...
```

## CI/CD System

The repository uses GitHub Actions with a unified build system:
- All services are built and tagged with the same version (`v1.YYYYMMDD.HHMMSSfff`)
- Docker images are published to GitHub Container Registry (GHCR)
- Helm chart is packaged and pushed as an OCI artifact
- Workflow triggers on changes to `src/**` or `helm/**` on the `main` branch

## External Database Configuration

For production deployments, create these Kubernetes resources before deployment:

### AdService PostgreSQL:
- ConfigMap: `adservice-database-configuration` with DB connection details
- Secret: `adservice-database` with `postgres-password` key

### CartService Valkey/Redis:
- Secret: `cartservice-database` with `connectionString` key

## Container Images

All service images are available at:
`ghcr.io/do-solutions/microservices-demo-<service>:<tag>`

Services are containerized with optimized Dockerfiles in each service directory.