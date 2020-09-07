package io.bdeploy.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.RenderableResult;

public class TestCliTool implements ParameterResolver {

    private static final Logger log = LoggerFactory.getLogger(TestCliTool.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface CliArgs {

        public String[] value() default {};
    }

    private final ActivityReporter defaultReporter = new ActivityReporter.Stream(System.out);
    private final ToolBase cli;

    public TestCliTool(ToolBase cli) {
        this.cli = cli;

        // make sure we're in test mode.
        ToolBase.setTestMode(true);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return CliTool.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Optional<CliArgs> args = parameterContext.findAnnotation(CliArgs.class);
        String[] arr = new String[0];
        if (args.isPresent()) {
            arr = args.get().value();
        }
        return getTool(defaultReporter, (Class<? extends CliTool>) parameterContext.getParameter().getType(), arr);
    }

    public <T extends CliTool> String[] execute(Class<T> tool, String... args) throws IOException {
        return readOutput(getTool(defaultReporter, tool, args));
    }

    private <T extends CliTool> String[] readOutput(T tool) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            tool.setOutput(new PrintStream(os));
            tool.setDataFormat(DataFormat.JSON);
            RenderableResult result = tool.run();
            if (result != null) {
                result.render();
            }

            String plainOutput = os.toString();

            log.info(tool.getClass().getSimpleName() + " output:\n" + plainOutput);

            return plainOutput.split("\\r?\\n");
        }
    }

    private CliTool getTool(ActivityReporter reporter, Class<? extends CliTool> tool, String... args) {
        String name = ToolBase.nameOf(tool);
        if (name == null) {
            throw new IllegalArgumentException("Unknown tool: " + tool);
        }

        String[] argCopy = new String[args.length + 1];
        argCopy[0] = name;
        System.arraycopy(args, 0, argCopy, 1, args.length);

        try {
            CliTool result = cli.getTool(argCopy);
            result.setActivityReporter(reporter);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot creat tool", e);
        }
    }

}
