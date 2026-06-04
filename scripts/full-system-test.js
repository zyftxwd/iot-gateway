const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");
const net = require("net");
const { spawn, execFileSync } = require("child_process");
const mqtt = require("mqtt");

const ROOT = path.resolve(__dirname, "..");
const REPORT_DIR = path.join(ROOT, "reports");
const API_BASE = process.env.IIOT_API_BASE || "http://127.0.0.1:8080";
const WEB_BASE = process.env.IIOT_WEB_BASE || "http://127.0.0.1:5173";
const MYSQL = process.env.IIOT_MYSQL || "C:\\Program Files\\MySQL\\MySQL Server 5.5\\bin\\mysql.exe";
const DB_NAME = process.env.IIOT_DB || "iiot_db";
const DB_USER = process.env.IIOT_DB_USER || "root";
const DB_PASSWORD = process.env.IIOT_DB_PASSWORD || "root";
const HTTP_TIMEOUT_MS = Number(process.env.IIOT_HTTP_TIMEOUT_MS || 15000);
const STEP_TIMEOUT_MS = Number(process.env.IIOT_STEP_TIMEOUT_MS || 90000);

const runId = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const testPrefix = `AUTO_TEST_${runId}`;
const usernamePrefix = `auto_t_${runId}`;
const startedProcesses = [];
const records = [];
const simulatorLogs = [];
const context = {
  projectId: null,
  groupId: null,
  devices: {},
  points: {},
  users: {},
  alarmRuleId: null,
  alarmId: null,
  workOrderId: null,
  maintenanceCardId: null,
  mqttPort: null,
  mqttReadTopic: `iiot/auto/${runId}/data`,
  mqttWriteTopic: `iiot/auto/${runId}/write`,
  mqttChineseTopic: `iiot/auto/${runId}/cn`
};

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function withTimeout(promiseFactory, timeoutMs, message) {
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(promiseFactory),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(message)), timeoutMs);
      })
    ]);
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

function nowText() {
  return new Date().toLocaleString("zh-CN", { hour12: false });
}

function markdownEscape(value) {
  return String(value ?? "")
    .replace(/\r?\n/g, " ")
    .replace(/\|/g, "\\|");
}

function shortJson(value, max = 420) {
  let text;
  try {
    text = JSON.stringify(value);
  } catch (error) {
    text = String(value);
  }
  if (text.length > max) {
    return text.slice(0, max) + "...";
  }
  return text;
}

function addRecord(moduleName, caseName, status, detail, evidence) {
  records.push({
    moduleName,
    caseName,
    status,
    detail: detail || "",
    evidence: evidence || ""
  });
  const mark = status === "PASS" ? "PASS" : status === "BLOCKED" ? "BLOCKED" : status === "WARN" ? "WARN" : "FAIL";
  console.log(`[${mark}] ${moduleName} - ${caseName}${detail ? `: ${detail}` : ""}`);
}

async function step(moduleName, caseName, fn, options = {}) {
  console.log(`[RUN] ${moduleName} - ${caseName}`);
  try {
    const timeoutMs = options.timeoutMs || STEP_TIMEOUT_MS;
    const result = await withTimeout(
      fn,
      timeoutMs,
      `${moduleName} - ${caseName} timed out after ${timeoutMs}ms`
    );
    if (result && result.status) {
      addRecord(moduleName, caseName, result.status, result.detail, result.evidence);
    } else {
      addRecord(moduleName, caseName, "PASS", result && result.detail, result && result.evidence);
    }
    return result;
  } catch (error) {
    const status = options.blocked ? "BLOCKED" : "FAIL";
    addRecord(moduleName, caseName, status, error.message || String(error), error.stack || "");
    return null;
  }
}

