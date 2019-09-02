package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;

/**
 * Holds brief information about the history of a single instance version.
 */
public class InstanceManifestHistoryDto {

    public List<InstanceManifestHistoryRecord> records = new ArrayList<>();

}
