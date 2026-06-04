const mqtt = require('mqtt');

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 1883);
const topic = process.argv[4] || 'iiot/test';
const commandTopic = process.argv[5] || 'iiot/write';
const ackTopic = process.argv[6] || 'iiot/write/ack';

const client = mqtt.connect(`mqtt://${host}:${port}`, {
  clientId: `iiot_sim_${Date.now()}`,
  clean: true,
  reconnectPeriod: 1000,
});

const writableState = {
  setpoint: 50,
  fan_start: false,
  lastCommand: null,
};

function round(value, digits = 2) {
  const factor = Math.pow(10, digits);
  return Math.round(value * factor) / factor;
}

function buildPayload() {
  const now = new Date();
  const second = now.getSeconds();
  const temperature = round(22 + Math.sin(second / 6) * 4 + (Math.random() - 0.5) * 0.6);
  const humidity = round(55 + Math.cos(second / 8) * 8 + (Math.random() - 0.5));
  const pressure = round(0.65 + Math.random() * 0.8);
  const speed = Math.floor(1180 + Math.random() * 340);
  const running = speed > 1250;
  const status = running ? '运行' : '待机';

  return {
    出口温度: temperature,
    环境湿度: humidity,
    排气压力: pressure,
    设备状态: status,
    temperature,
    humidity,
    pressure,
    setpoint: writableState.setpoint,
    fan_start: writableState.fan_start,
    status,
    lastCommand: writableState.lastCommand,
    motor: {
      speed,
      running,
      current: round(8 + Math.random() * 3),
    },
    电机: {
      转速: speed,
      运行: running,
      电流: round(8 + Math.random() * 3),
    },
    alarms: [
      { code: 'A001', active: temperature > 25, level: 'WARN' },
      { code: 'P001', active: pressure > 1.2, level: 'INFO' },
    ],
    报警: [
      { 编码: 'A001', 触发: temperature > 25, 级别: '预警' },
      { 编码: 'P001', 触发: pressure > 1.2, 级别: '提示' },
    ],
    timestamp: Date.now(),
  };
}

function applyCommand(command) {
  if (command.params && typeof command.params === 'object') {
    let accepted = false;
    for (const [key, value] of Object.entries(command.params)) {
      accepted = applyCommand({ pointKey: key, address: key, value }) || accepted;
    }
    return accepted;
  }
  if (command.data && typeof command.data === 'object') {
    return applyCommand(command.data);
  }
  if (command.pointKey === 'setpoint' || command.address === 'setpoint') {
    writableState.setpoint = Number(command.value);
    return true;
  }
  if (command.pointKey === 'fan_start' || command.address === 'fan_start') {
    writableState.fan_start = command.value === true || command.value === 'true' || command.value === 1 || command.value === '1';
    return true;
  }
  return false;
}

function publishAck(command, success, message) {
  const requestId = command.requestId || command.id || command.msgId;
  if (!ackTopic || !requestId) {
    return;
  }
  const ack = {
    requestId,
    id: requestId,
    pointKey: command.pointKey,
    address: command.address,
    value: command.value,
    success,
    status: success ? 'success' : 'failed',
    code: success ? 0 : 500,
    message,
    timestamp: Date.now(),
  };
  client.publish(ackTopic, JSON.stringify(ack), { qos: 0, retain: false });
  console.log(`ack ${ackTopic}: ${JSON.stringify(ack)}`);
}

client.on('connect', () => {
  console.log(`MQTT simulator connected: ${host}:${port}, telemetry=${topic}, command=${commandTopic}, ack=${ackTopic}`);
  client.subscribe(commandTopic);
  setInterval(() => {
    const payload = JSON.stringify(buildPayload());
    client.publish(topic, payload, { qos: 0, retain: false });
    console.log(payload);
  }, 1000);
});

client.on('message', (receivedTopic, payloadBuffer) => {
  if (receivedTopic !== commandTopic) {
    return;
  }
  const text = payloadBuffer.toString('utf8');
  console.log(`command ${receivedTopic}: ${text}`);
  try {
    const command = JSON.parse(text);
    writableState.lastCommand = command;
    const success = applyCommand(command);
    publishAck(command, success, success ? 'write accepted' : 'unknown point');
  } catch (error) {
    writableState.lastCommand = { raw: text, error: error.message };
    publishAck({ requestId: null }, false, error.message);
  }
});

client.on('error', (error) => {
  console.error(error.message);
});
