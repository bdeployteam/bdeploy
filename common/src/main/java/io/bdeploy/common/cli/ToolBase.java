/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.common.cli;

import java.io.File;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.codahale.metrics.Timer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.ConfigValidationException;
import io.bdeploy.common.cfg.Configuration;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.util.VersionHelper;

/**
 * Main CLI entry point base class.
 */
@SuppressFBWarnings
public abstract class ToolBase {

    private static final String[] LOGO = { //
            "┌──────────────────────────────┐  ", //
            "│                   ▄▄▄        │  ", //
            "│                 ██████       │  ", //
            "│   █▄           ███████▌      │  ", //
            "│     ▀█        ████████       │  ", //
            "│ ▀▀▀▀▄▄▀      ▐███████        │  ", //
            "│       ▀█▄▄▄  ▐████▀          │  ", //
            "│       ▄█████  ▀▀             │  ", //
            "│       ▀█████ ▐██  ██▌        │  ", //
            "│     ▐██████  ██▌ ▐██▌▐██     │  ", //
            "│      ████▀  ███ ▄███ ███▄    │  ", //
            "│                ▄███ ▄██▀     │  ", //
            "│                              │  ", //
            "└──────────────────────────────┘  ", //
    };

    private static boolean testMode = false;
    private static boolean testModeLLM = false;
    private static boolean failWithException = false;
    private final Map<String, Class<? extends CliTool>> tools = new TreeMap<>();

    /**
     * Indicate that the tools are executed in the context of a JUNIT test. In this mode the tools
     * will NOT take values from the environmental as fallback for command line arguments. Only arguments directly passed to the
     * tool are evaluated. Additionally the tools will fail with an exception without exiting the JVM.
     */
    public static void setTestMode(boolean test) {
        testMode = test;
        testModeLLM = test;
    }

    public static void setTestModeForLLM(boolean test) {
        testModeLLM = test;
    }

    /**
     * Indicates whether or not the tool should fail with an exception or should print out the errors to the command line.
     */
    public static void setFailWithException(boolean fail) {
        failWithException = fail;
    }

    /**
     * Returns whether or not the test mode has been enabled
     */
    public static boolean isTestMode() {
        return testMode;
    }

    public static boolean istTestModeLLM() {
        return testModeLLM;
    }

