package io.bdeploy.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import io.bdeploy.common.cfg.ConfigValidationException;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.ToolBase.CliTool;
import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.JacksonHelper;

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
        String[] arr = {};
        if (args.isPresent()) {
            arr = args.get().value();
        }
        return getTool(defaultReporter, (Class<? extends CliTool>) parameterContext.getParameter().getType(), arr);
    }

    public <T extends CliTool> StructuredOutput execute(Class<T> tool, String... args) throws IOException {
        return readOutput(getTool(defaultReporter, tool, args));
    }

    private static <T extends CliTool> StructuredOutput readOutput(T tool) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            tool.setOutput(new PrintStream(os));
            tool.setDataFormat(DataFormat.JSON);
            try {
                RenderableResult result = tool.run();
                if (result != null) {
                    result.render();
                }
            } catch (ConfigValidationException e) {
                // need to log issues. build does not output suppressed exceptions.
                for (Throwable t : e.getSuppressed()) {
                    t.printStackTrace();
                }
                throw e;
            }

            String plainOutput = os.toString();

            log.info(tool.getClass().getSimpleName() + " output:\n" + plainOutput);

            return new StructuredOutput(plainOutput);
        }
    }

    private CliTool getTool(ActivityReporter reporter, Class<? extends CliTool> tool, String... args) {
        String name = ToolBase.namesOf(tool).get(0);
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

    public static final class StructuredOutputRow {

        private final Map<String, String> data;

        public StructuredOutputRow(Map<String, String> data) {
            this.data = data;
        }

        public String get(String column) {
            assertTrue(data.containsKey(column));
            return data.get(column);
        }

    }

    public static final class StructuredOutput {

        private final String plain;
        private final List<Map<String, String>> data;

        public StructuredOutput(String plain) throws JsonProcessingException {
            this.plain = plain;

            if (plain.startsWith("[\n") || plain.startsWith("[\r\n")) {
                data = JacksonHelper.getDefaultJsonObjectMapper().readValue(plain,
                        new TypeReference<List<Map<String, String>>>() {
                        });
            } else if (plain.startsWith("{\n") || plain.startsWith("{\r\n")) {
                data = Collections.singletonList(
                        JacksonHelper.getDefaultJsonObjectMapper().readValue(plain, new TypeReference<Map<String, String>>() {
                        }));
            } else {
                data = null; // cannot parse freestyle output, which exists :)
            }

        }

        public String[] getRawOutput() {
            return plain.split("\\r?\\n");
        }

        public StructuredOutputRow get(int index) {
            assertNotNull(data);
            assertTrue(data.size() > index);
            return new StructuredOutputRow(data.get(index));
        }

        public List<StructuredOutputRow> getAll() {
            assertNotNull(data);
            return data.stream().map(x -> new StructuredOutputRow(x)).collect(Collectors.toUnmodifiableList());
        }

        public int size() {
            assertNotNull(data);
            return data.size();
        }
    }
}
