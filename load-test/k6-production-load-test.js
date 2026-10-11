// =============================================================================
// k6-production-load-test.js — QuickBite Full Load Test Suite
//
// TRƯỚC KHI CHẠY (trên VPS): bash infra/scripts/loadtest-mode.sh on   (nới rate-limit Nginx + Gateway)
// setup() TỰ tạo: Brand, Category, Product(1 SKU duy nhất FLASH-<runId>), Inventory(STOCK), USERS customer + địa chỉ mặc định.
// => KHÔNG bắt buộc chuẩn bị bằng Postman. Mọi VU tranh cùng 1 SKU. ID/token lấy từ response, không hard-code.
// SAU KHI XONG (trên VPS): bash infra/scripts/loadtest-mode.sh off
//
// CHẠY SMOKE TEST:
//   k6 run -e BASE_URL=https://khoatla-ecommerce.duckdns.org -e ADMIN_PASSWORD=YourAdminPass -e SCENARIO=smoke load-test/k6-production-load-test.js
//
// CHẠY FLASH SALE 100 RPS:
//   k6 run -e BASE_URL=https://khoatla-ecommerce.duckdns.org -e ADMIN_PASSWORD=YourAdminPass -e SCENARIO=ramp100 -e STOCK=100 load-test/k6-production-load-test.js
//
// CHẠY FLASH SALE 500 RPS:
//   k6 run -e BASE_URL=https://khoatla-ecommerce.duckdns.org -e ADMIN_PASSWORD=YourAdminPass -e SCENARIO=ramp500 -e STOCK=100 load-test/k6-production-load-test.js
//
// CHẠY FLASH SALE 1000 RPS:
//   k6 run -e BASE_URL=https://khoatla-ecommerce.duckdns.org -e ADMIN_PASSWORD=YourAdminPass -e SCENARIO=ramp1000 -e STOCK=100 load-test/k6-production-load-test.js
// =============================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ─── Environment Variables ───────────────────────────────────────────────────
const BASE     = __ENV.BASE_URL      || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO      || 'smoke';
const STOCK    = Number(__ENV.STOCK  || 100);
const USERS    = Number(__ENV.USERS  || 50);
const RATE     = Number(__ENV.RATE   || 50);      // chi dung khi SCENARIO=custom
const HOLD     = __ENV.HOLD          || '60s';    // chi dung khi SCENARIO=custom
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;
if (!ADMIN_PASSWORD) { throw new Error('Thieu -e ADMIN_PASSWORD=... (= ADMIN_DEFAULT_PASSWORD trong .env tren VPS)'); }   // số user pre-created trong setup()

// ─── Custom Metrics ──────────────────────────────────────────────────────────
const ordersCreated        = new Counter('orders_created');
const ordersSoldOut        = new Counter('orders_sold_out');   // 400/409 = hết hàng (hợp lệ)
const ordersRateLimited    = new Counter('orders_rate_limited'); // 429
const ordersError5xx       = new Counter('orders_error_5xx');
const orderLatency         = new Trend('order_latency_ms', true);
const loginLatency         = new Trend('login_latency_ms', true);
const successRate          = new Rate('success_rate');

