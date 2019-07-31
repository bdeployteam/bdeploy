package io.bdeploy.minion.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;

@Help("An interactive shell. Reads commands from the console and executes them.")
@CliName("shell")
public class InteractiveShell extends CliTool {

    public InteractiveShell() {
    }

    @Override
    public void run() {
        try {
            MinionServerCli.setFailWithException(true);
            System.out.println("Interactive shell started.");
            boolean readNextCommand = true;
            while (readNextCommand) {
                readNextCommand = readAndExecute();
            }
        } finally {
            MinionServerCli.setFailWithException(false);
        }
    }

    /** Reads the next line and executes it. Returns false to terminate the shell */
    private boolean readAndExecute() {
        MinionServerCli cli = new MinionServerCli();
        try {
            System.out.print("> ");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String command = br.readLine();
            if (command == null || command.equals("exit")) {
                return false;
            }
            command = command.trim();
            if (command.isEmpty()) {
                return true;
            }
            String[] arguments = splitCommand(command);
            cli.toolMain(arguments);
        } catch (Exception ex) {
            System.err.println("Failed to execute command.");
            ex.printStackTrace();
        }
        return true;
    }

    private String[] splitCommand(String command) {
        List<String> matchList = new ArrayList<>();
        Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
        Matcher regexMatcher = regex.matcher(command);
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
