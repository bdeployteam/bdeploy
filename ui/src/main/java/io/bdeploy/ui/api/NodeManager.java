package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.minion.MultiNodeDto;
import io.bdeploy.ui.api.impl.ChangeEventManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.SecurityContext;

/**
 * The {@link NodeManager} keeps track of all nodes in the system. It is responsible for establishing and tracking connections
 * to nodes so that processes requiring communication with nodes will not synchronously run into communication issues.
 */
public interface NodeManager {

    /**
     * @return all configured nodes in the system.
     */
    public Map<String, MinionDto> getAllNodes();

    /**
     * @return all currently online multi-node runtime nodes for a given multi-node name.
     */
    public Map<String, MinionDto> getMultiNodeRuntimeNodes(String name);

    /**
     * @return the name of the multi-node that is implemented by the given runtime node.
     */
    public String getMultiNodeConfigNameForRuntimeNode(String runtimeNode);

    /**
     * @return the status of all configured nodes in the system.
     */
    public Map<String, MinionStatusDto> getAllNodeStatus();

    /**
     * @param name the name of the node to look up.
     * @return the configuration of the specified node.
     */
    public MinionDto getNodeConfig(String name);

    /**
     * @param name the name of the node to look up.
     * @return the status of the specified node.
     */
    public MinionStatusDto getNodeStatus(String name);

    /**
     * @param name the name of the node to look up.
     * @return the configurations for the specified node which are online. Never <code>null</code>. Might be empty in case no node
     *         is online. Might be multiple in case of multi-nodes.
     */
    public Map<String, MinionDto> getOnlineNodeConfigs(String name);

    /**
     * @param name the name of a node which must not refer to a multi-node
     * @return a single {@link MinionDto corresponding to an online} physical node which can be talked to.
     */
    public MinionDto getSingleOnlineNodeConfig(String name);

    /**
     * @param <T> one of the node remote interfaces.
     * @param node the name of the node to contact.
     * @param clazz the {@link Class} of one of the node remote interfaces.
     * @param context the {@link SecurityContext} of the current operation.
     * @return an instance of the node remote interface of the node is known and online.
     * @throws WebApplicationException in case the node is not known or offline.
     */
    public <T> T getNodeResourceIfOnlineOrThrow(String node, Class<T> clazz, SecurityContext context);

    /**
     * @return the configuration of the own running node process.
     */
    public MinionDto getSelf();

    /**
     * @return the currently running minions own name.
     */
    public String getSelfName();

    /**
     * @param name the name of the node to add.
     * @param config the configuration of the node.
     */
    public void addNode(String name, MinionDto config);

    /**
     * @param name the name of the node to edit.
     * @param node the updated configuration for the node.
     */
    public void editNode(String name, RemoteService node);

    /**
     * @param name the name of the node to remove.
     */
    public void removeNode(String name);

    /**
     * @param changes the {@link ChangeEventManager} to be used by this {@link NodeManager}.
     */
    public void setChangeEventManager(ChangeEventManager changes);

    /**
     * @param name the name of the multi-node to add
     * @param config the configuration of the node
     */
    public void addMultiNode(String name, MultiNodeDto config);

    /**
     * Attach a multi node (runtime) to a multi node (configuration).
     *
     * @param name the name of the multi-node - must exist in the node manager already.
     * @param runtimeName the unique name of the node as provided by the user
     * @param multiNodeDto the actual runtime part to be attached to the multi-node.
     */
    public void attachMultiNodeRuntime(String name, String runtimeName, MinionDto multiNodeDto);

    /**
     * @return a mapping of multi-node name to the names of all its runtime nodes.
     */
    public Map<String, List<String>> getMultiNodeToRuntimeNodes();
}