// ─── Scenarios Configuration ─────────────────────────────────────────────────
const SCENARIO_CONFIGS = {
  smoke: {
    scenarios: {
      smoke: {
        executor: 'constant-vus',
        vus: 5,
        duration: '30s',
      },
    },
    thresholds: {
      http_req_duration: ['p(95)<3000'],
      success_rate: ['rate>0.95'],
    },
  },

  // Muc tai TUY CHON theo nang luc VPS thuc te:  -e SCENARIO=custom -e RATE=50 [-e HOLD=60s]
  // (RATE = so request /api/orders moi giay o muc on dinh; ramp len 20s, giu HOLD, ha 10s)
  custom: {
    scenarios: {
      flash_sale: {
        executor: 'ramping-arrival-rate',
        startRate: Math.max(1, Math.floor(RATE / 5)),
        timeUnit: '1s',
        preAllocatedVUs: Math.max(20, RATE),
        maxVUs: Math.max(100, RATE * 4),
        stages: [
          { target: RATE, duration: '20s' },
          { target: RATE, duration: HOLD },
          { target: 0,    duration: '10s' },
        ],
      },
    },
    thresholds: {
      http_req_duration:    ['p(95)<3000', 'p(99)<8000'],
      orders_error_5xx:     ['count<20'],
      success_rate:         ['rate>0.90'],
    },
  },

  ramp100: {
    scenarios: {
      flash_sale: {
        executor: 'ramping-arrival-rate',
        startRate: 10,
        timeUnit: '1s',
        preAllocatedVUs: 150,
        maxVUs: 500,
        stages: [
          { target: 100, duration: '20s' },   // ramp up
          { target: 100, duration: '60s' },   // steady state
          { target: 0,   duration: '10s' },   // ramp down
        ],
      },
    },
    thresholds: {
      http_req_duration:    ['p(95)<2000', 'p(99)<5000'],
      orders_error_5xx:     ['count<10'],
      orders_rate_limited:  ['count<100'],
      success_rate:         ['rate>0.90'],
    },
  },

  ramp500: {
    scenarios: {
      flash_sale: {
        executor: 'ramping-arrival-rate',
        startRate: 50,
        timeUnit: '1s',
        preAllocatedVUs: 500,
        maxVUs: 1500,
        stages: [
          { target: 500, duration: '30s' },
          { target: 500, duration: '90s' },
          { target: 0,   duration: '10s' },
        ],
      },
    },
    thresholds: {
      http_req_duration:    ['p(95)<3000', 'p(99)<8000'],
      orders_error_5xx:     ['count<50'],
      success_rate:         ['rate>0.85'],
    },
  },

  ramp1000: {
    scenarios: {
      flash_sale: {
        executor: 'ramping-arrival-rate',
        startRate: 100,
        timeUnit: '1s',
        preAllocatedVUs: 800,
        maxVUs: 2000,
        stages: [
          { target: 1000, duration: '30s' },
          { target: 1000, duration: '90s' },
          { target: 0,    duration: '10s' },
        ],
      },
    },
    thresholds: {
      http_req_duration:    ['p(95)<5000', 'p(99)<10000'],
      orders_error_5xx:     ['count<200'],
      success_rate:         ['rate>0.80'],
    },
  },
};

// setup() dang ky + dang nhap USERS khach hang (BCrypt) tren VPS 1 vCPU rat cham -> mac dinh k6 chi cho 60s se bi
// "setup() execution timed out". Nang len 15 phut.
export const options = {
  ...(SCENARIO_CONFIGS[SCENARIO] || SCENARIO_CONFIGS.smoke),
  setupTimeout: '15m',
  teardownTimeout: '2m',
};

// ─── Helpers ─────────────────────────────────────────────────────────────────
const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

function post(path, body, token) {
  const opts = token ? authHeaders(token) : { headers: JSON_HEADERS };
  return http.post(`${BASE}${path}`, JSON.stringify(body), opts);
}

function get(path, token) {
  const opts = token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  return http.get(`${BASE}${path}`, opts);
}

