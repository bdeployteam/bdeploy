package io.bdeploy.minion.remote.jersey;

import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRoot;

@Singleton
public class MinionStatusResourceImpl implements MinionStatusResource {

    @Inject
    private MinionRoot root;

    @Inject
    @Named(JerseyServer.START_TIME)
    private Instant startTime;

    @Override
    public MinionStatusDto getStatus() {
        MinionStatusDto s = new MinionStatusDto();
        s.startup = startTime;
        s.config = MinionDto.create(root.getSelf());
        return s;
    }

}
