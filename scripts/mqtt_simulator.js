const mqtt = require("mqtt");

// Usage:
// node mqtt_simulator.js 127.0.0.1 1883 iiot/test2 iiot/write2

const broker = process.argv[2] || process.env.MQTT_BROKER || "127.0.0.1";
const port = Number(process.argv[3] || process.env.MQTT_PORT || 1883);

const readTopic = process.argv[4] || process.env.MQTT_READ_TOPIC || "iiot/test2";
const writeTopic = process.argv[5] || process.env.MQTT_WRITE_TOPIC || "iiot/write2";

const client = mqtt.connect(`mqtt://${broker}:${port}`, {
  clientId: "mqtt_simulator_" + Math.random().toString(16).slice(2),
  clean: true,
  reconnectPeriod: 1000
});

let state = {
  temperature: 24.00,
  humidity: 60.00,
  pressure: 0.82,

  setpoint: 26.00,

  fan_start: 0,
  fan_status: 0,

  alarm: 0,
  online: 1
};

function round(num, digits) {
  return Number(num.toFixed(digits));
}

function random(min, max) {
  return min + Math.random() * (max - min);
}

function toBoolValue(value) {
  if (typeof value === "number") {
    return value > 0 ? 1 : 0;
  }

  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }

  const text = String(value).trim().toLowerCase();

  if (
    text === "1" ||
    text === "true" ||
    text === "on" ||
    text === "start" ||
    text === "run" ||
    text === "running"
  ) {
    return 1;
  }

  if (
    text === "0" ||
    text === "false" ||
    text === "off" ||
    text === "stop" ||
    text === "stopped"
  ) {
    return 0;
  }

  return 0;
}

function writeValue(key, value) {
  key = String(key || "").trim();

  if (key === "") {
    console.log("[WRITE FAIL] empty key");
    return false;
  }

  if (key === "setpoint") {
    const num = Number(value);

    if (Number.isNaN(num)) {
      console.log("[WRITE FAIL] setpoint is not number:", value);
      return false;
    }

    state.setpoint = round(num, 2);
    console.log("[WRITE OK] setpoint =", state.setpoint);
    return true;
  }

  if (key === "fan_start") {
    state.fan_start = toBoolValue(value);
    state.fan_status = state.fan_start;
    console.log("[WRITE OK] fan_start =", state.fan_start);
    return true;
  }

  // Allow writing fan_status only for testing, but normally fan_status should be feedback.
  if (key === "fan_status") {
    state.fan_status = toBoolValue(value);
    state.fan_start = state.fan_status;
    console.log("[WRITE OK] fan_status =", state.fan_status);
    return true;
  }

  if (key === "temperature") {
    const num = Number(value);
    if (Number.isNaN(num)) {
      console.log("[WRITE FAIL] temperature is not number:", value);
      return false;
    }

    state.temperature = round(num, 2);
    console.log("[WRITE OK] temperature =", state.temperature);
    return true;
  }

  if (key === "humidity") {
    const num = Number(value);
    if (Number.isNaN(num)) {
      console.log("[WRITE FAIL] humidity is not number:", value);
      return false;
    }

    state.humidity = round(num, 2);
    console.log("[WRITE OK] humidity =", state.humidity);
    return true;
  }

  if (key === "pressure") {
    const num = Number(value);
    if (Number.isNaN(num)) {
      console.log("[WRITE FAIL] pressure is not number:", value);
      return false;
    }

    state.pressure = round(num, 3);
    console.log("[WRITE OK] pressure =", state.pressure);
    return true;
  }

  if (key === "alarm") {
    state.alarm = toBoolValue(value);
    console.log("[WRITE OK] alarm =", state.alarm);
    return true;
  }

  if (key === "online") {
    state.online = toBoolValue(value);
    console.log("[WRITE OK] online =", state.online);
    return true;
  }

  console.log("[WRITE IGNORE] unknown key:", key);
  return false;
}

function publishWriteResult(request, success, message) {
  const result = {
    requestId: request && request.requestId ? request.requestId : null,
    address: request && request.address ? request.address : null,
    pointKey: request && request.pointKey ? request.pointKey : null,
    value: request && request.value !== undefined ? request.value : null,
    success: success,
    message: message,
    timestamp: Date.now()
  };

  client.publish(writeTopic + "/result", JSON.stringify(result), {
    qos: 0,
    retain: false
  });

  if (result.requestId) {
    client.publish(writeTopic + "/result/" + result.requestId, JSON.stringify(result), {
      qos: 0,
      retain: false
    });
  }
}

