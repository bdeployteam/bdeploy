package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VariableResolver;

class EscapeCharactersResolverTest {

    @Test
    void testEscapeYamlCharactersResolver() {
        VariableResolver parent = (str) -> str;
        EscapeYamlCharactersResolver yaml = new EscapeYamlCharactersResolver(parent);

        assertEquals("1", yaml.doResolve("1"));
        assertEquals("test", yaml.doResolve("test"));
        assertEquals("\"a_b\"", yaml.doResolve("a_b"));
        assertEquals("\"property: value\"", yaml.doResolve("property: value"));
    }

    @Test
    void testEscapeXmlCharactersResovler() {
        VariableResolver parent = (str) -> str;
        EscapeXmlCharactersResolver xml = new EscapeXmlCharactersResolver(parent);

        assertEquals("1", xml.doResolve("1"));
        assertEquals("test", xml.doResolve("test"));
        assertEquals("&lt;tag&gt;&amp;quot;fish &amp; chips&amp;quot;&lt;/tag&gt;", xml.doResolve("<tag>\"fish & chips\"</tag>"));
        assertEquals("&lt;tag&gt;&amp;apos;apostrophe&amp;apos;&lt;/tag&gt;", xml.doResolve("<tag>'apostrophe'</tag>"));
    }

    @Test
    void testEscapeJsonCharactersResolver() {
        VariableResolver parent = (str) -> str;
        EscapeJsonCharactersResolver json = new EscapeJsonCharactersResolver(parent);

        assertEquals("1", json.doResolve("1"));
        assertEquals("\\\\\"double quotes\\\\\"", json.doResolve("\"double quotes\""));
        assertEquals("\\\\\"\\\\ttab\\\\nnew line\\\\\"", json.doResolve("\"\ttab\nnew line\""));
    }

}
