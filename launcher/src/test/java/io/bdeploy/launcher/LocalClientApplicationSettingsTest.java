package io.bdeploy.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;

/**
 * Contains unit tests for {@link LocalClientApplicationSettings}.
 */
public class LocalClientApplicationSettingsTest {

    private static final ClickAndStartDescriptor descr1 = new ClickAndStartDescriptor();
    private static final ClickAndStartDescriptor descr2 = new ClickAndStartDescriptor();
    static {
        descr1.host = new RemoteService(URI.create("test://uri1"));
        descr1.groupId = "grp1";
        descr1.instanceId = "inst1";
        descr1.applicationId = "app1";

        descr2.host = new RemoteService(URI.create("test://uri2"));
        descr2.groupId = "grp2";
        descr2.instanceId = "inst2";
        descr2.applicationId = "app2";
    }

    @Test
    void testAutostart() {
        LocalClientApplicationSettings settings = new LocalClientApplicationSettings();
        assertNull(settings.getAutostartEnabled(descr1));
        settings.putAutostartEnabled(descr1, false);
        assertFalse(settings.getAutostartEnabled(descr1));
        settings.putAutostartEnabled(descr1, true);
        assertTrue(settings.getAutostartEnabled(descr1));
    }

    @Test
    void testStartScripts() {
        testScripts(//
                (settings, scriptName, scriptInfo, override) -> settings.putStartScriptInfo(scriptName, scriptInfo, override),
                (settings, scriptName) -> settings.getStartScriptInfo(scriptName));
    }

    @Test
    void testFileAssocScripts() {
        testScripts(//
                (settings, scriptName, scriptInfo, override) -> settings.putFileAssocScriptInfo(scriptName, scriptInfo, override),
                (settings, scriptName) -> settings.getFileAssocScriptInfo(scriptName));
    }

    private static void testScripts(QuadFunction<LocalClientApplicationSettings, String, ScriptInfo, Boolean, Boolean> setter,
            BiFunction<LocalClientApplicationSettings, String, ScriptInfo> putter) {
        LocalClientApplicationSettings settings = new LocalClientApplicationSettings();

        String scriptName = "script";
        ScriptInfo scriptInfo1 = new ScriptInfo(scriptName, descr1);
        ScriptInfo scriptInfo2 = new ScriptInfo(scriptName, descr2);

        // Map must be empty at the start
        assertNull(putter.apply(settings, scriptName));

        // Put scriptName with scriptInfo1- it must be active
        assertTrue(setter.apply(settings, scriptName, scriptInfo1, false));
        assertEquals(scriptInfo1, putter.apply(settings, scriptName));

        // Put scriptName with scriptInfo2 without override -> no change
        assertFalse(setter.apply(settings, scriptName, scriptInfo2, false));
        assertEquals(scriptInfo1, putter.apply(settings, scriptName));

        // Put scriptName with scriptInfo2 with override -> new descriptor is active
        assertTrue(setter.apply(settings, scriptName, scriptInfo2, true));
        assertEquals(scriptInfo2, putter.apply(settings, scriptName));
    }

    @FunctionalInterface
    private interface QuadFunction<A, B, C, D, R> {

        /**
         * Applies this function to the given arguments.
         *
         * @param a the first function argument
         * @param b the second function argument
         * @param c the third function argument
         * @param d the fourth function argument
         * @return the function result
         */
        R apply(A a, B b, C c, D d);
    }
}
