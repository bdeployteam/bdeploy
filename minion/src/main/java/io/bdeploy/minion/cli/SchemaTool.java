package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.minion.cli.SchemaTool.SchemaConfig;
import io.bdeploy.schema.PublicSchemaGenerator;
import io.bdeploy.schema.PublicSchemaValidator;

@Help("Generate and validate YAML/JSON schemas")
@ToolCategory(MinionServerCli.UTIL_TOOLS)
@CliName(value = "schema")
public class SchemaTool extends ConfiguredCliTool<SchemaConfig> {

    public @interface SchemaConfig {

        @Help("The requested schema for generation or validation")
        Schema schema();

        @Help(value = "Path to non-existing file to generate the schema to.")
        @Validator(NonExistingPathValidator.class)
        String generate();

        @Help("Path to existing file to validate")
        @Validator(ExistingPathValidator.class)
        String validate();

        @Help("List all known schemas")
        boolean list() default false;
    }

    public SchemaTool() {
        super(SchemaConfig.class);
    }

    @Override
    protected RenderableResult run(SchemaConfig config) {
        if (config.list()) {
            for (var schema : Schema.values()) {
                out().println(schema.name());
            }
            return createSuccess();
        }

        helpAndFailIfMissing(config.schema(), "Schema must be given");

        if (config.generate() != null) {
            PublicSchemaGenerator generator = new PublicSchemaGenerator();
            String schema = generator.generateSchema(config.schema());
            try {
                Files.writeString(Paths.get(config.generate()), schema);
            } catch (IOException e) {
                return createResultWithErrorMessage("Cannot write " + config.generate()).setException(e);
            }
        }

        if (config.validate() != null) {
            PublicSchemaValidator validator = new PublicSchemaValidator();
            List<String> messages = validator.validate(config.schema(), Paths.get(config.validate()));

            if (!messages.isEmpty()) {
                DataResult result = createEmptyResult();
                int cnt = 0;
                for (var m : messages) {
                    result.addField("[" + ++cnt + "]", m);
                }

                return result;
            }
        }

        if (config.generate() == null && config.validate() == null && !config.list()) {
            // just print the schema to out().
            PublicSchemaGenerator generator = new PublicSchemaGenerator();
            out().println(generator.generateSchema(config.schema()));
        }

        return createSuccess();
    }

}
