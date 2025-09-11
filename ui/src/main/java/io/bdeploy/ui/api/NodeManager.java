package io.bdeploy.ui.api;

import java.util.Collection;
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
     * @return the names of all configured nodes in the system.
     */
    public Collection<String> getAllNodeNames();

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
     * @return the configuration of the specified node if the node is known and online. <code>null</code> otherwise.
     */
    public MinionDto getNodeConfigIfOnline(String name);

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

}
