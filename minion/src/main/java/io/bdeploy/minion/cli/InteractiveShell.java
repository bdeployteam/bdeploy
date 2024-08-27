package io.bdeploy.minion.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.minion.cli.InteractiveShell.ShellConfig;

@Help("An interactive shell - reads commands from the console and executes them")
@ToolCategory(MinionServerCli.LOCAL_SESSION_TOOLS)
@CliName("shell")
public class InteractiveShell extends ConfiguredCliTool<ShellConfig> {

    private static final Pattern CLI_SPLIT = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

    public @interface ShellConfig {

        @Help("Path to a script containing a series of commands to execute.")
        @Validator(ExistingPathValidator.class)
        String script();
    }

    public InteractiveShell() {
        super(ShellConfig.class);
    }

    @Override
    public RenderableResult run(ShellConfig config) {
        try {
            ToolBase.setFailWithException(true);

            boolean readNextCommand = true;
            if (config.script() != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(Files.newInputStream(Paths.get(config.script()))))) {
                    while (readNextCommand) {
                        readNextCommand = readAndExecute(br, true);
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot read script from " + config.script(), e);
                }
            } else {
                System.out.println("Interactive shell started.");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                    while (readNextCommand) {
                        readNextCommand = readAndExecute(br, false);
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot read commands from STDIN", e);
                }
            }

            return null; // The shell itself has no result. Only the individual commands do.
        } finally {
            ToolBase.setFailWithException(false);
        }
    }

    /** Reads the next line and executes it. Returns false to terminate the shell */
    private boolean readAndExecute(BufferedReader br, boolean headless) {
        MinionServerCli cli = new MinionServerCli();
        try {
            if (!headless) {
                System.out.print("> ");
            }
            String command = br.readLine();
            if (command == null || "exit".equals(command)) {
                return false;
            }
            command = command.trim();
            if (command.isEmpty()) {
                return true;
            }
            if (headless) {
                System.out.println("> " + command);
            }
            String[] arguments = splitCommand(command);
            cli.toolMain(arguments);
        } catch (Exception ex) {
            System.err.println("Failed to execute command.");
            ex.printStackTrace();
        }
        return true;
    }

    private static String[] splitCommand(String command) {
        List<String> matchList = new ArrayList<>();
        Matcher regexMatcher = CLI_SPLIT.matcher(command);
        while (regexMatcher.find()) {
            if (regexMatcher.group(1) != null) {
                // Add double-quoted string without the quotes
                matchList.add(regexMatcher.group(1));
            } else if (regexMatcher.group(2) != null) {
                // Add single-quoted string without the quotes
                matchList.add(regexMatcher.group(2));
            } else {
                // Add unquoted word
                matchList.add(regexMatcher.group());
            }
        }
        return matchList.toArray(new String[0]);
    }

}
