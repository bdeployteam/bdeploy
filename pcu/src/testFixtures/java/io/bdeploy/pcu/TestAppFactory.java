package io.bdeploy.pcu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;

public class TestAppFactory {

    private static Map<OperatingSystem, String> scriptEnding = new TreeMap<>();
    private static Map<OperatingSystem, String> sleepScriptContent = new TreeMap<>();

    static {
        scriptEnding.put(OperatingSystem.LINUX, ".sh");
        scriptEnding.put(OperatingSystem.MACOS, ".sh");
        scriptEnding.put(OperatingSystem.WINDOWS, ".cmd");

        sleepScriptContent.put(OperatingSystem.WINDOWS, //
                "@echo off\n" + //
                        "echo Hello script\n" + //
                        "powershell -command \"sleep %1\"" //
        );

        sleepScriptContent.put(OperatingSystem.LINUX, //
                "#!/usr/bin/env bash\n" + //
                        "echo 'Hello script'\n" + //
                        "sleep $1\n" //
        );

        sleepScriptContent.put(OperatingSystem.MACOS, //
                "#!/usr/bin/env bash\n" + //
                        "echo 'Hello script'\n" + //
                        "sleep $1\n" //
        );
    }

    /**
     * Generates script that takes a single argument how long it should block before
     * terminating the application
     *
     * @param prefix the name of the script
     * @param target the target path
     * @return the generated script
     */
    public static Path genSleepScript(String prefix, Path target) {
        try {
            Path script = target.resolve(prefix + scriptEnding.get(OsHelper.getRunningOs()));
            PathHelper.mkdirs(target);
            Files.write(script, Collections.singleton(sleepScriptContent.get(OsHelper.getRunningOs())));
            setExecutable(script);
            return script;
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to generate script", ioe);
        }
    }

    /**
     * Sets required attributes to make a file executable
     */
    private static void setExecutable(Path child) throws IOException {
        PosixFileAttributeView view = PathHelper.getPosixView(child);
        if (view != null) {
            Set<PosixFilePermission> perms = view.readAttributes().permissions();
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            view.setPermissions(perms);
        }
    }

    public static Path createDummyApp(String name, Path tmp) {
        return createDummyApp(name, tmp, false, 0);
    }

    /**
     * Creates a dummy application along with an {@link ApplicationDescriptor}.
     *
     * @param port
     */
    public static Path createDummyApp(String name, Path tmp, boolean client, int port) {
        Path target = tmp.resolve(name);
        PathHelper.mkdirs(target);
        ApplicationDescriptor cfg = new ApplicationDescriptor();

        Path script = genSleepScript("launch", target);

        cfg.name = name;
        cfg.supportedOperatingSystems.add(OsHelper.getRunningOs());
        cfg.startCommand = new ExecutableDescriptor();
        cfg.startCommand.launcherPath = script.getFileName().toString();
        cfg.type = client ? ApplicationType.CLIENT : ApplicationType.SERVER;

        cfg.runtimeDependencies.add("jdk:1.8.0");
        ParameterDescriptor sleepParam = new ParameterDescriptor();
        sleepParam.id = "sleepParam";
        sleepParam.name = "Sleep Time";
        sleepParam.groupName = "Dummy Group";
        sleepParam.longDescription = "Long parameter description for sleep timeout";
        sleepParam.parameter = "10"; // seconds to sleep
        sleepParam.hasValue = false;
        cfg.startCommand.parameters.add(sleepParam);
        cfg.startCommand.parameters.add(getParam("--param1", "Parameter 1", "test"));
        cfg.startCommand.parameters.add(getParam("--param2", "Parameter 2", "more"));
        cfg.startCommand.parameters.add(getParam("--jdk", "Parameter 2", "{{M:jdk}}"));

        if (port != 0) {
            HttpEndpoint fakeEndpoint = new HttpEndpoint();
            fakeEndpoint.id = "test";
            fakeEndpoint.path = "/api/test/with/path"; // must match HelloEndpoint
            fakeEndpoint.port = new LinkedValueConfiguration(String.valueOf(port));
            fakeEndpoint.secure = new LinkedValueConfiguration("true");
            fakeEndpoint.trustAll = true;

            cfg.endpoints.http.add(fakeEndpoint);
        }

        try {
            Files.write(target.resolve(ApplicationDescriptor.FILE_NAME),
                    JacksonHelper.createObjectMapper(MapperType.YAML).writeValueAsBytes(cfg));
            return target;
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to generate application.", ioe);
        }
    }

    /**
     * Create a dummy application without {@link ApplicationDescriptor}.
     * <p>
     * This should mimic how a third party application looks like (e.g. "jdk").
     */
    public static Path createDummyAppNoDescriptor(String name, Path tmp) throws IOException {
        Path target = tmp.resolve(name);
        PathHelper.mkdirs(target);
        ContentHelper.genTestFile(target.resolve("dummy.bin"), 1024);
        return target;
    }

    private static ParameterDescriptor getParam(String param, String desc, String def) {
        ParameterDescriptor c = new ParameterDescriptor();
        c.id = param;
        c.parameter = param;
        c.name = desc;
        c.groupName = "Generated Group";
        c.longDescription = "[Long description] :" + desc;
        c.defaultValue = new LinkedValueConfiguration(def);
        return c;
    }

}
