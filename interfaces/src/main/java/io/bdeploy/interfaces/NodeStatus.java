package io.bdeploy.interfaces;

import java.time.Instant;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

public class NodeStatus {

    public OperatingSystem os;

    public Instant startup;

    public String version;

    public boolean master;

}