package io.bdeploy.ui.dto;

import java.util.List;

import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord.OverallStatus;

public class InstanceOverallStatusDto {

    public String uuid;
    public OverallStatus status;
    public long timestamp;
    public List<String> messages;

    public InstanceOverallStatusDto(String uuid, InstanceOverallStateRecord rec) {
        this.uuid = uuid;
        this.status = rec.status;
        this.timestamp = rec.timestamp;
        this.messages = rec.messages;
    }

}