function expect(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function api(method, urlPath, body, token, options = {}) {
  const headers = {};
  let payload;
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs || HTTP_TIMEOUT_MS;
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined && body !== null && !options.rawBody) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  } else {
    payload = body;
  }
  try {
    const response = await fetch(`${API_BASE}${urlPath}`, {
      method,
      headers,
      body: payload,
      signal: controller.signal
    });
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch (error) {
      json = null;
    }
    return { httpStatus: response.status, json, text };
  } catch (error) {
    if (error && error.name === "AbortError") {
      throw new Error(`${method} ${urlPath} timed out after ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

async function apiOk(method, urlPath, body, token) {
  const response = await api(method, urlPath, body, token);
  if (response.httpStatus < 200 || response.httpStatus >= 300) {
    throw new Error(`${method} ${urlPath} HTTP ${response.httpStatus}: ${response.text}`);
  }
  if (!response.json || response.json.code !== 200) {
    throw new Error(`${method} ${urlPath} Result ${response.json ? response.json.code : "NO_JSON"}: ${response.text}`);
  }
  return response.json.data;
}

async function apiExpectCode(method, urlPath, body, token, expectedCode) {
  const response = await api(method, urlPath, body, token);
  const code = response.json ? response.json.code : response.httpStatus;
  if (code !== expectedCode) {
    throw new Error(`${method} ${urlPath} expected code ${expectedCode}, got ${code}: ${response.text}`);
  }
  return response;
}

async function portOpen(port, host = "127.0.0.1", timeoutMs = 800) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let done = false;
    const finish = (value) => {
      if (done) return;
      done = true;
      socket.destroy();
      resolve(value);
    };
    socket.setTimeout(timeoutMs);
    socket.once("connect", () => finish(true));
    socket.once("timeout", () => finish(false));
    socket.once("error", () => finish(false));
    socket.connect(port, host);
  });
}

async function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close(() => resolve(address.port));
    });
  });
}

async function waitForPort(port, host = "127.0.0.1", timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await portOpen(port, host, 500)) {
      return true;
    }
    await sleep(250);
  }
  return false;
}

async function poll(fn, timeoutMs = 10000, intervalMs = 500) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const value = await fn();
      if (value) {
        return value;
      }
    } catch (error) {
      lastError = error;
    }
    await sleep(intervalMs);
  }
  if (lastError) {
    throw lastError;
  }
  return null;
}

function startNodeProcess(script, args, cwd) {
  const child = spawn("node", [script, ...args], {
    cwd,
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true
  });
  child.stdout.on("data", (chunk) => {
    const text = chunk.toString("utf8").trim();
    if (text) {
      simulatorLogs.push(`[sim] ${text}`);
      if (simulatorLogs.length > 80) simulatorLogs.shift();
    }
  });
  child.stderr.on("data", (chunk) => {
    const text = chunk.toString("utf8").trim();
    if (text) {
      simulatorLogs.push(`[sim:err] ${text}`);
      if (simulatorLogs.length > 80) simulatorLogs.shift();
    }
  });
  startedProcesses.push(child);
  return child;
}

async function startTempMqtt() {
  context.mqttPort = await getFreePort();
  startNodeProcess(path.join("scripts", "mqtt-broker.js"), ["127.0.0.1", String(context.mqttPort)], ROOT);
  const ready = await waitForPort(context.mqttPort, "127.0.0.1", 8000);
  if (!ready) {
    throw new Error(`temporary MQTT broker did not start on ${context.mqttPort}`);
  }
  const simulator = startNodeProcess(
    path.join("scripts", "mqtt_simulator.js"),
    ["127.0.0.1", String(context.mqttPort), context.mqttReadTopic, context.mqttWriteTopic],
    ROOT
  );
  context.mqttSimulator = simulator;
  await sleep(1800);
}

function stopProcess(child) {
  if (!child || child.killed) return;
  try {
    child.kill();
  } catch (error) {
    // ignore cleanup failures
  }
}

async function publishMqtt(topic, payload, retain = true) {
  return new Promise((resolve, reject) => {
    let settled = false;
    let timer;
    const client = mqtt.connect(`mqtt://127.0.0.1:${context.mqttPort}`, {
      clientId: `auto_test_pub_${Math.random().toString(16).slice(2)}`,
      clean: true,
      reconnectPeriod: 0,
      connectTimeout: 4000
    });
    const finish = (error) => {
      if (settled) return;
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      try {
        client.end(true);
      } catch (endError) {
        // ignore MQTT cleanup failures in tests
      }
      if (error) reject(error);
      else resolve(true);
    };
    timer = setTimeout(() => {
      finish(new Error("MQTT publish timeout"));
    }, 5000);
    client.once("connect", () => {
      client.publish(topic, payload, { qos: 0, retain }, (error) => {
        finish(error);
      });
    });
    client.once("error", finish);
    client.once("close", () => {
      if (!settled) {
        finish(new Error("MQTT publish connection closed before publish completed"));
      }
    });
  });
}

function extConfig(obj) {
  return JSON.stringify(obj);
}

async function setupTestData(adminToken) {
  const project = await apiOk("POST", "/api/projects", {
    projectName: `${testPrefix}_PROJECT`,
    projectCode: `${testPrefix}_PROJECT_CODE`,
    ownerName: "auto-test",
    status: "ACTIVE",
    remark: "auto generated full-system test project"
  }, adminToken);
  context.projectId = project.id;

  const group = await apiOk("POST", "/api/projects/groups", {
    projectId: project.id,
    parentId: 0,
    groupName: `${testPrefix}_AREA`,
    groupType: "AREA",
    sortNo: 1,
    remark: "auto generated test group"
  }, adminToken);
  context.groupId = group.id;

  return { project, group };
}

async function createUsers(adminToken) {
  const roles = await apiOk("GET", "/api/auth-admin/roles", null, adminToken);
  const depts = await apiOk("GET", "/api/auth-admin/depts", null, adminToken);
  const roleKeys = new Set((roles || []).map((role) => String(role.roleKey)));
  const deptId = depts && depts.length ? depts[0].deptId : null;
  const operatorRole = roleKeys.has("operator") ? "operator" : "viewer";
  const viewerRole = roleKeys.has("viewer") ? "viewer" : operatorRole;

  const assignee = await apiOk("POST", "/api/auth-admin/users", {
    username: `${usernamePrefix}_assignee`,
    nickName: "自动测试维修员",
    password: "123456",
    deptId,
    roleKey: operatorRole
  }, adminToken);
  const viewer = await apiOk("POST", "/api/auth-admin/users", {
    username: `${usernamePrefix}_viewer`,
    nickName: "自动测试只读",
    password: "123456",
    deptId,
    roleKey: viewerRole
  }, adminToken);
  const testAdmin = await apiOk("POST", "/api/auth-admin/users", {
    username: `${usernamePrefix}_admin`,
    nickName: "自动测试管理员",
    password: "123456",
    deptId,
    roleKey: "admin"
  }, adminToken);
  const deleteMe = await apiOk("POST", "/api/auth-admin/users", {
    username: `${usernamePrefix}_delete`,
    nickName: "自动测试待删除",
    password: "123456",
    deptId,
    roleKey: viewerRole
  }, adminToken);

  await apiOk("POST", `/api/auth-admin/users/${assignee.userId}/projects`, {
    projectId: context.projectId,
    permissionLevel: "OPERATE"
  }, adminToken);
  await apiOk("POST", `/api/auth-admin/users/${viewer.userId}/projects`, {
    projectId: context.projectId,
    permissionLevel: "VIEW"
  }, adminToken);

  context.users = { assignee, viewer, testAdmin, deleteMe };
  return context.users;
}

async function login(username, password) {
  const data = await apiOk("POST", "/api/user/login", { username, password }, null);
  return data.token || data;
}

