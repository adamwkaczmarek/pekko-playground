// =============================================================================
// k6 load test for the Pekko microservices playground
//
// Usage:
//   # Basic run (console output only):
//   k6 run load-test.js
//
//   # With Prometheus remote write (metrics visible in Grafana):
//   K6_PROMETHEUS_RW_SERVER_URL=http://<minikube-ip>:30090/api/v1/write \
//   K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=false \
//   K6_PROMETHEUS_RW_TREND_STATS=p(50),p(90),p(95),p(99),min,max,avg \
//     k6 run -o experimental-prometheus-rw load-test.js
//
// Environment variables:
//   FRONTEND_URL  — base URL of the frontend gateway (default: http://localhost:8080)
//   NUM_USERS     — number of users to register (default: 50)
// =============================================================================

import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
// ── Configuration ───────────────────────────────────────────────────────────

const BASE_URL  = __ENV.FRONTEND_URL || "http://localhost:8080";
const NUM_USERS = parseInt(__ENV.NUM_USERS || "50", 10);

const ITEMS = [
  "laptop", "phone", "tablet", "headphones", "keyboard",
  "mouse", "monitor", "camera", "speaker", "watch",
];

function randomItem() {
  return ITEMS[Math.floor(Math.random() * ITEMS.length)];
}

function randomBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Pre-generate user IDs so all scenarios share the same pool.
const userIds = new SharedArray("userIds", function () {
  const ids = [];
  for (let i = 1; i <= NUM_USERS; i++) {
    ids.push(`user-${String(i).padStart(4, "0")}`);
  }
  return ids;
});

// ── Scenarios ───────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    // Phase 1: register all users (runs once, fast)
    register_users: {
      executor: "shared-iterations",
      iterations: NUM_USERS,
      vus: 5,
      maxDuration: "2m",
      exec: "registerUser",
      gracefulStop: "10s",
      tags: { scenario: "register_users" },
    },

    // Phase 2: place orders (starts after registration completes)
    // With 50 users, 5 VUs, ~0.05s sleep → registration takes ~1s.
    // 2m grace period ensures all users exist before orders begin.
    place_orders: {
      executor: "shared-iterations",
      iterations: NUM_USERS,          // one iteration per user, each places 2-5 orders
      vus: 5,
      maxDuration: "5m",
      startTime: "2m",
      exec: "placeOrders",
      tags: { scenario: "place_orders" },
    },

    // Phase 3: continuous read/write traffic
    steady_traffic: {
      executor: "constant-vus",
      vus: 5,
      duration: "10m",
      startTime: "3m",               // starts after orders phase is well underway
      exec: "steadyTraffic",
      tags: { scenario: "steady_traffic" },
    },
  },

  thresholds: {
    "http_req_failed{scenario:steady_traffic}": ["rate<0.10"],  // <10% errors on steady traffic
    "http_req_duration{scenario:steady_traffic}": ["p(95)<3000"],  // p95 < 3s on steady traffic
  },
};

// ── Scenario functions ──────────────────────────────────────────────────────

// Phase 1: Register a single user
export function registerUser() {
  const idx    = __ITER % userIds.length;
  const userId = userIds[idx];

  const res = http.post(
    `${BASE_URL}/users/${userId}`,
    JSON.stringify({ name: `User ${idx + 1}`, email: `user${idx + 1}@example.com` }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: "POST /users" } },
  );

  check(res, {
    "user registered (201)": (r) => r.status === 201,
  });
  sleep(0.05);
}

// Phase 2: Place 2-5 orders for one user
export function placeOrders() {
  const idx    = __ITER % userIds.length;
  const userId = userIds[idx];
  const count  = randomBetween(2, 5);

  for (let i = 0; i < count; i++) {
    const payload = JSON.stringify({
      customerId: userId,
      products: [randomItem(), randomItem()],
    });

    const res = http.post(`${BASE_URL}/orders`, payload, {
      headers: { "Content-Type": "application/json" },
      tags: { endpoint: "POST /orders" },
    });

    check(res, {
      "order placed (201)": (r) => r.status === 201,
    });
    sleep(0.02);
  }
}

// Phase 3: Mixed read/write traffic
export function steadyTraffic() {
  // Poll a random user
  const userIdx = randomBetween(0, userIds.length - 1);
  const userId  = userIds[userIdx];

  const userRes = http.get(`${BASE_URL}/users/${userId}`, {
    tags: { endpoint: "GET /users" },
  });
  check(userRes, { "get user OK (200)": (r) => r.status === 200 });

  sleep(0.2);

  // Every ~5th iteration, place a new order to keep entities active
  if (Math.random() < 0.2) {
    const payload = JSON.stringify({
      customerId: userId,
      products: [randomItem()],
    });

    const orderRes = http.post(`${BASE_URL}/orders`, payload, {
      headers: { "Content-Type": "application/json" },
      tags: { endpoint: "POST /orders" },
    });
    check(orderRes, { "steady order placed (201)": (r) => r.status === 201 });

    if (orderRes.status === 201) {
      // Immediately read back the order
      try {
        const body    = JSON.parse(orderRes.body);
        const orderId = body.orderId;
        if (orderId) {
          const getOrder = http.get(`${BASE_URL}/orders/${orderId}`, {
            tags: { endpoint: "GET /orders" },
          });
          check(getOrder, { "get order OK (200)": (r) => r.status === 200 });
        }
      } catch (_) { /* response parse failed — skip */ }
    }
  }

  sleep(0.5);
}
