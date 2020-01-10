package io.bdeploy.launcher.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.cli.ToolBase;

public class LauncherCli extends ToolBase {

    public LauncherCli() {
        register(LauncherTool.class);
        register(UninstallerTool.class);
    }

    @Override
    public void toolMain(String... args) throws Exception {
        // filter out extra options given by the command line scripts.
        List<String> noOptArgs = Arrays.stream(args).filter(a -> !a.startsWith("-")).collect(Collectors.toList());
        if (noOptArgs.size() == 1 && noOptArgs.get(0).toLowerCase().endsWith(".bdeploy")) {
            // want to directly launch a single .bdeploy file
            List<String> argumentList = new ArrayList<>();
            argumentList.add("launcher");
            for (String arg : args) {
                if (arg.toLowerCase().endsWith(".bdeploy")) {
                    argumentList.add("--launch=" + arg);
                } else {
                    // pass on all others (e.g. updateDir).
                    argumentList.add(arg);
                }
            }
            args = argumentList.toArray(new String[argumentList.size()]);
        }

        super.toolMain(args);
    }

    public static void main(String... args) throws Exception {
        new LauncherCli().toolMain(args);
    }

}