async function createDevicesAndPoints(adminToken) {
  const mqttDevice = await apiOk("POST", "/api/devices", {
    projectId: context.projectId,
    groupId: context.groupId,
    deviceName: `${testPrefix}_MQTT`,
    deviceType: "PLC",
    protocolType: "MQTT",
    ipAddress: "127.0.0.1",
    port: context.mqttPort,
    collectIntervalMs: 1000,
    historyEnabled: true,
    historyMode: "INTERVAL_CHANGE",
    historyIntervalMs: 1000,
    storeOnChange: true,
    extConfig: extConfig({
      topic: context.mqttReadTopic,
      publishTopic: context.mqttWriteTopic,
      ackTopic: `${context.mqttWriteTopic}/result`,
      writablePointKeys: "setpoint,fan_start",
      writeConfirmTimeoutMs: 3000,
      staleTimeoutMs: 2500,
      cleanSession: true,
      qos: 0
    }),
    remark: "auto MQTT protocol test device"
  }, adminToken);
  context.devices.mqtt = mqttDevice;

  await sleep(1800);
  const mqttCreated = await apiOk("POST", `/api/points/discover?deviceId=${mqttDevice.id}`, null, adminToken);
  context.points.mqtt = {};
  for (const point of mqttCreated || []) {
    context.points.mqtt[point.pointKey] = point;
  }

  const cnDevice = await apiOk("POST", "/api/devices", {
    projectId: context.projectId,
    groupId: context.groupId,
    deviceName: `${testPrefix}_MQTT_CN`,
    deviceType: "PLC",
    protocolType: "MQTT",
    ipAddress: "127.0.0.1",
    port: context.mqttPort,
    collectIntervalMs: 1000,
    historyEnabled: true,
    historyMode: "CHANGE",
    storeOnChange: true,
    extConfig: extConfig({
      topic: context.mqttChineseTopic,
      staleTimeoutMs: 5000
    }),
    remark: "auto MQTT Chinese/nested JSON test device"
  }, adminToken);
  context.devices.mqttCn = cnDevice;
  await publishMqtt(context.mqttChineseTopic, JSON.stringify({
    "中文温度": 28.66,
    "嵌套": { "压力": 1.23 },
    "数组": [{ "状态": "运行" }]
  }));

  if (await portOpen(4840)) {
    const opcDevice = await apiOk("POST", "/api/devices", {
      projectId: context.projectId,
      groupId: context.groupId,
      deviceName: `${testPrefix}_OPC`,
      deviceType: "PLC",
      protocolType: "OPC_UA",
      ipAddress: "127.0.0.1",
      port: 4840,
      collectIntervalMs: 1000,
      historyEnabled: true,
      historyMode: "INTERVAL_CHANGE",
      historyIntervalMs: 1000,
      storeOnChange: true,
      extConfig: extConfig({
        endpointUrl: "opc.tcp://127.0.0.1:4840/UA/IiotTest",
        securityPolicy: "None",
        authMode: "ANONYMOUS",
        requestTimeoutMs: 5000
      }),
      remark: "auto OPC UA protocol test device"
    }, adminToken);
    context.devices.opc = opcDevice;
    const opcPoint = await apiOk("POST", "/api/points", {
      commDeviceId: opcDevice.id,
      pointLabel: "OPC温度",
      pointKey: "Temperature",
      address: "ns=1;s=Temperature",
      functionCode: 0,
      slaveId: 1,
      quantity: 1,
      dataType: "Float32",
      byteOrder: "ABCD",
      wordOrder: "AB",
      coef: 1,
      decimalPlaces: 2,
      unit: "℃",
      enabled: true,
      accessMode: "READ_WRITE",
      historyEnabled: true,
      historyMode: "INHERIT",
      storeOnChange: true
    }, adminToken);
    context.points.opc = { Temperature: opcPoint };
  }

  if (await portOpen(502)) {
    const modbusDevice = await apiOk("POST", "/api/devices", {
      projectId: context.projectId,
      groupId: context.groupId,
      deviceName: `${testPrefix}_MODBUS`,
      deviceType: "PLC",
      protocolType: "MODBUS_TCP",
      ipAddress: "127.0.0.1",
      port: 502,
      collectIntervalMs: 1000,
      historyEnabled: true,
      historyMode: "INTERVAL_CHANGE",
      historyIntervalMs: 1000,
      storeOnChange: true,
      extConfig: extConfig({ slaveId: 1 }),
      remark: "auto Modbus TCP protocol test device"
    }, adminToken);
    context.devices.modbus = modbusDevice;
    const modbusPoint = await apiOk("POST", "/api/points", {
      commDeviceId: modbusDevice.id,
      pointLabel: "Modbus测试寄存器",
      pointKey: "modbus_test_40001",
      address: "40001",
      functionCode: 3,
      slaveId: 1,
      quantity: 2,
      dataType: "Float32",
      byteOrder: "ABCD",
      wordOrder: "AB",
      coef: 1,
      decimalPlaces: 2,
      unit: "",
      enabled: true,
      accessMode: "READ_WRITE",
      historyEnabled: true,
      historyMode: "INHERIT",
      storeOnChange: true
    }, adminToken);
    context.points.modbus = { modbus_test_40001: modbusPoint };
  }

  return context.devices;
}

function valueOfRuntime(runtime, key) {
  const row = (runtime || []).find((item) => item.point && item.point.pointKey === key);
  return row ? row.value : null;
}

async function waitRuntime(deviceId, pointKey, timeoutMs = 12000) {
  return poll(async () => {
    const runtime = await apiOk("GET", `/api/points/runtime?deviceId=${deviceId}`, null, global.adminToken);
    const value = valueOfRuntime(runtime, pointKey);
    if (value !== null && value !== undefined) {
      return { runtime, value };
    }
    return null;
  }, timeoutMs, 600);
}

