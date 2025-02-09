package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VariableResolver;

class ConditionalExpressionResolverTest {

    @Test
    void testConditionalExpressionResolverWithFixedParentReturnValue() {
        Set<VariableResolver> trueParents =//
                Set.of(s -> "true", s -> "TRUE", s -> "0", s -> "1", s -> "-1", s -> "yes", s -> "no");
        Set<VariableResolver> falseParents =//
                Set.of(s -> null, s -> "", s -> " ", s -> "false", s -> "FALSE");

        var trueResolvers = trueParents.stream().map(ConditionalExpressionResolver::new).collect(Collectors.toSet());
        var falseResolvers = falseParents.stream().map(ConditionalExpressionResolver::new).collect(Collectors.toSet());

        // Test invalid expressions
        Set<String> invalidExpressions = Set.of("invalid expression",//
                "?invalid expression", "invalid?expression", "invalid expression?",//
                ":invalid expression", "invalid:expression", "invalid expression:",//
                ":?invalid expression", "invalid:?expression", "invalid expression:?",//
                "??", "::", "::?", "::??");
        invalidExpressions.forEach(
                invalidExpression -> trueResolvers.forEach(resolver -> assertNull(resolver.doResolve(invalidExpression))));
        invalidExpressions.forEach(
                invalidExpression -> falseResolvers.forEach(resolver -> assertNull(resolver.doResolve(invalidExpression))));

        // Test valid expressions
        trueResolvers.forEach(resolver -> assertEquals("",//
                resolver.doResolve("?:")));
        trueResolvers.forEach(resolver -> assertEquals("",//
                resolver.doResolve("?::")));
        trueResolvers.forEach(resolver -> assertEquals("?",//
                resolver.doResolve("??::")));

        falseResolvers.forEach(resolver -> assertEquals("",//
                resolver.doResolve("?:")));
        falseResolvers.forEach(resolver -> assertEquals(":",//
                resolver.doResolve("?::")));
        falseResolvers.forEach(resolver -> assertEquals(":",//
                resolver.doResolve("??::")));

        trueResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve("x? : ")));
        trueResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve(" ?x: ")));
        trueResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve(" ? :x")));
        trueResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve("x?x: ")));
        trueResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve("x? :x")));
        trueResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve(" ?x:x")));
        trueResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve("x?x:x")));

        falseResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve("x? : ")));
        falseResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve(" ?x: ")));
        falseResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve(" ? :x")));
        falseResolvers.forEach(resolver -> assertEquals(" ",//
                resolver.doResolve("x?x: ")));
        falseResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve("x? :x")));
        falseResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve(" ?x:x")));
        falseResolvers.forEach(resolver -> assertEquals("x",//
                resolver.doResolve("x?x:x")));
    }

    @Test
    void testConditionalExpressionResolverWithDynamicParentReturnValue() {
        VariableResolver parent1 = s -> Integer.valueOf(s) >= 3 ? "true" : "false";
        var resolver1 = new ConditionalExpressionResolver(parent1);
        assertEquals("b", resolver1.doResolve("-1?a:b"));
        assertEquals("b", resolver1.doResolve("0?a:b"));
        assertEquals("b", resolver1.doResolve("1?a:b"));
        assertEquals("b", resolver1.doResolve("2?a:b"));
        assertEquals("a", resolver1.doResolve("3?a:b"));
        assertEquals("a", resolver1.doResolve("4?a:b"));
        assertEquals("a", resolver1.doResolve("5?a:b"));

        VariableResolver parent2 = s -> s.equalsIgnoreCase("foo") ? "true" : "false";
        var resolver2 = new ConditionalExpressionResolver(parent2);
        assertEquals("a", resolver2.doResolve("foo?a:b"));
        assertEquals("a", resolver2.doResolve("FOO?a:b"));
        assertEquals("a", resolver2.doResolve("FoO?a:b"));
        assertEquals("b", resolver2.doResolve("foobar?a:b"));
        assertEquals("b", resolver2.doResolve("true?a:b"));
        assertEquals("b", resolver2.doResolve("0?a:b"));
    }
}
