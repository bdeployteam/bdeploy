package io.bdeploy.launcher.cli;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.cli.ToolBase;

public class LauncherCli extends ToolBase {

    public LauncherCli() {
        register(LauncherTool.class);
        register(ConfigurationTool.class);
    }

    @Override
    public void toolMain(String... args) throws Exception {
        // filter out extra options given by the command line scripts.
        List<String> noOptArgs = Arrays.stream(args).filter(a -> !a.startsWith("-")).collect(Collectors.toList());

        if (noOptArgs.isEmpty()) {
            // want to launch config UI.
            args = new String[] { "config" };
        } else if (noOptArgs.size() == 1 && noOptArgs.get(0).toLowerCase().endsWith(".bdeploy")) {
            // want to directly launch a single .bdeploy file
            args = new String[] { "launcher", "--launch=" + noOptArgs.get(0) };
        }

        super.toolMain(args);
    }

    public static void main(String... args) throws Exception {
        new LauncherCli().toolMain(args);
    }

}
