package io.bdeploy.launcher.cli;

import java.awt.Frame;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;

@CliName("config")
@Help("A tool which launches the launcher's built in configuration UI")
public class ConfigurationTool extends CliTool {

    @Override
    public void run() {
        System.err.println("Would want to show config UI");
        Frame f = new Frame("Test");
        f.setSize(300, 400);
        f.setVisible(true);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
