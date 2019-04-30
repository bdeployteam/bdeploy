package io.bdeploy.pcu;

import java.nio.file.Path;
import java.util.Arrays;

import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;

/**
 * Factory class to create instance {@linkplain InstanceProcessController controllers}, process {@linkplain ProcessConfiguration
 * configuration} and process {@linkplain ProcessController controllers}.
 */
public class TestFactory {

    /**
     * Creates and returns a new group configuration.
     *
     * @param name
     *            the UID of the instance
     * @param apps
     *            the assigned applications
     * @return the created configuration
     */
    public static ProcessGroupConfiguration createGroupConfig(String name, ProcessConfiguration... apps) {
        ProcessGroupConfiguration config = new ProcessGroupConfiguration();
        config.name = name;
        config.uuid = name;
        config.applications.addAll(Arrays.asList(apps));
        return config;
    }

    /**
     * Creates and returns a new process configuration using the 'Sleep' application.
     *
     * @param path
     *            the target path where to store the script
     * @param name
     *            the UID of the application
     * @param keepAlive
     *            whether or not to restart the application after it terminated
     * @param argument
     *            the arguments passed to the script
     * @return the process configuration that describes the application
     */
    public static ProcessConfiguration createConfig(Path path, String name, boolean keepAlive, String argument) {
        Path script = TestAppFactory.genSleepScript("sleep", path);

        ProcessConfiguration config = new ProcessConfiguration();
        config.uid = name;
        config.processControl = new ProcessControlConfiguration();
        config.processControl.keepAlive = keepAlive;
        config.processControl.startType = keepAlive ? ApplicationStartType.INSTANCE : ApplicationStartType.MANUAL;
        config.start.addAll(Arrays.asList(script.toString(), argument));
        return config;
    }

    /**
     * Creates and returns a new process controller using the 'Sleep' application.
     *
     * @param path
     *            the target path where to store the script
     * @param name
     *            the UID of the application
     * @param keepAlive
     *            whether or not to launch the application automatically
     * @param argument
     *            the arguments passed to the script
     * @return the process controller to launch the application
     */
    public static ProcessController create(Path path, String name, boolean keepAlive, String argument) {
        ProcessConfiguration config = createConfig(path, name, keepAlive, argument);
        ProcessController controller = new ProcessController("Test", "V1", config, path);
        return controller;
    }

}