    public void toolMain(String... args) throws Exception {
        ActivityReporter.Stream streamReporter = new ActivityReporter.Stream(System.out);
        ActivityReporter defaultReporter = new ActivityReporter.Null();
        ActivityReporter reporter = defaultReporter;
        PrintStream output = null;
        PrintStream reporterOutput = null;
        DataFormat dataMode = DataFormat.TEXT;

        boolean verbose = false;
        boolean closeOutput = false;
        RuntimeException exc = null;
        try {
            int toolArgNum = 0;
            for (int i = 0; i < args.length; ++i) {
                if (args[i].startsWith("-")) {
                    switch (args[i]) {
                        case "-vv":
                            streamReporter.setVerboseSummary(true);
                            reporter = null;
                            verbose = true;
                            break;
                        case "-v":
                            verbose = true;
                            break;
                        case "-q":
                            // explicit new instance to signal explicit quiet-ness
                            reporter = new ActivityReporter.Null();
                            verbose = false;
                            break;
                        case "-o":
                            String of = args[++i];
                            closeOutput = true;
                            output = new PrintStream(new File(of), StandardCharsets.UTF_8.name());
                            break;
                        case "-op":
                            String opf = args[++i];
                            reporterOutput = new PrintStream(new File(opf), StandardCharsets.UTF_8.name());
                            streamReporter = new ActivityReporter.Stream(reporterOutput);
                            streamReporter.setVerboseSummary(reporter == null);
                            break;
                        case "--csv":
                            dataMode = DataFormat.CSV;
                            break;
                        case "--json":
                            dataMode = DataFormat.JSON;
                            break;
                        case "--version":
                            String version = VersionHelper.getVersion().toString();
                            System.out.println(version);
                            return;
                        default:
                            break;
                    }
                } else {
                    toolArgNum = i;
                    break;
                }
            }

            if (reporter == null) {
                reporter = streamReporter;
            }
            if (output == null) {
                output = System.out;
            }

            if (args.length <= toolArgNum || tools.get(args[toolArgNum]) == null) {
                int logo = 0;
                System.out.println(LOGO[logo++]);
                System.out.println(LOGO[logo++] + "BHive & BDeploy");
                System.out.println(LOGO[logo++] + "──────────────────────────────────────────────────────────");
                System.out.println(LOGO[logo++] + "Usage: $0 <options...> <tool> <args...>");
                System.out.println(LOGO[logo++]);
                System.out.println(LOGO[logo++] + "Options:");
                System.out.println(LOGO[logo++] + "  -q      Be quiet - no progress reporting.");
                System.out.println(LOGO[logo++] + "  -v|-vv  Be verbose. No effect if -q is given as well.");
                System.out.println(LOGO[logo++] + "  -o <f>  Write output to file <f>. No effect on progress output.");
                System.out.println(LOGO[logo++] + "  -op <f> Write progress tracking output to file <f>. No");
                System.out.println(LOGO[logo++] + "          effect on normal output.");
                System.out.println(LOGO[logo++] + "  --csv   Write data tables in CSV format");
                System.out.println(LOGO[logo++] + "  --json  Write data tables in JSON format");
                System.out.println(LOGO[logo++]);
                System.out.println("Tools:");
                System.out.println();
                Map<String, List<Entry<String, Class<? extends CliTool>>>> grouped = tools.entrySet().stream()
                        .collect(Collectors.groupingBy(e -> getToolCategory(e.getValue()), TreeMap::new, Collectors.toList()));
                grouped.entrySet().stream().forEach(group -> {
                    System.out.println("  " + group.getKey() + ":");

                    DataTable table = DataFormat.TEXT.createTable(System.out);
                    table.setIndentHint(5).setHideHeadersHint(true).setLineWrapHint(true);
                    table.column("Tool", 20).column("Description", 60);

                    group.getValue().stream().forEach(e -> {
                        List<String> names = namesOf(e.getValue());
                        if (names.get(0).equals(e.getKey())) {
                            Help h = e.getValue().getAnnotation(Help.class);
                            table.row().cell(e.getKey()).cell(h.value() != null ? h.value() : "").build();
                        } else {
                            table.row().cell(e.getKey()).cell("DEPRECATED. Alias for '" + names.get(0) + "'").build();
                        }
                    });
                    table.render();

                    System.out.println();
                });

                if (failWithException || testMode) {
                    throw new IllegalArgumentException("Wrong number of arguments");
                } else {
                    System.exit(1);
                }
            }

            CliTool instance = getTool(Arrays.copyOfRange(args, toolArgNum, args.length));
            Class<? extends CliTool> clazz = instance.getClass();
            ToolDefaultVerbose defVerbose = clazz.getAnnotation(ToolDefaultVerbose.class);
            if (defVerbose != null) {
                // switch to verbose, if we aren't already explicitly set up.
                if (verbose || (verbose && reporter == streamReporter)) {
                    // explicit verbose -v || -vv
                } else if (reporter != defaultReporter) {
                    // explicit quiet -q
                } else {
                    // nothing explicit, respect annotation.
                    verbose = true;

                    if (defVerbose.value()) {
                        reporter = streamReporter;
                    }
                }
            }

            if (reporter == streamReporter) {
                streamReporter.beginReporting();
            }

            instance.setOutput(output);
            instance.setVerbose(verbose);
            instance.setActivityReporter(reporter);
            instance.setDataFormat(dataMode);

            if (instance instanceof ConfiguredCliTool) {
                if (((ConfiguredCliTool<?>) instance).getRawConfiguration().getAllRawObjects().containsKey("help")) {
                    ((ConfiguredCliTool<?>) instance).helpAndFail("Help:");
                }
            }

            try (Timer.Context timer = Metrics.getMetric(MetricGroup.CLI)
                    .timer(instance.getClass().getSimpleName() + "/" + args[toolArgNum]).time()) {
                RenderableResult result = instance.run();

                if (result != null) {
                    result.render();
                }
            }
        } catch (RuntimeException t) {
            exc = t;
        } finally {
            if (closeOutput && output != null) {
                output.close();
            }

            streamReporter.stopReporting();
            if (reporterOutput != null) {
                reporterOutput.close();
            }
        }

        // don't system.exit in unit tests
        if (failWithException || testMode) {
            if (exc != null) {
                throw exc;
            } else {
                return; // normal return in tests.
            }
        }

        // explicit exit, otherwise non-daemon async jersey threads block.
        // The reason is not jersey itself, but it's usage of ForkJoinPool.commonPool.
        if (exc != null) {
            DataResult result = dataMode.createResult(output);
            result.setException(exc);
            result.render();

            System.exit(1);
        }
        System.exit(0);
    }

