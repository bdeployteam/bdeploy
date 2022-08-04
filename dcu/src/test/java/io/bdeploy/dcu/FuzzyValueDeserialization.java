package io.bdeploy.dcu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;

public class FuzzyValueDeserialization {

    private static final String V_OLD = """
            {
                "uid": "param1",
                "value": "value1"
            }
            """;

    private static final String V_NEW = """
            {
                "uid": "param2",
                "value": {
                    "value": "value2",
                    "linkExpression": "expression2"
                }
            }
            """;

    @Test
    void testFuzzyDeserialization() {
        ParameterConfiguration config1 = StorageHelper.fromRawBytes(V_OLD.getBytes(StandardCharsets.UTF_8),
                ParameterConfiguration.class);

        assertEquals("param1", config1.uid);
        assertEquals("value1", config1.value.value);

        ParameterConfiguration config2 = StorageHelper.fromRawBytes(V_NEW.getBytes(StandardCharsets.UTF_8),
                ParameterConfiguration.class);

        assertEquals("param2", config2.uid);
        assertEquals("value2", config2.value.value);
        assertEquals("expression2", config2.value.linkExpression);
    }

}