function handleWrite(topic, message) {
  const text = message.toString().trim();

  console.log("");
  console.log("[RECEIVE WRITE]", topic, text);

  // Format 1:
  // topic: iiot/write2/setpoint
  // payload: 11.2
  if (topic.startsWith(writeTopic + "/")) {
    const key = topic.substring(writeTopic.length + 1);

    // Ignore our own result topics.
    if (key === "result" || key.startsWith("result/")) {
      return;
    }

    const success = writeValue(key, text);
    publishNow();
    publishWriteResult(
      {
        address: key,
        pointKey: key,
        value: text
      },
      success,
      success ? "OK" : "WRITE FAILED"
    );
    return;
  }

  // Format 2:
  // SCADA format:
  // {
  //   "address": "setpoint",
  //   "requestId": "...",
  //   "pointKey": "setpoint",
  //   "dataType": "Float32",
  //   "value": 11.2,
  //   "timestamp": 1780372728191
  // }
  try {
    const obj = JSON.parse(text);

    if (Array.isArray(obj)) {
      let okCount = 0;

      for (const item of obj) {
        if (!item || typeof item !== "object") {
          continue;
        }

        const key = item.address || item.pointKey || item.path || item.name || item.tag;
        const value = item.value;

        if (key !== undefined && value !== undefined) {
          const success = writeValue(key, value);
          if (success) okCount++;

          publishWriteResult(item, success, success ? "OK" : "WRITE FAILED");
        }
      }

      publishNow();
      console.log("[WRITE ARRAY] success count =", okCount);
      return;
    }

    if (obj && typeof obj === "object") {
      // Your SCADA format, highest priority.
      if (obj.address !== undefined && obj.value !== undefined) {
        const success = writeValue(obj.address, obj.value);
        publishNow();
        publishWriteResult(obj, success, success ? "OK" : "WRITE FAILED");
        return;
      }

      if (obj.pointKey !== undefined && obj.value !== undefined) {
        const success = writeValue(obj.pointKey, obj.value);
        publishNow();
        publishWriteResult(obj, success, success ? "OK" : "WRITE FAILED");
        return;
      }

      // Other common formats.
      if (obj.path !== undefined && obj.value !== undefined) {
        const success = writeValue(obj.path, obj.value);
        publishNow();
        publishWriteResult(obj, success, success ? "OK" : "WRITE FAILED");
        return;
      }

      if (obj.name !== undefined && obj.value !== undefined) {
        const success = writeValue(obj.name, obj.value);
        publishNow();
        publishWriteResult(obj, success, success ? "OK" : "WRITE FAILED");
        return;
      }

      if (obj.tag !== undefined && obj.value !== undefined) {
        const success = writeValue(obj.tag, obj.value);
        publishNow();
        publishWriteResult(obj, success, success ? "OK" : "WRITE FAILED");
        return;
      }

      // Simple JSON format:
      // {"setpoint": 11.2}
      // {"fan_start": 1}
      let okCount = 0;

      for (const key of Object.keys(obj)) {
        const success = writeValue(key, obj[key]);
        if (success) okCount++;
      }

      publishNow();
      console.log("[WRITE OBJECT] success count =", okCount);
      return;
    }
  } catch (e) {
    // Not JSON, continue to parse plain text.
  }

  // Format 3:
  // setpoint=11.2
  // setpoint:11.2
  // fan_start=1
  const match = text.match(/^([a-zA-Z0-9_\\.]+)\s*[:=]\s*(.+)$/);

  if (match) {
    const success = writeValue(match[1], match[2]);
    publishNow();
    publishWriteResult(
      {
        address: match[1],
        pointKey: match[1],
        value: match[2]
      },
      success,
      success ? "OK" : "WRITE FAILED"
    );
    return;
  }

  console.log("[WRITE FAIL] unsupported payload");
}

function simulate() {
  if (state.fan_start === 1) {
    const diff = state.temperature - state.setpoint;
    state.temperature = state.temperature - diff * 0.15 + random(-0.1, 0.1);
    state.pressure = 0.85 + random(-0.03, 0.03);
  } else {
    state.temperature = state.temperature + random(0.01, 0.12);
    state.pressure = 0.78 + random(-0.02, 0.02);
  }

  state.humidity = state.humidity + random(-0.5, 0.5);

  if (state.humidity < 45) state.humidity = 45;
  if (state.humidity > 80) state.humidity = 80;

  state.temperature = round(state.temperature, 2);
  state.humidity = round(state.humidity, 2);
  state.pressure = round(state.pressure, 3);

  state.fan_status = state.fan_start;
  state.alarm = state.temperature > 35 ? 1 : 0;
  state.online = 1;
}

function publishNow() {
  const json = JSON.stringify(state);

  // Publish full JSON.
  client.publish(readTopic, json, {
    qos: 0,
    retain: true
  });

  // Publish single-point topics too.
  client.publish(readTopic + "/temperature", String(state.temperature), { qos: 0, retain: true });
  client.publish(readTopic + "/humidity", String(state.humidity), { qos: 0, retain: true });
  client.publish(readTopic + "/pressure", String(state.pressure), { qos: 0, retain: true });
  client.publish(readTopic + "/setpoint", String(state.setpoint), { qos: 0, retain: true });
  client.publish(readTopic + "/fan_start", String(state.fan_start), { qos: 0, retain: true });
  client.publish(readTopic + "/fan_status", String(state.fan_status), { qos: 0, retain: true });
  client.publish(readTopic + "/alarm", String(state.alarm), { qos: 0, retain: true });
  client.publish(readTopic + "/online", String(state.online), { qos: 0, retain: true });

  console.log("[PUBLISH]", readTopic, json);
}

client.on("connect", function () {
  console.log("");
  console.log("MQTT simulator started");
  console.log("Broker:", broker);
  console.log("Port:", port);
  console.log("Read topic:", readTopic);
  console.log("Write topic:", writeTopic);
  console.log("Writable keys: setpoint, fan_start, fan_status, temperature, humidity, pressure, alarm, online");
  console.log("");

  client.subscribe(writeTopic, { qos: 0 });
  client.subscribe(writeTopic + "/#", { qos: 0 });

  publishNow();

  setInterval(function () {
    simulate();
    publishNow();
  }, 1000);
});

client.on("message", handleWrite);

client.on("error", function (err) {
  console.log("[MQTT ERROR]", err.message);
});

client.on("close", function () {
  console.log("[MQTT CLOSED]");
});