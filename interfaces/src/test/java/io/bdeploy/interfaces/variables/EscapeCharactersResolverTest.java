package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VariableResolver;

class EscapeCharactersResolverTest {

    private static final VariableResolver PARENT1 = s -> s;
    private static final VariableResolver PARENT2 = String::toUpperCase;
    private static final VariableResolver PARENT3 = s -> "###" + s + "###";

    @Test
    void testEscapeYamlCharactersResolver() {
        var resolver1 = new EscapeYamlCharactersResolver(PARENT1);
        assertEquals("1", resolver1.doResolve("1"));
        assertEquals("test", resolver1.doResolve("test"));
        assertEquals("\"a_b\"", resolver1.doResolve("a_b"));
        assertEquals("\"property: value\"", resolver1.doResolve("property: value"));

        var resolver2 = new EscapeYamlCharactersResolver(PARENT2);
        assertEquals("1", resolver2.doResolve("1"));
        assertEquals("TEST", resolver2.doResolve("test"));
        assertEquals("\"A_B\"", resolver2.doResolve("a_b"));
        assertEquals("\"PROPERTY: VALUE\"", resolver2.doResolve("property: value"));

        var resolver3 = new EscapeYamlCharactersResolver(PARENT3);
        assertEquals("###1###", resolver3.doResolve("1"));
        assertEquals("###test###", resolver3.doResolve("test"));
        assertEquals("\"###a_b###\"", resolver3.doResolve("a_b"));
        assertEquals("\"###property: value###\"", resolver3.doResolve("property: value"));
    }

    @Test
    void testEscapeXmlCharactersResovler() {
        var resolver1 = new EscapeXmlCharactersResolver(PARENT1);
        assertEquals("1", resolver1.doResolve("1"));
        assertEquals("test", resolver1.doResolve("test"));
        assertEquals("&lt;tag&gt;&amp;quot;fish &amp; chips&amp;quot;&lt;/tag&gt;",
                resolver1.doResolve("<tag>\"fish & chips\"</tag>"));
        assertEquals("&lt;tag&gt;&amp;apos;apostrophe&amp;apos;&lt;/tag&gt;",//
                resolver1.doResolve("<tag>'apostrophe'</tag>"));

        var resolver2 = new EscapeXmlCharactersResolver(PARENT2);
        assertEquals("1", resolver2.doResolve("1"));
        assertEquals("TEST", resolver2.doResolve("test"));
        assertEquals("&lt;TAG&gt;&amp;quot;FISH &amp; CHIPS&amp;quot;&lt;/TAG&gt;",
                resolver2.doResolve("<tag>\"fish & chips\"</tag>"));
        assertEquals("&lt;TAG&gt;&amp;apos;APOSTROPHE&amp;apos;&lt;/TAG&gt;",//
                resolver2.doResolve("<tag>'apostrophe'</tag>"));

        var resolver3 = new EscapeXmlCharactersResolver(PARENT3);
        assertEquals("###1###", resolver3.doResolve("1"));
        assertEquals("###test###", resolver3.doResolve("test"));
        assertEquals("###&lt;tag&gt;&amp;quot;fish &amp; chips&amp;quot;&lt;/tag&gt;###",
                resolver3.doResolve("<tag>\"fish & chips\"</tag>"));
        assertEquals("###&lt;tag&gt;&amp;apos;apostrophe&amp;apos;&lt;/tag&gt;###",//
                resolver3.doResolve("<tag>'apostrophe'</tag>"));
    }

    @Test
    void testEscapeJsonCharactersResolver() {
        var resolver1 = new EscapeJsonCharactersResolver(PARENT1);
        assertEquals("1", resolver1.doResolve("1"));
        assertEquals("test", resolver1.doResolve("test"));
        assertEquals("\\\\\"double quotes\\\\\"", resolver1.doResolve("\"double quotes\""));
        assertEquals("\\\\\"\\\\ttab\\\\nnew line\\\\\"", resolver1.doResolve("\"\ttab\nnew line\""));

        var resolver2 = new EscapeJsonCharactersResolver(PARENT2);
        assertEquals("1", resolver2.doResolve("1"));
        assertEquals("TEST", resolver2.doResolve("test"));
        assertEquals("\\\\\"DOUBLE QUOTES\\\\\"", resolver2.doResolve("\"double quotes\""));
        assertEquals("\\\\\"\\\\tTAB\\\\nNEW LINE\\\\\"", resolver2.doResolve("\"\ttab\nnew line\""));

        var resolver3 = new EscapeJsonCharactersResolver(PARENT3);
        assertEquals("###1###", resolver3.doResolve("1"));
        assertEquals("###test###", resolver3.doResolve("test"));
        assertEquals("###\\\\\"double quotes\\\\\"###", resolver3.doResolve("\"double quotes\""));
        assertEquals("###\\\\\"\\\\ttab\\\\nnew line\\\\\"###", resolver3.doResolve("\"\ttab\nnew line\""));
    }
}
