/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.server;

import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.FrameworkUtil;

public class ResourceHelper {

    public static URL locate(String name) {
        // ResourceLocator is available only in newer eclipse versions :|
        return FileLocator.find(FrameworkUtil.getBundle(ResourceHelper.class), new Path(name));
    }

}
