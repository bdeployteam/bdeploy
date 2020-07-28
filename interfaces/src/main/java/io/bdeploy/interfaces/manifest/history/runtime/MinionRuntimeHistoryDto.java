package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MinionRuntimeHistoryDto {

    private final Map<String, MinionRuntimeHistory> versions;

    public MinionRuntimeHistoryDto(Map<String, MinionRuntimeHistory> dto) {
        this.versions = dto;
    }

    public MinionRuntimeHistoryDto() {
        versions = new HashMap<>();
    }

    public Map<String, MinionRuntimeHistory> getVersions() {
        return versions;
    }

    public Set<Map.Entry<String, MinionRuntimeHistory>> entrySet() {
        return versions.entrySet();
    }
}
