package io.bdeploy.minion;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestMinion.class)
public class MinionBasicClientTest {

    @Test
    public void listMinionsTest(MasterRootResource master) throws Exception {
        SortedMap<String, MinionStatusDto> minions = master.getMinions();
        assertThat(minions.size(), is(1));
        assertThat(minions.get(Minion.DEFAULT_NAME), is(notNullValue()));
    }

}
