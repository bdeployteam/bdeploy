/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.services;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.tea.core.BackgroundTask;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Describes a single application build.
 * <p>
 * The build may happen in the future, the {@link #source} is evaluated only once the originating task has been executed.
 */
public class BDeployApplicationBuild {

    public String name;
    public Supplier<File> source;
    public OperatingSystem os;
    public BackgroundTask task;
    public Supplier<List<File>> cleanup;

}