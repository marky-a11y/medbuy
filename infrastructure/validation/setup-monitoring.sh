#!/bin/bash
# ------------------------------------------------------------------------------
# Setup Monitoring Stack (INFRA-11 / OBSV-01 through OBSV-11)
# Deploys:
#   1. kube-prometheus-stack (Prometheus + AlertManager + Grafana)
#   2. ELK Stack (Elasticsearch + Kibana + Filebeat DaemonSet)
#   3. cert-manager + Let's Encrypt for TLS
#   4. Grafana dashboards as ConfigMaps
#   5. PrometheusRule CRD for alerting rules
#
# Usage: ./setup-monitoring.sh [dev|staging|prod]
#   Default: dev
# ------------------------------------------------------------------------------

set -euo pipefail

ENVIRONMENT="${1:-dev}"
MONITORING_NS="monitoring"
LOGGING_NS="logging"
CERT_NS="cert-manager"

echo "=========================================="
echo " Setting up Full Monitoring Stack"
echo " Environment: $ENVIRONMENT"
echo "=========================================="

# ---- Step 1: Create Namespaces ----
kubectl create namespace $MONITORING_NS --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace $LOGGING_NS --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace $CERT_NS --dry-run=client -o yaml | kubectl apply -f -

# ---- Step 2: Deploy kube-prometheus-stack ----
echo "--- Deploying kube-prometheus-stack (Prometheus + AlertManager + Grafana) ---"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo update

helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace $MONITORING_NS \
    --version 60.0.0 \
    -f infrastructure/observability/prometheus-stack-values.yaml \
    --wait \
    --timeout 15m

# ---- Step 3: Apply PrometheusRule CRD (alerting rules) ----
echo "--- Applying PrometheusRule CRD (alerting rules) ---"
kubectl apply -f infrastructure/observability/prometheus-rules.yaml

# ---- Step 4: Deploy ELK Stack ----
echo "--- Deploying Elasticsearch + Kibana ---"
helm repo add elastic https://helm.elastic.co 2>/dev/null || true
helm repo update

# Check if Elasticsearch is already deployed to avoid overwriting
if ! kubectl get statefulset elasticsearch-master -n $LOGGING_NS &>/dev/null; then
    helm upgrade --install elasticsearch elastic/elasticsearch \
        --namespace $LOGGING_NS \
        --version 8.15.0 \
        -f infrastructure/observability/elk-stack-values.yaml \
        --wait \
        --timeout 15m

    helm upgrade --install kibana elastic/kibana \
        --namespace $LOGGING_NS \
        --version 8.15.0 \
        -f infrastructure/observability/elk-stack-values.yaml \
        --wait \
        --timeout 10m
else
    echo "Elasticsearch already deployed — skipping."
fi

# ---- Step 5: Deploy Filebeat DaemonSet ----
echo "--- Deploying Filebeat DaemonSet ---"
helm upgrade --install filebeat elastic/filebeat \
    --namespace $LOGGING_NS \
    --version 8.15.0 \
    -f infrastructure/observability/elk-stack-values.yaml \
    --wait \
    --timeout 10m

# ---- Step 6: Deploy cert-manager + Let's Encrypt ----
echo "--- Deploying cert-manager ---"
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo update

if ! kubectl get deployment cert-manager -n $CERT_NS &>/dev/null; then
    helm upgrade --install cert-manager jetstack/cert-manager \
        --namespace $CERT_NS \
        --version v1.15.0 \
        --set installCRDs=true \
        --set 'extraArgs={--dns01-recursive-nameservers-only,--dns01-recursive-nameservers=8.8.8.8:53\,1.1.1.1:53}' \
        --wait \
        --timeout 10m
else
    echo "cert-manager already deployed — skipping."
fi

# ---- Step 7: Apply ClusterIssuer + Certificates ----
echo "--- Applying cert-manager ClusterIssuer + Certificates ---"
kubectl apply -f infrastructure/observability/cert-manager-clusterissuer.yaml

# ---- Step 8: Apply Grafana Dashboard ConfigMap ----
echo "--- Applying Grafana dashboard ConfigMap ---"
# Create ConfigMap from the dashboard JSON
kubectl create configmap grafana-dashboards-media-buying \
    --namespace $MONITORING_NS \
    --from-file=infrastructure/observability/grafana-dashboards/media-buying-dashboard.json \
    --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap grafana-dashboards-media-buying \
    --namespace $MONITORING_NS \
    grafana_dashboard="1" \
    --overwrite

# ---- Step 9: Validate Deployment ----
echo ""
echo "--- Validating Deployment ---"

echo "  Prometheus pods:"
kubectl get pods -n $MONITORING_NS -l app=prometheus -o wide 2>/dev/null || echo "  (not found)"

echo "  Grafana pods:"
kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=grafana -o wide 2>/dev/null || echo "  (not found)"

echo "  Filebeat pods:"
kubectl get pods -n $LOGGING_NS -l app=filebeat -o wide 2>/dev/null || echo "  (not found)"

echo "  Elasticsearch pods:"
kubectl get pods -n $LOGGING_NS -l app=elasticsearch-master -o wide 2>/dev/null || echo "  (not found)"

echo "  Certificates:"
kubectl get certificates -A 2>/dev/null || echo "  (none)"

echo ""
echo "=========================================="
echo " Monitoring Stack Deployed!"
echo "=========================================="
echo ""
echo "Access URLs:"
echo "  Grafana:       https://grafana.media-buying.autoresolve.com"
echo "  Kibana:        https://kibana.media-buying.autoresolve.com"
echo "  Dashboard:     https://dashboard.media-buying.autoresolve.com"
echo ""
echo "  Prometheus:    kubectl port-forward -n $MONITORING_NS svc/prometheus-operated 9090:9090"
echo "  AlertManager:  kubectl port-forward -n $MONITORING_NS svc/prometheus-stack-kube-prom-alertmanager 9093:9093"
echo ""
echo "Credentials:"
echo "  Grafana:       admin / (password from values/secret)"
echo "  Kibana:        elastic / (auto-generated, retrieve with:)"
echo "                 kubectl get secret elasticsearch-master-credentials -n $LOGGING_NS -o jsonpath='{.data.password}' | base64 -d"
echo ""
echo "Verification Steps:"
echo "  1. Login to Grafana → check 'Media Buying Dashboard' auto-imported"
echo "  2. Login to Kibana → create index pattern 'media-buying-logs-*'"
echo "  3. Check Prometheus targets: kubectl port-forward ... → http://localhost:9090/targets"
echo "  4. Test alert: temporarily set a low threshold to verify Slack + PagerDuty routing"
echo ""