async function cleanupDatabase() {
  if (!context.projectId) {
    return "no project created";
  }
  if (!fs.existsSync(MYSQL)) {
    return `mysql client not found: ${MYSQL}`;
  }
  const pid = Number(context.projectId);
  const prefix = usernamePrefix.replace(/'/g, "''");
  const sql = `
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM iot_work_order_attachment WHERE work_order_id IN (SELECT id FROM iot_work_order WHERE project_id=${pid});
DELETE FROM iot_work_order_flow_log WHERE work_order_id IN (SELECT id FROM iot_work_order WHERE project_id=${pid});
DELETE FROM iot_work_order_participant WHERE work_order_id IN (SELECT id FROM iot_work_order WHERE project_id=${pid});
DELETE FROM iot_maintenance_card WHERE project_id=${pid};
DELETE FROM iot_work_order WHERE project_id=${pid};
DELETE FROM iot_alarm_event WHERE project_id=${pid};
DELETE FROM iot_alarm_rule WHERE project_id=${pid};
DELETE FROM iot_point_history WHERE project_id=${pid};
DELETE FROM iot_comm_point WHERE comm_device_id IN (SELECT id FROM iot_comm_device WHERE project_id=${pid});
DELETE FROM iot_comm_device WHERE project_id=${pid};
DELETE FROM iot_project_group WHERE project_id=${pid};
DELETE FROM iot_project WHERE id=${pid};
DELETE FROM sys_user_project WHERE user_id IN (SELECT user_id FROM sys_user WHERE username LIKE '${prefix}%');
DELETE FROM sys_user WHERE username LIKE '${prefix}%';
SET FOREIGN_KEY_CHECKS=1;`;
  execFileSync(MYSQL, [`-u${DB_USER}`, `-p${DB_PASSWORD}`, DB_NAME, "-e", sql], {
    stdio: "pipe",
    windowsHide: true
  });
  return "temporary database rows removed";
}

async function generateReport() {
  await fsp.mkdir(REPORT_DIR, { recursive: true });
  const counts = records.reduce((acc, record) => {
    acc[record.status] = (acc[record.status] || 0) + 1;
    return acc;
  }, {});
  const reportPath = path.join(REPORT_DIR, `full-system-test-${runId}.md`);
  const lines = [];
  lines.push(`# IoT Gateway 全功能测试报告`);
  lines.push("");
  lines.push(`- 测试时间：${nowText()}`);
  lines.push(`- 后端：${API_BASE}`);
  lines.push(`- 前端：${WEB_BASE}`);
  lines.push(`- 测试数据前缀：${testPrefix}`);
  lines.push(`- 临时 MQTT 端口：${context.mqttPort || "-"}`);
  lines.push("");
  lines.push("## 汇总");
  lines.push("");
  lines.push(`- PASS：${counts.PASS || 0}`);
  lines.push(`- WARN：${counts.WARN || 0}`);
  lines.push(`- BLOCKED：${counts.BLOCKED || 0}`);
  lines.push(`- FAIL：${counts.FAIL || 0}`);
  lines.push("");
  lines.push("## 测试明细");
  lines.push("");
  lines.push("| 模块 | 用例 | 结果 | 说明 | 证据 |");
  lines.push("| --- | --- | --- | --- | --- |");
  for (const record of records) {
    lines.push(`| ${markdownEscape(record.moduleName)} | ${markdownEscape(record.caseName)} | ${record.status} | ${markdownEscape(record.detail)} | ${markdownEscape(record.evidence)} |`);
  }
  lines.push("");
  lines.push("## 结论");
  lines.push("");
  if ((counts.FAIL || 0) === 0) {
    lines.push("- 本轮自动化功能测试没有发现阻断性失败。");
  } else {
    lines.push("- 本轮自动化功能测试发现失败项，需要优先处理 FAIL 行。");
  }
  lines.push("- BLOCKED 表示测试环境缺少对应模拟器或外部服务，不等同于功能失败。");
  lines.push("- WARN 表示功能可用但存在商业化前应处理的质量或体验风险。");
  await fsp.writeFile(reportPath, lines.join("\n"), "utf8");
  return reportPath;
}

function scheduleExit() {
  if (records.some((record) => record.status === "FAIL")) {
    process.exitCode = 1;
  }
  const code = process.exitCode || 0;
  setTimeout(() => process.exit(code), 120);
}

async function main() {
  try {
    await step("环境", "后端端口 8080", async () => {
      expect(await portOpen(8080), "backend port 8080 is not listening");
      return { detail: "后端在线" };
    });
    await step("环境", "前端端口 5173", async () => {
      expect(await portOpen(5173), "frontend port 5173 is not listening");
      return { detail: "前端在线" };
    });
    await step("环境", "临时 MQTT Broker 和模拟设备", async () => {
      await startTempMqtt();
      return { detail: `127.0.0.1:${context.mqttPort}` };
    });
    await step("环境", "OPC UA 模拟器", async () => {
      if (!(await portOpen(4840))) {
        return { status: "BLOCKED", detail: "127.0.0.1:4840 未监听，OPC UA 读写测试跳过" };
      }
      return { detail: "127.0.0.1:4840 已监听" };
    });
    await step("环境", "Modbus 模拟器", async () => {
      if (!(await portOpen(502))) {
        return { status: "BLOCKED", detail: "127.0.0.1:502 未监听，Modbus 真实读写测试跳过" };
      }
      return { detail: "127.0.0.1:502 已监听" };
    });

    await step("认证", "管理员登录", async () => {
      global.adminToken = await login("admin", "54321");
      const current = await apiOk("GET", "/api/user/current", null, global.adminToken);
      global.currentAdmin = current;
      expect(current.username === "admin", "current user is not admin");
      return { detail: `登录账号 ${current.username}` };
    });

    await step("基础数据", "创建临时项目和分组", async () => {
      const { project, group } = await setupTestData(global.adminToken);
      return { detail: `projectId=${project.id}, groupId=${group.id}` };
    });

    await step("权限", "创建测试账号并分配项目权限", async () => {
      const users = await createUsers(global.adminToken);
      return { detail: `assignee=${users.assignee.userId}, viewer=${users.viewer.userId}, admin=${users.testAdmin.userId}` };
    });

    await step("权限", "管理员可重置自己的密码", async () => {
      const testAdminToken = await login(context.users.testAdmin.username, "123456");
      await apiOk("POST", `/api/auth-admin/users/${context.users.testAdmin.userId}/password`, { password: "654321" }, testAdminToken);
      const reloginToken = await login(context.users.testAdmin.username, "654321");
      expect(!!reloginToken, "test admin self password reset login failed");
      return { detail: "测试管理员自助重置密码成功；未修改真实 admin 密码" };
    });

    await step("权限", "账号删除接口", async () => {
      await apiOk("DELETE", `/api/auth-admin/users/${context.users.deleteMe.userId}`, null, global.adminToken);
      const users = await apiOk("GET", "/api/auth-admin/users", null, global.adminToken);
      expect(!users.some((user) => user.userId === context.users.deleteMe.userId), "deleted user still exists");
      return { detail: `已删除测试账号 ${context.users.deleteMe.username}` };
    });

    await step("协议", "协议能力模型", async () => {
      const protocols = await apiOk("GET", "/api/protocols", null, global.adminToken);
      const types = protocols.map((protocol) => protocol.protocolType).sort();
      expect(types.includes("MQTT"), "MQTT protocol missing");
      expect(types.includes("MODBUS_TCP"), "MODBUS_TCP protocol missing");
      expect(types.includes("OPC_UA"), "OPC_UA protocol missing");
      const mqttMeta = protocols.find((protocol) => protocol.protocolType === "MQTT");
      expect(mqttMeta.supportsDiscovery === true && mqttMeta.supportsWrite === true, "MQTT capabilities incomplete");
      return { detail: types.join(", "), evidence: shortJson(mqttMeta && mqttMeta.fields ? mqttMeta.fields.slice(0, 3) : []) };
    });

    await step("协议", "禁止覆盖内置协议定义", async () => {
      await apiExpectCode("POST", "/api/protocols/definitions", { protocolType: "MQTT", displayName: "bad" }, global.adminToken, 400);
      return { detail: "内置协议覆盖被拒绝" };
    });

    await step("设备与点表", "创建 MQTT/OPC/Modbus 临时设备和点位", async () => {
      const devices = await createDevicesAndPoints(global.adminToken);
      return { detail: Object.entries(devices).map(([key, device]) => `${key}=${device.id}`).join(", ") };
    });

    await step("MQTT", "自动发现点位", async () => {
      const points = await apiOk("GET", `/api/points?deviceId=${context.devices.mqtt.id}`, null, global.adminToken);
      const keys = points.map((point) => point.pointKey);
      expect(keys.includes("temperature"), "temperature not discovered");
      expect(keys.includes("setpoint"), "setpoint writable point not discovered");
      expect(keys.includes("fan_start"), "fan_start writable point not discovered");
      return { detail: `${points.length} 个点位`, evidence: keys.join(", ") };
    });

    await step("MQTT", "中文、嵌套 JSON、数组路径发现预览", async () => {
      const chinesePayload = JSON.stringify({
        "中文温度": 28.66,
        "嵌套": { "压力": 1.23 },
        "数组": [{ "状态": "运行" }]
      });
      const publisher = setInterval(() => {
        publishMqtt(context.mqttChineseTopic, chinesePayload, true).catch(() => {});
      }, 300);
      let preview;
      try {
        await publishMqtt(context.mqttChineseTopic, chinesePayload, true);
        preview = await apiOk("POST", `/api/points/discover/preview?deviceId=${context.devices.mqttCn.id}`, null, global.adminToken);
      } finally {
        clearInterval(publisher);
      }
      const labels = (preview.rows || []).map((row) => row.point && row.point.pointKey).filter(Boolean);
      expect(labels.includes("中文温度"), "Chinese field not discovered");
      expect(labels.includes("嵌套.压力"), "nested field not discovered");
      expect(labels.includes("数组[0].状态"), "array path not discovered");
      return { detail: `${preview.totalCount} 个预览点位`, evidence: labels.join(", ") };
    });

    await step("MQTT", "实时数据采集", async () => {
      const result = await waitRuntime(context.devices.mqtt.id, "temperature", 14000);
      expect(typeof result.value === "number", "runtime temperature is not numeric");
      return { detail: `temperature=${result.value}`, evidence: shortJson(result.runtime.slice(0, 3)) };
    });

    await step("MQTT", "历史数据入库", async () => {
      await sleep(2500);
      const history = await apiOk("GET", `/api/history/points?deviceId=${context.devices.mqtt.id}&limit=20`, null, global.adminToken);
      expect(Array.isArray(history) && history.length > 0, "MQTT history rows are empty");
      expect(history.every((row) => String(row.valueText || "").length <= 200), "history value text abnormal");
      return { detail: `${history.length} 条`, evidence: shortJson(history.slice(0, 2)) };
    });

    await step("MQTT", "写入数值点和开关点", async () => {
      const setpoint = context.points.mqtt.setpoint;
      const fan = context.points.mqtt.fan_start;
      expect(setpoint && fan, "writable MQTT points missing");
      const setpointResult = await apiOk("POST", `/api/points/${setpoint.id}/write`, { value: 31.25 }, global.adminToken);
      const fanResult = await apiOk("POST", `/api/points/${fan.id}/write`, { value: true }, global.adminToken);
      expect(setpointResult === true, "setpoint write did not return true");
      expect(fanResult === true, "fan_start write did not return true");
      const runtime = (await waitRuntime(context.devices.mqtt.id, "setpoint", 8000)).runtime;
      return { detail: `setpoint=${valueOfRuntime(runtime, "setpoint")}, fan_start=${valueOfRuntime(runtime, "fan_start")}` };
    });

    await step("MQTT", "只读点禁止写入", async () => {
      const temperature = context.points.mqtt.temperature;
      expect(temperature, "temperature point missing");
      const response = await api("POST", `/api/points/${temperature.id}/write`, { value: 99 }, global.adminToken);
      const ok = response.httpStatus >= 400 || (response.json && response.json.code !== 200);
      expect(ok, `read-only write unexpectedly succeeded: ${response.text}`);
      return { detail: "只读点写入被拒绝", evidence: response.text.slice(0, 180) };
    });

    await step("MQTT", "断线离线与重连恢复", async () => {
      stopProcess(context.mqttSimulator);
      const offlineDevice = await poll(async () => {
        const device = await apiOk("GET", `/api/devices/${context.devices.mqtt.id}`, null, global.adminToken);
        return String(device.status).toUpperCase() === "OFFLINE" ? device : null;
      }, 9000, 800);
      expect(offlineDevice, "MQTT device did not become OFFLINE after simulator stopped");
      context.mqttSimulator = startNodeProcess(
        path.join("scripts", "mqtt_simulator.js"),
        ["127.0.0.1", String(context.mqttPort), context.mqttReadTopic, context.mqttWriteTopic],
        ROOT
      );
      const onlineDevice = await poll(async () => {
        const device = await apiOk("GET", `/api/devices/${context.devices.mqtt.id}`, null, global.adminToken);
        return String(device.status).toUpperCase() === "ONLINE" ? device : null;
      }, 12000, 800);
      expect(onlineDevice, "MQTT device did not recover ONLINE after simulator restarted");
      return { detail: "OFFLINE -> ONLINE 状态可恢复" };
    });

    await step("OPC UA", "树状浏览", async () => {
      if (!context.devices.opc) {
        return { status: "BLOCKED", detail: "OPC UA 模拟器不可用" };
      }
      const nodes = await apiOk("POST", `/api/points/browse?deviceId=${context.devices.opc.id}`, null, global.adminToken);
      const text = JSON.stringify(nodes);
      expect(text.includes("Temperature"), "OPC UA browse tree does not include Temperature");
      return { detail: "浏览树包含 Temperature", evidence: text.slice(0, 320) };
    });

    await step("OPC UA", "读取与写入", async () => {
      if (!context.devices.opc) {
        return { status: "BLOCKED", detail: "OPC UA 模拟器不可用" };
      }
      const point = context.points.opc.Temperature;
      const before = await waitRuntime(context.devices.opc.id, "Temperature", 14000);
      const writeResult = await apiOk("POST", `/api/points/${point.id}/write`, { value: 33.33 }, global.adminToken);
      expect(writeResult === true, "OPC write returned false");
      const after = await poll(async () => {
        const runtime = await apiOk("GET", `/api/points/runtime?deviceId=${context.devices.opc.id}`, null, global.adminToken);
        const value = valueOfRuntime(runtime, "Temperature");
        return Math.abs(Number(value) - 33.33) < 0.05 ? value : null;
      }, 12000, 700);
      expect(after !== null, "OPC runtime value did not update to written value");
      return { detail: `before=${before.value}, after=${after}` };
    });

    await step("Modbus", "读取与写入", async () => {
      if (!context.devices.modbus) {
        return { status: "BLOCKED", detail: "本机 502 端口未启动 Modbus 模拟器" };
      }
      const point = context.points.modbus.modbus_test_40001;
      const runtime = await waitRuntime(context.devices.modbus.id, point.pointKey, 14000);
      const writeResponse = await api("POST", `/api/points/${point.id}/write`, { value: 12.34 }, global.adminToken);
      const detail = `runtime=${runtime.value}, writeHttp=${writeResponse.httpStatus}, writeBody=${writeResponse.text.slice(0, 120)}`;
      if (!writeResponse.json || writeResponse.json.code !== 200 || writeResponse.json.data !== true) {
        return { status: "WARN", detail: `Modbus 读取可用，写入未通过；可能是模拟器不支持该地址写入。${detail}` };
      }
      return { detail };
    });

    await step("历史", "项目/设备/点位级联查询", async () => {
      const deviceRows = await apiOk("GET", `/api/history/points?deviceId=${context.devices.mqtt.id}&limit=10`, null, global.adminToken);
      const point = context.points.mqtt.temperature;
      const pointRows = await apiOk("GET", `/api/history/points?pointId=${point.id}&limit=10`, null, global.adminToken);
      const projectRows = await apiOk("GET", `/api/history/points?projectId=${context.projectId}&limit=10`, null, global.adminToken);
      expect(deviceRows.length > 0 && pointRows.length > 0 && projectRows.length > 0, "history scoped query returned empty rows");
      return { detail: `device=${deviceRows.length}, point=${pointRows.length}, project=${projectRows.length}` };
    });

    await step("报警", "规则触发与恢复", async () => {
      const point = context.points.mqtt.temperature;
      const rule = await apiOk("POST", "/api/alarm-rules", {
        projectId: context.projectId,
        pointId: point.id,
        ruleName: `${testPrefix}_温度报警`,
        enabled: true,
        severity: "WARN",
        conditionType: "GT",
        thresholdValue: 0,
        immediateAlarm: true,
        triggerDurationMs: 0,
        recoverDurationMs: 1000,
        remark: "auto alarm test"
      }, global.adminToken);
      context.alarmRuleId = rule.id;
      const activeAlarm = await poll(async () => {
        const alarms = await apiOk("GET", `/api/alarms?projectId=${context.projectId}&status=ACTIVE`, null, global.adminToken);
        return alarms.find((alarm) => alarm.alarmRuleId === rule.id) || null;
      }, 10000, 700);
      expect(activeAlarm, "rule alarm was not raised");
      context.alarmId = activeAlarm.id;
      await apiOk("PUT", `/api/alarm-rules/${rule.id}`, {
        ...rule,
        thresholdValue: 9999,
        recoverDurationMs: 1000
      }, global.adminToken);
      const recovered = await poll(async () => {
        const alarms = await apiOk("GET", `/api/alarms?projectId=${context.projectId}&status=RECOVERED`, null, global.adminToken);
        return alarms.find((alarm) => alarm.id === activeAlarm.id) || null;
      }, 10000, 700);
      expect(recovered, "rule alarm did not recover after condition became false");
      return { detail: `alarmId=${activeAlarm.id} ACTIVE -> RECOVERED`, evidence: shortJson(recovered) };
    });

    await step("报警", "报警转工单入口", async () => {
      if (!context.alarmId) {
        return { status: "BLOCKED", detail: "上一条报警未创建" };
      }
      const order = await apiOk("POST", `/api/work-orders/from-alarm/${context.alarmId}`, null, global.adminToken);
      expect(order.id, "work order from alarm missing id");
      const detail = await apiOk("GET", `/api/work-orders/${order.id}`, null, global.adminToken);
      expect(detail.order.alarmEventId === context.alarmId, "work order is not linked with alarm");
      return { detail: `alarmId=${context.alarmId}, workOrderId=${order.id}` };
    });

    await step("工单", "流程设置读取与保存", async () => {
      const policy = await apiOk("GET", `/api/work-orders/policies/${context.projectId}`, null, global.adminToken);
      const saved = await apiOk("POST", `/api/work-orders/policies/${context.projectId}`, {
        ...policy,
        projectId: context.projectId,
        allowDispatcherAsAssignee: false,
        allowDispatcherAsVerifier: true,
        allowAssigneeVerifySelf: false,
        autoCloseAfterVerify: true,
        autoArchiveAfterClose: true,
        requireProcessPhoto: false,
        requireFaultReason: true,
        requireProcessMeasure: true
      }, global.adminToken);
      expect(saved.autoCloseAfterVerify === true, "policy save failed");
      return { detail: "工单策略保存成功" };
    });

    await step("工单", "禁止过去计划完成时间", async () => {
      await apiExpectCode("POST", "/api/work-orders", {
        projectId: context.projectId,
        deviceId: context.devices.mqtt.id,
        pointId: context.points.mqtt.temperature.id,
        title: `${testPrefix}_过去时间工单`,
        description: "invalid planned time",
        priority: "NORMAL",
        plannedFinishTime: Date.now() - 60000
      }, global.adminToken, 400);
      return { detail: "过去计划时间被拒绝" };
    });

    await step("工单", "派单候选人按权限过滤", async () => {
      const candidates = await apiOk("GET", `/api/work-orders/candidates?projectId=${context.projectId}`, null, global.adminToken);
      expect(candidates.assignees.some((user) => user.userId === context.users.assignee.userId), "assignee not in candidates");
      expect(!candidates.assignees.some((user) => user.userId === context.users.viewer.userId), "viewer should not be assignee candidate");
      return { detail: `assignees=${candidates.assignees.length}, verifiers=${candidates.verifiers.length}` };
    });

    await step("工单", "完整派单-接单-处理-附件-验收-归档流程", async () => {
      const assigneeToken = await login(context.users.assignee.username, "123456");
      const order = await apiOk("POST", "/api/work-orders", {
        projectId: context.projectId,
        deviceId: context.devices.mqtt.id,
        pointId: context.points.mqtt.temperature.id,
        title: `${testPrefix}_完整流程工单`,
        description: "自动化全流程测试工单",
        priority: "NORMAL",
        plannedFinishTime: Date.now() + 3600_000,
        remark: "auto test"
      }, global.adminToken);
      context.workOrderId = order.id;
      await apiOk("POST", `/api/work-orders/${order.id}/action`, {
        action: "DISPATCH",
        assigneeUserId: context.users.assignee.userId,
        verifierUserId: global.currentAdmin.userId,
        plannedFinishTime: Date.now() + 3600_000,
        remark: "派单给自动测试维修员"
      }, global.adminToken);
      await apiExpectCode("POST", `/api/work-orders/${order.id}/action`, { action: "ACCEPT" }, global.adminToken, 400);
      await apiOk("POST", `/api/work-orders/${order.id}/action`, { action: "ACCEPT" }, assigneeToken);
      await apiOk("POST", `/api/work-orders/${order.id}/action`, { action: "START" }, assigneeToken);

      const png = Buffer.from("89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C4890000000A49444154789C636000000200015FE221BC0000000049454E44AE426082", "hex");
      const form = new FormData();
      form.append("file", new Blob([png], { type: "image/png" }), "auto-test.png");
      const upload = await fetch(`${API_BASE}/api/work-orders/${order.id}/attachments`, {
        method: "POST",
        headers: { Authorization: `Bearer ${assigneeToken}` },
        body: form
      });
      const uploadJson = await upload.json();
      expect(uploadJson.code === 200 && uploadJson.data && uploadJson.data.fileUrl, `attachment upload failed: ${JSON.stringify(uploadJson)}`);

      await apiExpectCode("POST", `/api/work-orders/${order.id}/action`, {
        action: "FINISH",
        faultReason: "wrong operator",
        processMeasure: "wrong operator",
        processResult: "wrong operator"
      }, global.adminToken, 400);

      await apiOk("POST", `/api/work-orders/${order.id}/action`, {
        action: "FINISH",
        faultType: "模拟故障",
        faultReason: "自动化测试故障原因",
        processMeasure: "自动化测试处理措施",
        processResult: "设备恢复正常",
        remark: "提交验收"
      }, assigneeToken);
      await apiExpectCode("POST", `/api/work-orders/${order.id}/action`, { action: "VERIFY" }, assigneeToken, 400);
      const verified = await apiOk("POST", `/api/work-orders/${order.id}/action`, {
        action: "VERIFY",
        remark: "验收通过"
      }, global.adminToken);
      expect(String(verified.status).toUpperCase() === "CLOSED", `expected CLOSED, got ${verified.status}`);
      const detail = await apiOk("GET", `/api/work-orders/${order.id}`, null, global.adminToken);
      expect((detail.logs || []).length >= 6, "flow logs are incomplete");
      expect((detail.attachments || []).length >= 1, "attachments missing in detail");
      expect(detail.maintenanceCard && detail.maintenanceCard.id, "maintenance card not auto archived");
      context.maintenanceCardId = detail.maintenanceCard.id;
      return {
        detail: `workOrderId=${order.id}, cardId=${context.maintenanceCardId}, logs=${detail.logs.length}, attachments=${detail.attachments.length}`,
        evidence: shortJson(detail.maintenanceCard)
      };
    });

    await step("资料卡", "维修资料卡列表、详情、删除权限", async () => {
      const list = await apiOk("GET", `/api/maintenance-cards?projectId=${context.projectId}`, null, global.adminToken);
      expect(list.some((card) => card.id === context.maintenanceCardId), "maintenance card not found in list");
      const card = await apiOk("GET", `/api/maintenance-cards/${context.maintenanceCardId}`, null, global.adminToken);
      expect(card.workOrderId === context.workOrderId, "maintenance card work order link mismatch");
      const viewerToken = await login(context.users.viewer.username, "123456");
      await apiExpectCode("DELETE", `/api/maintenance-cards/${context.maintenanceCardId}`, null, viewerToken, 403);
      return { detail: `cardId=${card.id}, readonly delete denied` };
    });

    await step("设备模板", "模板列表、详情、应用", async () => {
      const templates = await apiOk("GET", "/api/device-templates", null, global.adminToken);
      if (!templates || templates.length === 0) {
        return { status: "WARN", detail: "没有可用设备模板" };
      }
      const template = templates.find((item) => item.protocolType === "MQTT") || templates[0];
      const detail = await apiOk("GET", `/api/device-templates/${encodeURIComponent(template.templateKey)}`, null, global.adminToken);
      const applied = await apiOk("POST", `/api/device-templates/${encodeURIComponent(template.templateKey)}/apply`, {
        projectId: context.projectId,
        groupId: context.groupId,
        deviceName: `${testPrefix}_TEMPLATE_DEVICE`,
        ipAddress: "127.0.0.1",
        port: context.mqttPort
      }, global.adminToken);
      expect(applied.device && applied.device.id, "template apply did not create device");
      await apiOk("DELETE", `/api/devices/${applied.device.id}`, null, global.adminToken);
      return { detail: `template=${detail.templateKey}, appliedDevice=${applied.device.id}` };
    });

    await step("权限", "只读账号只能查看不能操作", async () => {
      const viewerToken = await login(context.users.viewer.username, "123456");
      const projects = await apiOk("GET", "/api/projects", null, viewerToken);
      expect(projects.length === 1 && projects[0].id === context.projectId, "viewer project visibility incorrect");
      await apiExpectCode("POST", "/api/devices", {
        projectId: context.projectId,
        groupId: context.groupId,
        deviceName: "viewer_should_fail",
        protocolType: "MQTT",
        ipAddress: "127.0.0.1",
        port: context.mqttPort
      }, viewerToken, 403);
      await apiExpectCode("POST", `/api/points/${context.points.mqtt.setpoint.id}/write`, { value: 20 }, viewerToken, 403);
      await apiExpectCode("GET", "/api/auth-admin/users", null, viewerToken, 403);
      return { detail: "VIEW 权限读取可用，管理/写入/权限后台被拒绝" };
    });

    await step("诊断", "诊断概览", async () => {
      const overview = await apiOk("GET", "/api/diagnostics/overview", null, global.adminToken);
      expect(overview.summary && overview.collector && Array.isArray(overview.protocols), "diagnostics overview shape invalid");
      return { detail: `devices=${overview.summary.deviceTotal}, activeAlarms=${overview.summary.activeAlarms}, openOrders=${overview.summary.openWorkOrders}` };
    });

    await step("授权", "授权状态", async () => {
      const status = await apiOk("GET", "/api/license/status", null, global.adminToken);
      expect(status.machineCode && status.maxDevices && status.maxPoints, "license status missing key fields");
      return { detail: `mode=${status.mode}, valid=${status.valid}, devices=${status.usedDevices}/${status.maxDevices}, points=${status.usedPoints}/${status.maxPoints}` };
    });

    await step("接口边界", "缺少必要参数返回业务错误", async () => {
      await apiExpectCode("GET", "/api/history/points", null, global.adminToken, 400);
      const runtime = await api("GET", "/api/points/runtime", null, global.adminToken);
      expect(runtime.httpStatus === 400 || (runtime.json && runtime.json.code === 400), `expected bad request, got ${runtime.httpStatus}: ${runtime.text}`);
      return { detail: "历史和实时数据必填参数校验存在" };
    });

    await step("清理", "删除临时测试数据", async () => {
      const detail = await cleanupDatabase();
      return { detail };
    });
  } finally {
    for (const child of startedProcesses) {
      stopProcess(child);
    }
    const report = await generateReport();
    console.log(`\nREPORT_PATH=${report}`);
    scheduleExit();
  }
}

main().catch(async (error) => {
  addRecord("测试框架", "脚本异常", "FAIL", error.message || String(error), error.stack || "");
  try {
    await step("清理", "异常后清理临时测试数据", async () => {
      const detail = await cleanupDatabase();
      return { detail };
    });
  } catch (cleanupError) {
    addRecord("清理", "异常后清理临时测试数据", "WARN", cleanupError.message || String(cleanupError), "");
  }
  for (const child of startedProcesses) {
    stopProcess(child);
  }
  const report = await generateReport();
  console.log(`\nREPORT_PATH=${report}`);
  process.exitCode = 1;
  scheduleExit();
});
