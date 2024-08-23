package io.bdeploy.minion;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestMinion.class)
class MinionBasicClientTest {

    @Test
    void testListMinions(MasterRootResource master) {
        Map<String, MinionStatusDto> minions = master.getNodes();
        assertThat(minions.size(), is(1));
        assertThat(minions.get(Minion.DEFAULT_NAME), is(notNullValue()));
    }
}
