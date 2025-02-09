package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VariableResolver;

class DelayedVariableResolverTest {

    @Test
    void testDelayedVariableResolver() {
        VariableResolver parent1 = s -> s;
        VariableResolver parent2 = s -> s.toUpperCase();
        VariableResolver parent3 = s -> "###" + s + "###";

        var resolver1 = new DelayedVariableResolver(parent1);
        var resolver2 = new DelayedVariableResolver(parent2);
        var resolver3 = new DelayedVariableResolver(parent3);

        assertEquals("", resolver1.doResolve(""));
        assertEquals(" ", resolver1.doResolve(" "));
        assertEquals("{[()]}²³", resolver1.doResolve("{[()]}²³"));
        assertEquals("fooBAR", resolver1.doResolve("fooBAR"));
        assertEquals("0", resolver1.doResolve("0"));
        assertEquals("1", resolver1.doResolve("1"));
        assertEquals("-1", resolver1.doResolve("-1"));

        assertEquals("", resolver2.doResolve(""));
        assertEquals(" ", resolver2.doResolve(" "));
        assertEquals("{[()]}²³", resolver2.doResolve("{[()]}²³"));
        assertEquals("FOOBAR", resolver2.doResolve("fooBAR"));
        assertEquals("0", resolver2.doResolve("0"));
        assertEquals("1", resolver2.doResolve("1"));
        assertEquals("-1", resolver2.doResolve("-1"));

        assertEquals("######", resolver3.doResolve(""));
        assertEquals("### ###", resolver3.doResolve(" "));
        assertEquals("###{[()]}²³###", resolver3.doResolve("{[()]}²³"));
        assertEquals("###fooBAR###", resolver3.doResolve("fooBAR"));
        assertEquals("###0###", resolver3.doResolve("0"));
        assertEquals("###1###", resolver3.doResolve("1"));
        assertEquals("###-1###", resolver3.doResolve("-1"));
    }
}
