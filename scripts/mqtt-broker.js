const net = require('net');
const { Aedes } = require('aedes');

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 1883);

(async () => {
  const aedes = await Aedes.createBroker();
  const server = net.createServer(aedes.handle);

  server.listen(port, host, () => {
    console.log(`MQTT broker listening on ${host}:${port}`);
  });

  aedes.on('client', (client) => {
    console.log(`client connected: ${client ? client.id : 'unknown'}`);
  });

  aedes.on('clientDisconnect', (client) => {
    console.log(`client disconnected: ${client ? client.id : 'unknown'}`);
  });

  aedes.on('publish', (packet, client) => {
    if (client && packet.topic) {
      console.log(`publish ${packet.topic}: ${packet.payload.toString('utf8')}`);
    }
  });
})();
