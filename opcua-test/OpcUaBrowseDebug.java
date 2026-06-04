import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;

import java.util.Collections;

public class OpcUaBrowseDebug {
    public static void main(String[] args) throws Exception {
        String url = "opc.tcp://127.0.0.1:4840/UA/IiotTest";
        EndpointDescription endpoint = DiscoveryClient.getEndpoints(url).get().get(0);
        OpcUaClient client = OpcUaClient.create(OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setIdentityProvider(new AnonymousProvider())
                .build());
        client.connect().get();
        try {
            browse(client, Identifiers.RootFolder);
            browse(client, Identifiers.ObjectsFolder);
        } finally {
            client.disconnect().get();
        }
    }

    private static void browse(OpcUaClient client, NodeId nodeId) throws Exception {
        BrowseDescription bd = new BrowseDescription(nodeId, BrowseDirection.Forward, NodeId.NULL_VALUE, true, UInteger.valueOf(0), UInteger.valueOf(63));
        ViewDescription view = new ViewDescription(NodeId.NULL_VALUE, DateTime.MIN_VALUE, UInteger.valueOf(0));
        BrowseResult result = client.browse(view, UInteger.valueOf(100), Collections.singletonList(bd)).get().getResults()[0];
        System.out.println("node=" + nodeId + " status=" + result.getStatusCode() + " refs=" + (result.getReferences() == null ? 0 : result.getReferences().length));
        if (result.getReferences() != null) {
            for (int i = 0; i < Math.min(20, result.getReferences().length); i++) {
                System.out.println(" - " + result.getReferences()[i].getDisplayName().getText() + " " + result.getReferences()[i].getNodeClass() + " " + result.getReferences()[i].getNodeId().toParseableString());
            }
        }
    }
}
