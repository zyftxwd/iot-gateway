const { OPCUAServer, Variant, DataType, StatusCodes } = require("node-opcua");

async function main() {
  const server = new OPCUAServer({
    port: 4840,
    resourcePath: "/UA/IiotTest",
    buildInfo: {
      productName: "IIoT OPC UA Test Server",
      buildNumber: "1",
      buildDate: new Date()
    }
  });

  await server.initialize();

  const addressSpace = server.engine.addressSpace;
  const namespace = addressSpace.getOwnNamespace();
  const device = namespace.addObject({
    organizedBy: addressSpace.rootFolder.objects,
    browseName: "Device1"
  });

  let temperature = 25.5;
  namespace.addVariable({
    componentOf: device,
    browseName: "Temperature",
    nodeId: "ns=1;s=Temperature",
    dataType: "Double",
    value: {
      get: () => new Variant({ dataType: DataType.Double, value: temperature }),
      set: (variant) => {
        temperature = Number(variant.value);
        return StatusCodes.Good;
      }
    }
  });

  await server.start();
  console.log("OPC UA test server listening on opc.tcp://127.0.0.1:4840/UA/IiotTest");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
