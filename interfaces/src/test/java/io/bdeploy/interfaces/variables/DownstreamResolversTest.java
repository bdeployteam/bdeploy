package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VariableResolver;

class DownstreamResolversTest {

    private static final VariableResolver PARENT1 = s -> s;
    private static final VariableResolver PARENT2 = String::toUpperCase;
    private static final VariableResolver PARENT3 = s -> "###" + s + "###";

    @Test
    void testFileUriResolver() {
        Function<Path, String> toUri = p -> p.toUri().toString();

        String input1 = Path.of("a", "b", "c").toString();
        String input2 = Path.of("a", "b", "c.txt").toString();
        String input3 = Path.of("a", "b_b b", "c.txt").toString();

        Path p1_1 = Path.of("a", "b", "c");
        Path p1_2 = Path.of("a", "b", "c.txt");
        Path p1_3 = Path.of("a", "b_b b", "c.txt");
        var resolver1 = new FileUriResolver(PARENT1);
        assertEquals(toUri.apply(p1_1), resolver1.doResolve(input1));
        assertEquals(toUri.apply(p1_2), resolver1.doResolve(input2));
        assertEquals(toUri.apply(p1_3), resolver1.doResolve(input3));

        Path p2_1 = Path.of("A", "B", "C");
        Path p2_2 = Path.of("A", "B", "C.TXT");
        Path p2_3 = Path.of("A", "B_B B", "C.TXT");
        var resolver2 = new FileUriResolver(PARENT2);
        assertEquals(toUri.apply(p2_1), resolver2.doResolve(input1));
        assertEquals(toUri.apply(p2_2), resolver2.doResolve(input2));
        assertEquals(toUri.apply(p2_3), resolver2.doResolve(input3));

        Path p3_1 = Path.of("###a", "b", "c###");
        Path p3_2 = Path.of("###a", "b", "c.txt###");
        Path p3_3 = Path.of("###a", "b_b b", "c.txt###");
        var resolver3 = new FileUriResolver(PARENT3);
        assertEquals(toUri.apply(p3_1), resolver3.doResolve(input1));
        assertEquals(toUri.apply(p3_2), resolver3.doResolve(input2));
        assertEquals(toUri.apply(p3_3), resolver3.doResolve(input3));
    }

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
