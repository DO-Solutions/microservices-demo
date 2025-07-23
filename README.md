# Microservices Demo for DigitalOcean

This project is a DigitalOcean-specific adaptation of Google's [Online Boutique](https://github.com/GoogleCloudPlatform/microservices-demo) microservices demo application. It is designed to showcase the deployment and integration of DigitalOcean Kubernetes Service (DOKS) and Database-as-a-Service (DBaaS) using a Helm-based deployment model.

Solutions Architects can use this project for testing, demos, and internal enablement around Kubernetes, CI/CD, and managed services on DigitalOcean.

---

## Overview

**Online Boutique** is a polyglot microservices-based e-commerce application. It consists of more than 10 services written in various languages that communicate over gRPC. This fork provides Helm-based configuration that supports both quick development deployment as well as more production-like deployments using managed database services.

One of the included services, `loadgenerator`, simulates realistic user behavior by continuously generating traffic to the application. This helps showcase log output, observability, and performance under load.

---

## Features

* 12 microservices written in Go, Python, Java, Node.js, and C#
* Fully containerized and deployable via Helm
* Supports both ephemeral (in-cluster) and managed (external) databases
* Load generator service to simulate real user activity
* Container and Helm chart versions published to GitHub Container Registry (GHCR)
* Automated CI/CD with shared versioning and Helm chart packaging

---

## Deployment Options

### Prerequisites

* A Kubernetes cluster (e.g. [DOKS](https://docs.digitalocean.com/products/kubernetes/))
* `kubectl` configured to your cluster
* `helm`

### Option 1: Development Mode

In development mode (`devDeployment: true`), the chart provisions dependencies (PostgreSQL and Valkey) using Bitnami subcharts. This is ideal for quick demos or testing.

```sh
helm install demo oci://ghcr.io/do-solutions/microservices-demo \
  --namespace boutique --create-namespace
```

Once deployed, retrieve the external IP for the frontend service:

```sh
kubectl get svc frontend-external -n boutique -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

Then navigate to `http://<EXTERNAL_IP>`.

### Option 2: Production-Like Mode

In production-like mode (`devDeployment: false`), the chart expects you to provide externally managed database connection details. This is ideal for showcasing DBaaS integrations.

Required Kubernetes resources **must be created ahead of time**:

* **Adservice**

    * `ConfigMap` named `adservice-database-configuration` with the following keys:

      ```yaml
      DB_HOST: <hostname>
      DB_PORT: "5432"
      DB_USER: <username>
      DB_NAME: adservice
      DB_SSL_MODE: disable
      ```
    * `Secret` named `adservice-database` with a base64-encoded `postgres-password` key:

      ```yaml
      apiVersion: v1
      kind: Secret
      metadata:
        name: adservice-database
        namespace: boutique
      type: Opaque
      data:
        postgres-password: <base64-encoded-password>
      ```

* **Cartservice**

    * `Secret` named `cartservice-database` with a `connectionString` key containing the Valkey/Redis endpoint:

      ```yaml
      apiVersion: v1
      kind: Secret
      metadata:
        name: cartservice-database
        namespace: boutique
      type: Opaque
      stringData:
        connectionString: <host>:6379
      ```

Install with:

```sh
helm install demo oci://ghcr.io/do-solutions/microservices-demo \
  --namespace boutique --create-namespace \
  --set devDeployment=false
```

---

## Configuration

Configuration is handled via Helm `values.yaml`. Key toggles include:

* `devDeployment`: Enables or disables in-cluster dependencies

See the chart's `values.yaml` for all available configuration options.

---

## CI/CD and Versioning

All Docker images and Helm charts are built and published via GitHub Actions:

* Workflow triggers on changes to `src/**` or `helm/**` on the `main` branch
* A shared version tag is generated in the format `v1.YYYYMMDD.HHMMSSmmm`
* All images are tagged with the shared version and `latest`, and pushed to GHCR:
  `ghcr.io/do-solutions/microservices-demo-<service>:<tag>`
* The Helm chart is also packaged and pushed to GHCR as an OCI chart:
  `oci://ghcr.io/do-solutions`

This ensures all containers and the chart are consistently versioned and reproducible.

---

## Attribution

This project is a derivative work of Google's [microservices-demo](https://github.com/GoogleCloudPlatform/microservices-demo),  licensed under the Apache License 2.0. See the [NOTICE](./NOTICE) file for details.
