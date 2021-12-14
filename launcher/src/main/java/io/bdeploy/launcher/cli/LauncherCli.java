package io.bdeploy.launcher.cli;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.logging.audit.RollingFileAuditor;

public class LauncherCli extends ToolBase {

    public LauncherCli() {
        register(BrowserTool.class);
        register(LauncherTool.class);
        register(UninstallerTool.class);

        setAuditorFactory(RollingFileAuditor.getFactory());
    }

    @Override
    public void toolMain(String... args) throws Exception {
        // Arguments starting with a single dash (-) are options for the tool
        // Arguments starting with a double dash (--) are options for the application
        List<String> noOpt = new ArrayList<>();
        List<String> appArgs = new ArrayList<>();
        List<String> toolArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                appArgs.add(arg);
            } else if (arg.startsWith("-")) {
                toolArgs.add(arg);
            } else {
                noOpt.add(arg);
            }
        }

        // Feature: A single argument without a single/double dash prefix might be specified
        //          We expect that this is the descriptor of the file to launch
        // Order is important: <tool arguments> <toolName> <appArguments>
        // Sample: -v launcher --launch=MyFile.bdeploy
        if (noOpt.size() == 1 && Paths.get(noOpt.get(0)).toAbsolutePath().toFile().isFile()) {
            List<String> argumentList = new ArrayList<>();
            argumentList.addAll(toolArgs);
            argumentList.add("launcher");
            argumentList.add("--launch=" + noOpt.get(0));
            argumentList.addAll(appArgs);
            args = argumentList.toArray(new String[argumentList.size()]);
        } else if (noOpt.isEmpty()) {
            List<String> argumentList = new ArrayList<>();
            argumentList.addAll(toolArgs);
            argumentList.add("browser");
            argumentList.addAll(appArgs);
            args = argumentList.toArray(new String[argumentList.size()]);
        }

        // Pass arguments to the tools launcher
        super.toolMain(args);
    }

    public static void main(String... args) throws Exception {
        new LauncherCli().toolMain(args);
    }

}
