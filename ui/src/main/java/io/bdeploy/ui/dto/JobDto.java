package io.bdeploy.ui.dto;

/**
 * A DTO containing information about a job for Admin UI
 */
public class JobDto {

    public String name;
    public String group;
    public String schedule;
    public long lastRunTime;
    public long nextRunTime;
    public boolean isRunning;

}
