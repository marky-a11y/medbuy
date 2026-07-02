#!/bin/bash
# ==============================================================================
# Setup Observability Stack (OBSV-01 through OBSV-11)
# Deploys the full monitoring, logging, and TLS stack for the Media Buying Dashboard.
#
# Usage: ./setup-observability.sh [dev|staging|prod]
#   Default: dev
# ==============================================================================

set -euo pipefail

ENVIRONMENT="${1:-dev}"
MONITORING_NS="monitoring"
LOGGING_NS="logging"
CERT_NS="cert-manager"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")/observability"
HELM_RELEASE="prometheus-stack"

echo "=========================================="
echo " Setting up Observability Stack"
echo " Environment: $ENVIRONMENT"
echo "=========================================="

# ---- Step 1: Create Namespaces ----
echo "--- Creating namespaces ---"
kubectl create namespace $MONITORING_NS --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace $LOGGING_NS --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace $CERT_NS --dry-run=client -o yaml | kubectl apply -f -

# ---- Step 2: Deploy kube-prometheus-stack ----
echo "--- Deploying kube-prometheus-stack (Prometheus + AlertManager + Grafana) ---"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo update

helm upgrade --install $HELM_RELEASE prometheus-community/kube-prometheus-stack \
    --namespace $MONITORING_NS \
    --version 60.0.0 \
    -f "$INFRA_DIR/prometheus-stack-values.yaml" \
    --wait \
    --timeout 15m

echo "  ✓ kube-prometheus-stack deployed"

# ---- Step 3: Apply PrometheusRule CRD (alerting rules) ----
echo "--- Applying PrometheusRule CRD (alerting rules) ---"
kubectl apply -f "$INFRA_DIR/prometheus-rules.yaml"
echo "  ✓ PrometheusRule applied"

# ---- Step 4: Deploy Elasticsearch + Kibana ----
echo "--- Deploying Elasticsearch + Kibana ---"
helm repo add elastic https://helm.elastic.co 2>/dev/null || true
helm repo update

# Check if Elasticsearch is already deployed
if ! kubectl get statefulset elasticsearch-master -n $LOGGING_NS &>/dev/null; then
    helm upgrade --install elasticsearch elastic/elasticsearch \
        --namespace $LOGGING_NS \
        --version 8.15.0 \
        -f "$INFRA_DIR/elk-stack-values.yaml" \
        --wait \
        --timeout 15m
    echo "  ✓ Elasticsearch deployed"

    helm upgrade --install kibana elastic/kibana \
        --namespace $LOGGING_NS \
        --version 8.15.0 \
        -f "$INFRA_DIR/elk-stack-values.yaml" \
        --wait \
        --timeout 10m
    echo "  ✓ Kibana deployed"
else
    echo "  Elasticsearch already deployed — skipping."
fi

# ---- Step 5: Deploy Filebeat DaemonSet ----
echo "--- Deploying Filebeat DaemonSet ---"
helm upgrade --install filebeat elastic/filebeat \
    --namespace $LOGGING_NS \
    --version 8.15.0 \
    -f "$INFRA_DIR/elk-stack-values.yaml" \
    --wait \
    --timeout 10m
echo "  ✓ Filebeat deployed"

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
    echo "  ✓ cert-manager deployed"
else
    echo "  cert-manager already deployed — skipping."
fi

# ---- Step 7: Apply ClusterIssuer + Certificates ----
echo "--- Applying cert-manager ClusterIssuer + Certificates ---"
kubectl apply -f "$INFRA_DIR/cert-manager-clusterissuer.yaml"
echo "  ✓ ClusterIssuer and Certificate resources applied"

# ---- Step 8: Create Grafana Dashboard ConfigMap ----
echo "--- Applying Grafana dashboard ConfigMap ---"
kubectl create configmap grafana-dashboards-media-buying \
    --namespace $MONITORING_NS \
    --from-file="$INFRA_DIR/grafana-dashboards/media-buying-dashboard.json" \
    --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap grafana-dashboards-media-buying \
    --namespace $MONITORING_NS \
    grafana_dashboard="1" \
    --overwrite
echo "  ✓ Grafana dashboard ConfigMap created with grafana_dashboard=1 label"

# ---- Step 9: Validate Deployment ----
echo ""
echo "=========================================="
echo " Validating Deployment"
echo "=========================================="

echo ""
echo "Namespaces:"
kubectl get ns | grep -E "$MONITORING_NS|$LOGGING_NS|$CERT_NS"

echo ""
echo "Prometheus pods:"
kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=prometheus -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "Grafana pods:"
kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=grafana -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "AlertManager pods:"
kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=alertmanager -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "Filebeat DaemonSet:"
kubectl get daemonset -n $LOGGING_NS -l app=filebeat -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "Elasticsearch pods:"
kubectl get pods -n $LOGGING_NS -l app=elasticsearch-master -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "Kibana pods:"
kubectl get pods -n $LOGGING_NS -l app=kibana -o wide 2>/dev/null || echo "  (not found)"

echo ""
echo "Certificates:"
kubectl get certificates -A 2>/dev/null || echo "  (none)"

echo ""
echo "PrometheusRules:"
kubectl get prometheusrules -n $MONITORING_NS 2>/dev/null || echo "  (none)"

echo ""
echo "ServiceMonitors:"
kubectl get servicemonitor -n media-buying 2>/dev/null || echo "  (none)"

echo ""
echo "=========================================="
echo " Observability Stack Deployed!"
echo "=========================================="
echo ""
echo "Access URLs:"
echo "  Grafana:       https://grafana.media-buying.autoresolve.com"
echo "  Kibana:        https://kibana.media-buying.autoresolve.com"
echo "  Dashboard:     https://dashboard.media-buying.autoresolve.com"
echo ""
echo "  Prometheus:    kubectl port-forward -n $MONITORING_NS svc/prometheus-operated 9090:9090"
echo "  AlertManager:  kubectl port-forward -n $MONITORING_NS svc/$HELM_RELEASE-kube-prom-alertmanager 9093:9093"
echo ""
echo "Credentials:"
echo "  Grafana:       admin / (see kube-prometheus-stack values or secret)"
echo "  Elasticsearch: elastic / (kubectl get secret elasticsearch-master-credentials -n $LOGGING_NS -o jsonpath='{.data.password}' | base64 -d)"
echo ""
echo "Next Steps:"
echo "  1. Login to Grafana → check 'Media Buying Dashboard - Application Metrics' auto-imported"
echo "  2. Login to Kibana → create index pattern 'media-buying-logs-*'"
echo "  3. Check Prometheus targets: kubectl port-forward ... → http://localhost:9090/targets"
echo "  4. Run smoke tests: ./smoke-test-observability.sh"
echo ""
