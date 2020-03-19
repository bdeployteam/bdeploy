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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.codahale.metrics.Timer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.ConfigValidationException;
import io.bdeploy.common.cfg.Configuration;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.util.VersionHelper;

/**
 * Main CLI entry point base class.
 */
@SuppressFBWarnings
public abstract class ToolBase {

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
                System.out.println("Usage: $0 [-q|-v|-o <file>] <tool> <args...>");
                System.out.println("  -q      Be quiet - no progress reporting.");
                System.out.println(
                        "  -v      Be verbose - show a summary of tasks and durations. No effect if -q is given as well.");
                System.out.println("  -o <f>  Write output to file <f>. No effect on progress output.");
                System.out.println("  -op <f> Write progress tracking output to file <f>. No effect on normal output.");
                System.out.println("  Tools:");
                tools.entrySet().stream().forEach(e -> {
                    Help h = e.getValue().getAnnotation(Help.class);
                    if (h != null) {
                        System.out.println("  " + String.format("%1$12s: %2$s", e.getKey(), h.value()));
                    } else {
                        System.out.println("  " + String.format("%1$12s:", e.getKey()));
                    }
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

            if (instance instanceof ConfiguredCliTool) {
                if (((ConfiguredCliTool<?>) instance).getRawConfiguration().getAllRawObjects().containsKey("help")) {
                    ((ConfiguredCliTool<?>) instance).helpAndFail("Help:");
                }
            }

            try (Timer.Context timer = Metrics.getMetric(MetricGroup.CLI)
                    .timer(instance.getClass().getSimpleName() + "/" + args[toolArgNum]).time()) {
                instance.run();
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
            // just to make absolutely sure.
            if (output == null) {
                output = System.out;
            }

            if (verbose) {
                exc.printStackTrace(output);
            } else {
                Throwable c = exc;
                while (c != null) {
                    output.println("ERROR: " + c.toString());

                    Throwable next = c.getCause();
                    if (next == c) {
                        break;
                    }

                    c = next;
                }
            }

            System.exit(1);
        }
        System.exit(0);
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
     * For testing only; returns the name under which a certain {@link CliTool} is
     * registered.
     */
    public static String nameOf(Class<? extends CliTool> tool) {
        CliTool.CliName name = tool.getAnnotation(CliTool.CliName.class);
        if (name == null || name.value() == null) {
            throw new IllegalStateException("Cannot find annotation on " + tool);
        }
        return name.value();
    }

    protected void register(Class<? extends CliTool> tool) {
        tools.put(nameOf(tool), tool);
    }

    /**
     * Base class for all CLI tools.
     */
    public abstract static class CliTool {

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface CliName {

            String value();
        }

        private ActivityReporter reporter;
        private PrintStream output = System.out;
        private boolean verbose;

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
        public abstract void run();
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
        public final void run() {
            run(args);
        }

        protected abstract void run(String[] args);

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
        protected Collection<Class<? extends Annotation>> getConfigsForHelp() {
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
        public final void run() {
            run(getConfig(configClass));
        }

        /**
         * @param argument display help text and fail the tool if the argument is
         *            <code>null</code>.
         */
        protected void helpAndFailIfMissing(Object argument, String message) {
            if (argument == null || (argument.getClass().isArray() && Array.getLength(argument) == 0)
                    || ((argument instanceof String) && ((String) argument).isEmpty())) {
                helpAndFail(message);
            }
        }

        /**
         * Display the help text and fail the tool (<code>System.exit(1)</code>).
         */
        protected void helpAndFail(String message) {
            System.out.println(message);
            System.out.println();
            System.out.println("Usage: " + getClass().getSimpleName() + " <args...>");
            for (Class<? extends Annotation> x : getConfigsForHelp()) {
                Configuration.formatHelp(x, System.out, "  ");
            }
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
        protected abstract void run(T config);
    }

}