    private String getToolCategory(Class<? extends CliTool> clazz) {
        ToolCategory annotation = clazz.getAnnotation(ToolCategory.class);
        if (annotation == null || annotation.value() == null) {
            return "Ungrouped Tools";
        }
        return annotation.value();
    }

    /**
     * Retrieve the tool instance for the given command line. The tool is already
     * configured from command line arguments.
     */
    public CliTool getTool(String... args) throws Exception {
        Class<? extends CliTool> tool = tools.get(args[0]);
        CliTool instance = tool.getDeclaredConstructor().newInstance();

        if (instance instanceof ConfiguredCliTool) {
            Configuration cfg = new Configuration();
            if (args.length > 1) {
                cfg.add(Arrays.copyOfRange(args, 1, args.length));
            }
            ConfiguredCliTool<?> toConfig = ((ConfiguredCliTool<?>) instance);
            toConfig.setConfig(cfg);
        } else if (instance instanceof NativeCliTool) {
            ((NativeCliTool) instance).setArguments(Arrays.copyOfRange(args, 1, args.length));
        }
        return instance;
    }

    /**
     * @param tool the tool to inspect
     * @return all the names the tool should be known under. The 'official' name is the first in the list, the rest are aliases.
     */
    public static List<String> namesOf(Class<? extends CliTool> tool) {
        CliTool.CliName name = tool.getAnnotation(CliTool.CliName.class);
        if (name == null || name.value() == null) {
            throw new IllegalStateException("Cannot find annotation on " + tool);
        }

        List<String> result = new ArrayList<>();
        result.add(name.value());

        if (name.alias().length > 0) {
            result.addAll(Arrays.asList(name.alias()));
        }
        return result;
    }

    public void register(Class<? extends CliTool> tool) {
        namesOf(tool).forEach(name -> tools.put(name, tool));
    }

    /**
     * Base class for all CLI tools.
     */
    public abstract static class CliTool {

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface CliName {

            String value();

            String[] alias() default {};
        }

        private ActivityReporter reporter;
        private PrintStream output = System.out;
        private boolean verbose;
        private DataFormat dataFormat = DataFormat.TEXT;

        /**
         * Set an alternative {@link ActivityReporter}.
         */
        public void setActivityReporter(ActivityReporter reporter) {
            this.reporter = reporter;
        }

        /**
         * @return the current {@link ActivityReporter}
         */
        protected ActivityReporter getActivityReporter() {
            return reporter;
        }

        /**
         * Sets the mode to render tables with.
         */
        public void setDataFormat(DataFormat dataMode) {
            this.dataFormat = dataMode;
        }

        /**
         * @return the current mode tables are rendered in.
         */
        protected DataFormat getDataFormat() {
            return this.dataFormat;
        }

        protected DataTable createDataTable() {
            return dataFormat.createTable(output);
        }

        protected DataResult createSuccess() {
            return createResultWithMessage("Success");
        }

        protected DataResult createNoOp() {
            return createResultWithMessage("Nothing to do (missing arguments?)");
        }

        protected DataResult createEmptyResult() {
            return dataFormat.createResult(output);
        }

        protected DataResult createResultWithMessage(String message) {
            DataResult result = dataFormat.createResult(output);
            result.setMessage(message);
            return result;
        }

        protected DataResult createResultWithFields(String message, Map<String, String> fields) {
            DataResult result = dataFormat.createResult(output);
            result.setMessage(message);
            fields.entrySet().forEach(e -> result.addField(e.getKey(), e.getValue()));
            return result;
        }

        /**
         * Set an alternative output destination.
         */
        public void setOutput(PrintStream output) {
            this.output = output;
        }