// ─── Setup: Tạo dữ liệu test (chạy 1 lần trước khi test) ────────────────────
export function setup() {
  console.log(`\n=== SETUP: Chuẩn bị dữ liệu test (BASE=${BASE}, SCENARIO=${SCENARIO}, STOCK=${STOCK}) ===`);

  // 1. Login admin
  const loginStart = Date.now();
  const adminLoginRes = post('/api/users/login', {
    username: 'admin',
    password: ADMIN_PASSWORD,
  });
  loginLatency.add(Date.now() - loginStart);

  if (adminLoginRes.status !== 200) {
    console.error(`❌ Admin login thất bại: ${adminLoginRes.status} ${adminLoginRes.body}`);
    throw new Error('Admin login failed — kiểm tra ADMIN_PASSWORD');
  }
  const adminData = adminLoginRes.json('data');
  const adminToken = adminData.accessToken;
  console.log(`✅ Admin login OK, userId=${adminData.id}`);

  // 2. Tạo Brand, Category, Product, Inventory với SKU định danh cho run này
  const runId = Date.now().toString(36).toUpperCase();
  const sku   = `FLASH-${runId}`;

  const brandRes = post('/api/catalog/brands', {
    name: `K6 Brand ${runId}`,
    logoUrl: 'https://example.com/logo.png',
    description: 'Load test brand',
  }, adminToken);
  if (!check(brandRes, { 'create brand 201': r => r.status === 201 })) {
    throw new Error(`Create brand failed: ${brandRes.status}`);
  }
  const brandId = brandRes.json('data.id');

  const catRes = post('/api/catalog/categories', {
    name: `K6 Category ${runId}`,
    description: 'Load test category',
    parentId: null,
  }, adminToken);
  if (!check(catRes, { 'create category 201': r => r.status === 201 })) {
    throw new Error(`Create category failed: ${catRes.status}`);
  }
  const categoryId = catRes.json('data.id');

  const productRes = post('/api/catalog/products', {
    name: `Flash Sale Product ${runId}`,
    description: 'K6 load test product — DO NOT DELETE manually',
    basePrice: 100000,
    categoryId,
    brandId,
    images: 'https://example.com/product.jpg',
    status: 'ACTIVE',
    variants: [{
      sku,
      price: 100000,
      stockQuantity: STOCK,
      attributes: '{"color":"Red","size":"M"}',
      imageUrl: 'https://example.com/variant.jpg',
    }],
  }, adminToken);
  if (!check(productRes, { 'create product 201': r => r.status === 201 })) {
    throw new Error(`Create product failed: ${productRes.status} ${productRes.body}`);
  }
  console.log(`✅ Product tạo OK, SKU=${sku}`);

  // 3. Khởi tạo tồn kho
  const invRes = post('/api/inventory', {
    sku,
    productName: `Flash Sale Product ${runId}`,
    quantity: STOCK,
  }, adminToken);
  if (!check(invRes, { 'init inventory 200/201': r => r.status === 200 || r.status === 201 })) {
    console.warn(`⚠️ Init inventory: ${invRes.status} ${invRes.body} (có thể đã tồn tại)`);
  }
  const invData = invRes.json('data');
  console.log(`✅ Inventory OK: available=${invData ? invData.availableQty : '?'}`);

  // 4. Tạo USERS test users và lấy token + địa chỉ
  const users = [];
  const phoneBase = Date.now() % 100000000;
  const phoneFor = (i) => `09${String((phoneBase + i) % 100000000).padStart(8, '0')}`;
  for (let i = 0; i < USERS; i++) {
    const username = `k6u${runId}${i}`;
    const password = 'Password123!';
    const email    = `${username}@loadtest.example.com`;

    // Register (bỏ qua lỗi nếu đã tồn tại)
    post('/api/users/register', {
      username,
      password,
      email,
      fullName: `K6 User ${i}`,
      phone: phoneFor(i),
      role: 'CUSTOMER',
    });

    // Login
    const uLoginRes = post('/api/users/login', { username, password });
    if (uLoginRes.status !== 200) {
      console.warn(`⚠️ User ${username} login failed, skip`);
      continue;
    }
    const ud = uLoginRes.json('data');

    // Thêm địa chỉ
    post('/api/users/addresses', {
      recipientName: `K6 User ${i}`,
      phone: phoneFor(i),
      province: 'TP Ho Chi Minh',
      district: 'Quan 1',
      ward: 'Phuong Ben Nghe',
      addressDetail: `${i + 1} Test Street`,
      isDefault: true,
    }, ud.accessToken);

    users.push({ token: ud.accessToken, id: ud.id, username });
    sleep(0.05);
  }

  console.log(`✅ Setup hoàn tất: ${users.length} users, SKU=${sku}, stock=${STOCK}`);
  return { sku, users, adminToken, runId, stock: STOCK };
}

// ─── Main test function ───────────────────────────────────────────────────────
export default function (data) {
  const u = data.users[Math.floor(Math.random() * data.users.length)];
  if (!u) return;

  group('flash_sale_order', () => {
    const idempotencyKey = uuidv4();
    const start = Date.now();

    const r = post('/api/orders', {
      idempotencyKey,
      customerId: u.id,
      note: 'K6 load test order',
      items: [{ sku: data.sku, quantity: 1 }],
    }, u.token);

    orderLatency.add(Date.now() - start);

    if (r.status === 201 || r.status === 200) {
      ordersCreated.add(1);
      successRate.add(true);
    } else if (r.status === 409) {
      // 409 = InsufficientStockException (hết hàng) — kết quả HỢP LỆ khi nhiều người tranh 1 SKU
      ordersSoldOut.add(1);
      successRate.add(true);
    } else if (r.status === 400) {
      ordersError5xx.add(1);   // 400 = body/validation sai -> là LỖI của test, không phải hết hàng
      successRate.add(false);
      console.warn(`400 bat thuong: ${r.body ? r.body.substring(0, 200) : ''}`);
    } else if (r.status === 429) {
      ordersRateLimited.add(1);
      successRate.add(false);
    } else {
      ordersError5xx.add(1);
      successRate.add(false);
      console.warn(`❌ Unexpected status: ${r.status} | body: ${r.body ? r.body.substring(0, 200) : 'empty'}`);
    }

    check(r, {
      'not 5xx': x => x.status < 500,
      'responded': x => x.timings.duration < 10000,
    });
  });
}

