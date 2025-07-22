# microservices-demo

Online Boutique demo application tailored for deployment on DigitalOcean using Helm.

## Technical Details

This Helm chart deploys the Online Boutique, a 10+ microservices application. It is configured to be flexible for both development and production-like deployments.

### Deployment

The chart can be deployed directly from the OCI registry.

**Command to install:**
```bash
helm install demo oci://ghcr.io/do-solutions/microservices-demo
```

### Configuration

The deployment is controlled by the values in the `values.yaml` file. You can override these values during installation using the `--set` flag or by providing your own values file with `--values`.

#### Development vs. Production Deployments (`devDeployment`)

The most important configuration is the `devDeployment` flag, which controls how dependencies are handled.

*   `devDeployment: true` (Default): This mode is for development and testing. The chart will deploy local instances of dependencies like PostgreSQL (for `adservice`) and Valkey (for `cartservice`) as subcharts. This is the easiest way to get the application running.

*   `devDeployment: false`: This mode is for production-like environments where you manage your own databases. When set to `false`, the chart will not deploy the database subcharts. You are responsible for creating the necessary `ConfigMap` and `Secret` resources that provide connection details for each service before deploying the chart. For example, `adservice` requires a `ConfigMap` named `adservice-database-configuration` and a `Secret` named `adservice-database`.

To deploy in a production-like mode, you would run:
```bash
helm install demo oci://ghcr.io/do-solutions/microservices-demo --namespace boutique --set devDeployment=false
```

### Accessing the Application

By default, the `frontend` service is exposed via a Kubernetes `Service` of type `LoadBalancer`. To access the application, you need to get the external IP address of this service.

You can find the external IP by running:
```bash
kubectl get svc frontend-external -n boutique -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

Once you have the IP address, you can access the application by navigating to `http://<EXTERNAL_IP>` in your web browser.
