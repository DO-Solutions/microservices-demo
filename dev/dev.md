# Microservices Demo GitHub Actions Workflow

This document defines the desired GitHub Actions configuration for the `microservices-demo` repository under the `DO-Solutions` organization. It describes triggers, job structure, and image tagging conventions for all microservices.

---

## 1. Project Overview

* **Repository:** `microservices-demo`
* **Organization:** `DO-Solutions`
* **Services Directory:** `./src`
* **Microservices (11 total):**

    * adservice
    * cartservice
    * checkoutservice
    * currencyservice
    * emailservice
    * frontend
    * loadgenerator
    * paymentservice
    * productcatalogservice
    * recommendationservice
    * shippingservice
    * shoppingassistantservice

Each microservice has its own subdirectory under `src` and contains a `Dockerfile` to build its container image.

---

## 2. Workflow Objectives

1. **Trigger build** only when a commit has files change in a specific microservice directory.
2. **Build** the Docker image for the affected service using its `Dockerfile` located in its directory.
3. **Tag** the image according to the semver-like convention:

    * **Major version:** `v1` (static)
    * **Minor version:** current date in `YYYYMMDD` format
    * **Patch version:** current time in `HHMMSSfff` format (hour, minute, second, milliseconds)
    * **Example tag:** `v1.20250620.140230064`
4. **Publish** the image to GitHub Container Registry (GHCR) under the organization or repository container registry.

---

## 4. Key Points

* **Trigger Paths:** Only files under `src/<service>/**` trigger a build for that specific service.
* **Separate Jobs:** Each service has its own named job, conditionally triggered based on commit content.
* **Image Naming:** `ghcr.io/DO-Solutions/microservices-demo-<service>:<tag>`
* **Tagging Convention:** `v1.<YYYYMMDD>.<HHMMSSfff>` to ensure unique, chronological versioning.
* **Secrets Required:**

    * `GITHUB_TOKEN` (provided by default) for GHCR authentication.
