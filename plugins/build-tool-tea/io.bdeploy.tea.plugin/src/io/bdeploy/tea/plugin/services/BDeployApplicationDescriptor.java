/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.services;

import java.util.List;
import java.util.Map;

import org.eclipse.tea.library.build.model.PlatformTriple;

/**
 * Represents "build" information for a single application.
 */
public class BDeployApplicationDescriptor {

    public enum BDeployTargetOsArch {
        WINDOWS(PlatformTriple.WIN64),
        LINUX(PlatformTriple.LINUX64),
        AIX(PlatformTriple.LINUX64); // linux version (should) run for AIX

        private final PlatformTriple mappedTriple;

        private BDeployTargetOsArch(PlatformTriple mappedTriple) {
            this.mappedTriple = mappedTriple;
        }

        public PlatformTriple getMappedTriple() {
            return mappedTriple;
        }
    }

    public static class BDeployRCPProductDesc {

        public String product;
    }

    public String name;
    public List<BDeployTargetOsArch> includeOs;
    public String type;
    public Map<String, Object> application;

}
