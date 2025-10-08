package io.bdeploy.interfaces.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.bdeploy.interfaces.minion.MinionStatusDto;

public class NodeListDto {

    public Map<String, MinionStatusDto> nodes = Collections.emptyMap();

    public Map<String, List<String>> multiNodeToRuntimeNodes = Collections.emptyMap();

}
