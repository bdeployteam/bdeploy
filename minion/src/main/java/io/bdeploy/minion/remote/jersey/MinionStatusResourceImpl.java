package io.bdeploy.minion.remote.jersey;

import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jvnet.hk2.annotations.Optional;

import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.ui.api.Minion;

@Singleton
public class MinionStatusResourceImpl implements MinionStatusResource {

    @Inject
    @Named(JerseyServer.START_TIME)
    private Instant startTime;

    @Inject
    @Optional
    @Named(Minion.MASTER)
    private Boolean isMaster;

    @Override
    public NodeStatus getStatus() {
        NodeStatus s = new NodeStatus();

        s.os = OsHelper.getRunningOs();
        s.startup = startTime;

        s.version = VersionHelper.readVersion();
        s.master = isMaster == null ? false : isMaster;

        return s;
    }

}
