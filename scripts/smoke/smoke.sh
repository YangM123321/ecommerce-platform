#!/usr/bin/env bash
# Automated smoke test after every pipeline deploy (Session 6). Goes through the PUBLIC address,
# like a customer, and touches every service, whichever one was just deployed:
#   gateway health -> product-service (browse) -> user-service (admin login) -> order-service (my orders)
# Needs kubectl pointed at the cluster (to find the IP and read the admin login from the Secret), curl, jq.
set -euo pipefail

NS=ecommerce
ATTEMPTS=${SMOKE_ATTEMPTS:-30}   # x 10 s: services need a moment to (re)register with Eureka

ip=$(kubectl get ingress api-gateway -n "$NS" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
[ -n "$ip" ] || { echo "No public IP on the ingress"; exit 1; }
base="http://$ip"
email=$(kubectl get secret ecommerce-secrets -n "$NS" -o jsonpath='{.data.ADMIN_EMAIL}' | base64 -d)
password=$(kubectl get secret ecommerce-secrets -n "$NS" -o jsonpath='{.data.ADMIN_PASSWORD}' | base64 -d)

check() {
  # 1. gateway is up
  [ "$(curl -fsS "$base/actuator/health" | jq -r .status)" = "UP" ] || return 1
  # 2. product-service answers through the gateway
  [ "$(curl -fsS "$base/api/products?size=1" | jq -r .totalElements)" -ge 1 ] || return 1
  # 3. user-service issues a token
  token=$(curl -fsS -X POST "$base/api/auth/login" -H 'Content-Type: application/json' \
            -d "$(jq -n --arg e "$email" --arg p "$password" '{email:$e,password:$p}')" | jq -r .accessToken)
  [ -n "$token" ] && [ "$token" != "null" ] || return 1
  # 4. order-service answers for that token
  curl -fsS "$base/api/orders" -H "Authorization: Bearer $token" | jq -e '.content' > /dev/null || return 1
}

for i in $(seq 1 "$ATTEMPTS"); do
  if check; then
    echo "Smoke test passed on attempt $i: health UP, products, login, orders all OK at $base"
    exit 0
  fi
  echo "Attempt $i/$ATTEMPTS not ready yet, retrying in 10 s"
  sleep 10
done
echo "Smoke test FAILED at $base"
exit 1