// ─── Teardown: Verify kết quả ─────────────────────────────────────────────────
export function teardown(data) {
  sleep(3); // chờ async DB writes

  console.log('\n=== TEARDOWN: Kiểm tra kết quả ===');

  const invRes = get(`/api/inventory/${data.sku}`, data.adminToken);
  if (invRes.status !== 200) {
    console.error(`❌ Không lấy được inventory: ${invRes.status}`);
    return;
  }

  const inv = invRes.json('data');
  const available = inv.availableQty;
  const reserved  = inv.reservedQty;
  const total     = available + reserved;

  console.log(`\n${'='.repeat(60)}`);
  console.log(`SCENARIO  : ${SCENARIO}`);
  console.log(`SKU       : ${data.sku}`);
  console.log(`Stock ban đầu  : ${data.stock}`);
  console.log(`Available : ${available}`);
  console.log(`Reserved  : ${reserved}`);
  console.log(`Total     : ${total} (phải = ${data.stock})`);
  console.log(`\nKiểm tra OVERSELL:`);
  console.log(`  available >= 0    : ${available >= 0} ${available >= 0 ? '✅' : '❌ OVERSELL!'}`);
  console.log(`  available+reserved = stock: ${total === data.stock} ${total === data.stock ? '✅' : '❌ MISMATCH!'}`);
  console.log(`${'='.repeat(60)}\n`);

  // Kết luận
  const passOversell     = available >= 0;
  const passConsistency  = total === data.stock;

  if (passOversell && passConsistency) {
    console.log('🎉 PASS FUNCTIONAL: Không oversell, kho nhất quán');
  } else {
    console.error('❌ FAIL FUNCTIONAL: Có vấn đề với tồn kho!');
  }

  // Ghi note để verify thêm trên VPS
  console.log('\n=== VERIFY THÊM TRÊN VPS ===');
  console.log('# PostgreSQL — kiểm tra tồn kho:');
  console.log(`docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT sku, available_qty, reserved_qty, (available_qty+reserved_qty) as total FROM inventories WHERE sku='${data.sku}';"`);
  console.log('\n# Redis — kiểm tra lock còn tồn tại:');
  console.log(`docker exec redis redis-cli KEYS "*${data.sku}*"`);
  console.log('\n# Kafka — kiểm tra outbox:');
  console.log(`docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT status, count(*) FROM outbox_events GROUP BY status;"`);
  console.log('\n# Docker stats:');
  console.log('docker stats --no-stream');
}

// ─── Báo cáo tổng hợp (in ra màn hình + lưu JSON) ─────────────────────────────
export function handleSummary(data) {
  const m = data.metrics, v = (k, f) => (m[k] && m[k].values && m[k].values[f] !== undefined ? m[k].values[f] : 0);
  const out = [
    '',
    '================= KET QUA LOAD TEST =================',
    `Scenario            : ${SCENARIO}   Stock: ${STOCK}   Users: ${USERS}`,
    `Tong request        : ${v('http_reqs', 'count')}   RPS TB: ${v('http_reqs', 'rate').toFixed(1)}`,
    `Latency (ms)        : avg=${v('http_req_duration', 'avg').toFixed(0)}  p95=${v('http_req_duration', 'p(95)').toFixed(0)}  p99=${v('http_req_duration', 'p(99)').toFixed(0)}  max=${v('http_req_duration', 'max').toFixed(0)}`,
    `http_req_failed     : ${(v('http_req_failed', 'rate') * 100).toFixed(2)}%   (luu y: 409 het hang tinh la failed theo mac dinh k6)`,
    `success_rate (logic): ${(v('success_rate', 'rate') * 100).toFixed(2)}%`,
    `Don tao thanh cong  : ${v('orders_created', 'count')}`,
    `Het hang (409)      : ${v('orders_sold_out', 'count')}`,
    `Rate-limit (429)    : ${v('orders_rate_limited', 'count')}`,
    `Loi 5xx/400/khac    : ${v('orders_error_5xx', 'count')}`,
    `Dropped iterations  : ${v('dropped_iterations', 'count')}   (>0 = k6/VPS khong theo kip RPS muc tieu)`,
    '======================================================',
    'PASS LOAD chi khi: threshold k6 dat + 429=0 + 5xx~0 + dropped_iterations~0 + khong oversell (xem TEARDOWN) + khong OOMKilled tren VPS.',
  ].join('\n');
  return { stdout: out + '\n', [`k6-summary-${SCENARIO}.json`]: JSON.stringify(data, null, 2) };
}