        /**
         * Instructs tools which have some verbose output to print it.
         */
        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        /**
         * @return whether verbose output should be produced
         */
        protected boolean isVerbose() {
            return verbose;
        }

        /**
         * @return the current output destination. Can be used for any output on the
         *         CLI.
         */
        protected PrintStream out() {
            return output;
        }

        /**
         * Execute the tool.
         */
        public abstract RenderableResult run();
    }

    /**
     * Base class for tools which require access to the actual command line they have been passed.
     */
    public abstract static class NativeCliTool extends CliTool {

        private String[] args;

        private void setArguments(String[] args) {
            this.args = args;
        }

        @Override
        public final RenderableResult run() {
            return run(args);
        }

        protected abstract RenderableResult run(String[] args);

    }

    /**
     * Base class for tools that accept additional configuration.
     *
     * @see Configuration
     */
    @SuppressFBWarnings
    public abstract static class ConfiguredCliTool<T extends Annotation> extends CliTool {

        private final Class<T> configClass;
        private Configuration config;

        /**
         * @param configClass the configuration annotation class to use.
         */
        public ConfiguredCliTool(Class<T> configClass) {
            this.configClass = configClass;
        }

        /**
         * @param config the configuration proxy prepared by the framework
         */
        private void setConfig(Configuration config) {
            this.config = config;
        }

        /**
         * @return the type of the primary configuration annotation (the one passed in
         *         the constructor).
         */
        protected Class<? extends Annotation> getPrimaryConfigClass() {
            return configClass;
        }

        /**
         * @return all {@link Annotation}s for which to render help output.
         */
        protected List<Class<? extends Annotation>> getConfigsForHelp() {
            return Collections.singletonList(configClass);
        }

        /**
         * Create a specific configuration from the underlying raw configuration using
         * the given annotation.
         */
        protected <X extends Annotation> X getConfig(Class<X> clazz) {
            try {
                return config.get(clazz);
            } catch (ConfigValidationException e) {
                out().println("Validation Issues Exist:");
                for (Throwable t : e.getSuppressed()) {
                    out().println("  " + t.getMessage());
                }
                out().println();
                throw e;
            }
        }

        /**
         * @return the underlying raw configuration.
         */
        protected Configuration getRawConfiguration() {
            return config;
        }

        @Override
        public final RenderableResult run() {
            return run(getConfig(configClass));
        }

        /**
         * @param argument display help text and fail the tool if the argument is
         *            <code>null</code>.
         */
        protected void helpAndFailIfMissing(Object argument, String message) {
            if (argument == null || (argument.getClass().isArray() && Array.getLength(argument) == 0)
                    || ((argument instanceof String) && ((String) argument).isEmpty())) {
                helpAndFail("ERROR: " + message);
            }
        }

        /**
         * Display the help text and fail the tool (<code>System.exit(1)</code>).
         */
        protected void helpAndFail(String message) {
            out().println();
            out().println(message);
            out().println();
            DataTable table = getDataFormat().createTable(out());
            String name = getClass().getSimpleName();
            CliName annotation = getClass().getAnnotation(CliName.class);
            if (annotation != null && annotation.value() != null) {
                name = annotation.value();
            }
            Help help = getClass().getAnnotation(Help.class);
            table.setCaption(name + (help != null && help.value() != null ? (": " + help.value()) : ""));
            table.setLineWrapHint(true).setIndentHint(2);
            table.column("Argument", 20).column("Description", 70).column("Default", 10);

            List<Class<? extends Annotation>> configsForHelp = getConfigsForHelp();
            for (int i = 0; i < configsForHelp.size(); ++i) {
                Class<? extends Annotation> x = configsForHelp.get(i);
                Configuration.formatHelp(x, table);
                if (i != (configsForHelp.size() - 1)) {
                    table.addHorizontalRuler();
                }
            }
            table.render();

            if (failWithException || testMode) {
                throw new IllegalArgumentException(message);
            } else {
                System.exit(1);
            }
        }

        /**
         * Run the configured tool using configuration from the command line.
         *
         * @param config the configuration instance for the tool.
         */
        protected abstract RenderableResult run(T config);
    }

}
