package com.mastfrog.java.vogon;

import com.mastfrog.java.vogon.ClassBuilder;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ClassBuilderTest {

    private static final String CMT = "This is a long doc comment that ought to get "
            + "wrapped with the appopriate prefix added to it, because that is the sort of thing "
            + "which doc comments ought to do if done right, right?  I mean, it's a nice thing, I think.";

    @Test
    public void testSomeMethod() {
        String txt = ClassBuilder.forPackage("com.foo.bar").named("MyClass")
                .docComment(CMT)
                .importing("java.util.List")
                .importing("java.io.IOException")
                .withModifier(Modifier.FINAL)
                .withModifier(Modifier.PUBLIC)
                .annotatedWith("ServiceProvider").addArgument("service", "MyClass.class").closeAnnotation()
                .field("DOOHICKEY")
                .withModifier(Modifier.FINAL)
                .withModifier(Modifier.STATIC)
                .withModifier(Modifier.PRIVATE)
                .withInitializer("\"foo\"")
                .ofType("String")
                .method("doSomething")
                .returning("String")
                .addArgument("String", "bar")
                .withModifier(Modifier.PUBLIC).withModifier(Modifier.FINAL)
                .annotatedWith("MimeRegistration").addArgument("position", "32")
                .addStringArgument("value", "text/x-foo")
                .closeAnnotation()
                .body()
                .iff("bar.equals(\"moo\")").returning("\"hey\"").endBlock()
                .elseDo()
                .switchingOn("bar")
                .inStringLiteralCase("hoober")
                .returningStringLiteral("woo hoo").endBlock()
                .inStringLiteralCase("quo\"te")
                .returningStringLiteral("hey hey").endBlock()
                .inDefaultCase()
                .statement("System.out.println(\"uh oh\")").endBlock()
                .build()
                .endBlock()
                .returning("DOOHICKEY").endBlock()
                .innerClass("Wookie")
                .implementing("Iterable<String>")
                .makePublic().makeStatic().makeFinal()
                .method("iterator")
                .throwing("NoClassDefFoundError").throwing("IOException")
                .withModifier(Modifier.PUBLIC)
                .returning("Iterator<String>")
                .body().statement("throw new UnsupportedOperationException()")
                .endBlock()
                .method("doStuff")
                .addArgument("String", "foo")
                .body()
                .declare("result").initializedWith("\"not found\"").as("String")
                .ifCondition().invoke("length").on("foo").notEquals().literal(0)
                .or().stringLiteral("whee").equals().literal("foo")
                .endCondition().returning("wookie").endBlock()
                .elseIf().invoke("length").on("foo").greaterThan().literal(80)
                .endCondition().returningStringLiteral("hello\nyou").endBlock()
                .elseDo().returning("DOOHICKEY")
                .endBlock()
                .returning("foo")
                .endBlock()
                .build()
                .build();

        System.out.println("TEXT:\n" + txt);

    }

}