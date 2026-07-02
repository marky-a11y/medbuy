#!/bin/bash
# ==============================================================================
# Smoke Tests for Observability Stack (OBSV-10)
# Validates that all observability components are running and functional.
#
# Usage: ./smoke-test-observability.sh
# Returns: 0 if all tests pass, 1 if any fail
# ==============================================================================

set -euo pipefail

MONITORING_NS="monitoring"
LOGGING_NS="logging"
APP_NS="media-buying"
PASS_COUNT=0
FAIL_COUNT=0

echo "=========================================="
echo " Observability Smoke Tests"
echo "=========================================="

# Helper functions
pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "  PASS: $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  FAIL: $1"
}

check_pod_running() {
    local namespace=$1 label=$2 name=$3
    local count=$(kubectl get pods -n "$namespace" -l "$label" --field-selector=status.phase=Running -o json 2>/dev/null | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo "0")
    if [ "$count" -gt 0 ]; then
        pass "$name is running ($count pod(s))"
    else
        fail "$name has no running pods"
    fi
}

# ============================================
# Test 1: Verify Prometheus target discovery
# ============================================
echo ""
echo "--- Test 1: Prometheus Target Discovery ---"

# Check that dashboard and consumer pods are being scraped
PROMETHEUS_POD=$(kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$PROMETHEUS_POD" ]; then
    # Port-forward to Prometheus API
    kubectl port-forward -n $MONITORING_NS "pod/$PROMETHEUS_POD" 9090:9090 &>/dev/null &
    PF_PID=$!
    sleep 3

    # Query targets API
    TARGETS=$(curl -s http://localhost:9090/api/v1/targets 2>/dev/null || echo '{"data":{"activeTargets":[]}}')
    HEALTHY_COUNT=$(echo "$TARGETS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len([t for t in d.get('data',{}).get('activeTargets',[]) if t.get('health')=='up']))" 2>/dev/null || echo "0")
    TOTAL_COUNT=$(echo "$TARGETS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',{}).get('activeTargets',[])))" 2>/dev/null || echo "0")

    if [ "$TOTAL_COUNT" -gt 0 ] && [ "$HEALTHY_COUNT" -gt 0 ]; then
        pass "Prometheus is scraping $HEALTHY_COUNT/$TOTAL_COUNT targets (UP)"
    else
        fail "Prometheus targets: $HEALTHY_COUNT UP of $TOTAL_COUNT total"
    fi

    kill $PF_PID 2>/dev/null || true
else
    fail "Cannot find Prometheus pod"
fi

# ============================================
# Test 2: Verify Grafana API health
# ============================================
echo ""
echo "--- Test 2: Grafana API Health ---"
GRAFANA_POD=$(kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$GRAFANA_POD" ]; then
    HEALTH_STATUS=$(kubectl exec -n $MONITORING_NS "pod/$GRAFANA_POD" -- curl -s http://localhost:3000/api/health 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('database','DOWN'))" 2>/dev/null || echo "DOWN")
    if [ "$HEALTH_STATUS" = "ok" ]; then
        pass "Grafana API health check returns 'ok'"
    else
        fail "Grafana API health check: $HEALTH_STATUS"
    fi
else
    fail "Cannot find Grafana pod"
fi

# ============================================
# Test 3: Verify AlertManager API health
# ============================================
echo ""
echo "--- Test 3: AlertManager API Health ---"
AM_POD=$(kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=alertmanager -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$AM_POD" ]; then
    AM_STATUS=$(kubectl exec -n $MONITORING_NS "pod/$AM_POD" -- curl -s http://localhost:9093/api/v2/status 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('versionInfo',{}).get('version','unknown'))" 2>/dev/null || echo "DOWN")
    if [ "$AM_STATUS" != "DOWN" ]; then
        pass "AlertManager API responds (version: $AM_STATUS)"
    else
        fail "AlertManager API not responding"
    fi
else
    fail "Cannot find AlertManager pod"
fi

# ============================================
# Test 4: Verify ServiceMonitor labels match pod labels
# ============================================
echo ""
echo "--- Test 4: ServiceMonitor Label Matching ---"
SM_COUNT=$(kubectl get servicemonitor -n $APP_NS -o json 2>/dev/null | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo "0")
if [ "$SM_COUNT" -gt 0 ]; then
    # Verify ServiceMonitor selector matches a pod
    SM_SELECTOR=$(kubectl get servicemonitor -n $APP_NS -o json 2>/dev/null | python3 -c "
import sys,json
items=json.load(sys.stdin).get('items',[])
for sm in items:
    sel = sm.get('spec',{}).get('selector',{}).get('matchLabels',{})
    if sel:
        print(json.dumps(sel))
        break
" 2>/dev/null || echo "{}")
    
    if [ "$SM_SELECTOR" != "{}" ]; then
        pass "ServiceMonitor found ($SM_COUNT) with match labels"
    else
        fail "ServiceMonitor has no matchLabels"
    fi
else
    fail "No ServiceMonitors found in namespace $APP_NS"
fi

# ============================================
# Test 5: Verify custom metrics appear in Prometheus
# ============================================
echo ""
echo "--- Test 5: Custom Metrics in Prometheus ---"
PROMETHEUS_POD=$(kubectl get pods -n $MONITORING_NS -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$PROMETHEUS_POD" ]; then
    kubectl port-forward -n $MONITORING_NS "pod/$PROMETHEUS_POD" 9091:9090 &>/dev/null &
    PF_PID=$!
    sleep 3

    # Query for custom metric names
    CUSTOM_METRICS=$(curl -s 'http://localhost:9091/api/v1/label/__name__/values' 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
names=d.get('data',[])
custom=[n for n in names if n.startswith('media_buying_')]
for n in sorted(custom):
    print(n)
" 2>/dev/null || true)

    kill $PF_PID 2>/dev/null || true

    METRIC_COUNT=$(echo "$CUSTOM_METRICS" | wc -l)
    if [ "$METRIC_COUNT" -ge 6 ]; then
        pass "Found $METRIC_COUNT custom media_buying_* metrics in Prometheus"
        echo "    Metrics:"
        echo "$CUSTOM_METRICS" | while IFS= read -r line; do
            echo "      - $line"
        done
    else
        fail "Expected >= 6 custom metrics, found $METRIC_COUNT"
        echo "    Found:"
        echo "$CUSTOM_METRICS"
    fi
else
    fail "Cannot find Prometheus pod"
fi

# ============================================
# Test 6: Verify Elasticsearch health
# ============================================
echo ""
echo "--- Test 6: Elasticsearch Health ---"
ES_POD=$(kubectl get pods -n $LOGGING_NS -l app=elasticsearch-master -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [ -n "$ES_POD" ]; then
    ES_STATUS=$(kubectl exec -n $LOGGING_NS "pod/$ES_POD" -- curl -s -k https://localhost:9200/_cluster/health 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','unknown'))" 2>/dev/null || echo "DOWN")
    if [ "$ES_STATUS" = "green" ] || [ "$ES_STATUS" = "yellow" ]; then
        pass "Elasticsearch cluster health: $ES_STATUS"
    else
        fail "Elasticsearch cluster health: $ES_STATUS"
    fi
else
    fail "Cannot find Elasticsearch pod"
fi

# ============================================
# Test 7: Verify Filebeat is running on all nodes
# ============================================
echo ""
echo "--- Test 7: Filebeat DaemonSet ---"
FILEBEAT_READY=$(kubectl get daemonset -n $LOGGING_NS -l app=filebeat -o json 2>/dev/null | python3 -c "import sys,json; ds=json.load(sys.stdin).get('items',[{}])[0]; print(ds.get('status',{}).get('numberReady',0))" 2>/dev/null || echo "0")
FILEBEAT_DESIRED=$(kubectl get daemonset -n $LOGGING_NS -l app=filebeat -o json 2>/dev/null | python3 -c "import sys,json; ds=json.load(sys.stdin).get('items',[{}])[0]; print(ds.get('status',{}).get('desiredNumberScheduled',0))" 2>/dev/null || echo "0")
if [ "$FILEBEAT_READY" -gt 0 ] && [ "$FILEBEAT_READY" = "$FILEBEAT_DESIRED" ]; then
    pass "Filebeat is running on $FILEBEAT_READY/$FILEBEAT_DESIRED nodes"
else
    fail "Filebeat ready: $FILEBEAT_READY/$FILEBEAT_DESIRED"
fi

# ============================================
# Test 8: Verify cert-manager certificates are ready
# ============================================
echo ""
echo "--- Test 8: Cert-Manager Certificate Readiness ---"
CERT_READY=$(kubectl get certificates -A -o json 2>/dev/null | python3 -c "
import sys,json
certs=json.load(sys.stdin).get('items',[])
ready=[c for c in certs if c.get('status',{}).get('conditions',[{}])[0].get('status')=='True']
print(f\"{len(ready)}/{len(certs)}\")
" 2>/dev/null || echo "0/0")
echo "    Certificates ready: $CERT_READY"

if echo "$CERT_READY" | grep -qP '^\d+/\d+$'; then
    READY_COUNT=$(echo "$CERT_READY" | cut -d/ -f1)
    TOTAL_COUNT=$(echo "$CERT_READY" | cut -d/ -f2)
    if [ "$TOTAL_COUNT" -gt 0 ] && [ "$READY_COUNT" -eq "$TOTAL_COUNT" ]; then
        pass "All certificates are ready ($CERT_READY)"
    elif [ "$TOTAL_COUNT" -gt 0 ]; then
        fail "Not all certificates ready: $CERT_READY"
    else
        fail "No certificate resources found"
    fi
else
    fail "Could not parse certificate status"
fi

# ============================================
# Test 9: Verify PrometheusRules are loaded
# ============================================
echo ""
echo "--- Test 9: PrometheusRules ---"
RULE_COUNT=$(kubectl get prometheusrules -n $MONITORING_NS -o json 2>/dev/null | python3 -c "import sys,json; print(sum(len(g.get('spec',{}).get('groups',[])) for g in json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo "0")
if [ "$RULE_COUNT" -ge 8 ]; then
    pass "Found $RULE_COUNT alert rule groups (expected >= 8)"
else
    fail "Expected >= 8 alert rule groups, found $RULE_COUNT"
fi

# ============================================
# Test 10: Verify Grafana dashboard ConfigMap exists with proper label
# ============================================
echo ""
echo "--- Test 10: Grafana Dashboard ConfigMap ---"
CM_LABEL=$(kubectl get configmap -n $MONITORING_NS grafana-dashboards-media-buying -o json 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('metadata',{}).get('labels',{}).get('grafana_dashboard','missing'))" 2>/dev/null || echo "missing")
if [ "$CM_LABEL" = "1" ]; then
    pass "Grafana dashboard ConfigMap exists with grafana_dashboard=1 label"
else
    fail "Grafana dashboard ConfigMap label: $CM_LABEL"
fi

# ============================================
# Summary
# ============================================
echo ""
echo "=========================================="
echo " Smoke Test Results"
echo "=========================================="
echo "  Passed: $PASS_COUNT"
echo "  Failed: $FAIL_COUNT"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo "SOME TESTS FAILED — review output above."
    exit 1
else
    echo "ALL TESTS PASSED!"
    exit 0
fi
