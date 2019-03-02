package org.nemesis.registration.codegen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

/**
 * Quick'n'dirty java code generation that takes advantage of lambdas.
 *
 * @author Tim Boudreau
 */
public final class ClassBuilder<T> implements BodyBuilder {

    private final String name;
    private final String pkg;
    private final List<BodyBuilder> members = new LinkedList<>();
    private final Set<String> imports = new TreeSet<>();
    private final Set<Modifier> modifiers = new TreeSet<>();
    private final Function<ClassBuilder<T>, T> converter;
    private String extendsType;
    private final Set<String> implementsTypes = new LinkedHashSet<>();
    private final Set<BodyBuilder> annotations = new LinkedHashSet<>();
    private String docComment;
    private String classType = "class";
    private boolean loggerField;
    private static ThreadLocal<ClassBuilder<?>> CONTEXT = new ThreadLocal<>();
    private ClassBuilder<?> prev;
    boolean generateDebugCode;

    @SuppressWarnings("LeakingThisInConstructor")
    ClassBuilder(Object pkg, Object name, Function<ClassBuilder<T>, T> converter) {
        this.pkg = pkg == null ? null : pkg.toString();
        this.name = name.toString();
        checkTypeName(this.name);
        if (this.pkg != null) {
            for (String component : this.pkg.split("\\.")) {
                checkTypeName(component);
            }
        }
        this.converter = converter;
        if (pkg != null) {
            prev = CONTEXT.get();
            CONTEXT.set(this);
        }
    }

    public ClassBuilder<T> logger() {
        if (pkg == null && CONTEXT.get() != null && CONTEXT.get() != this) {
            CONTEXT.get().logger();
        } else {
            loggerField = true;
            importing("java.util.logging.Logger", "java.util.logging.Level");
        }
        return this;
    }

    public ClassBuilder<T> generateDebugLogCode() {
        this.generateDebugCode = true;
        return this;
    }

    public ClassBuilder<T> insertText(String text) {
        members.add(new Adhoc(text));
        return this;
    }

    public String packageName() {
        return pkg;
    }

    public String className() {
        return name;
    }

    public String fqn() {
        return pkg + "." + name;
    }

    public ClassBuilder<T> extending(String type) {
        if ("interface".equals(classType)) {
            implementsTypes.add(type);
            return this;
        }
        if (extendsType != null) {
            throw new IllegalStateException("Already extending " + extendsType + " - cannot extend " + type);
        }
        this.extendsType = type;
        return this;
    }

    public ClassBuilder<T> annotatedWith(String anno, Consumer<AnnotationBuilder<?>> c) {
        boolean[] built = new boolean[1];
        AnnotationBuilder<?> bldr = annotatedWith(anno, built);
        c.accept(bldr);
        if (!built[0]) {
            throw new IllegalStateException("closeAnnotation() not called");
        }
        return this;
    }

    public AnnotationBuilder<ClassBuilder<T>> annotatedWith(String anno) {
        return annotatedWith(anno, new boolean[1]);
    }

    private AnnotationBuilder<ClassBuilder<T>> annotatedWith(String anno, boolean[] built) {
        checkTypeName(anno);
        return new AnnotationBuilder<>(ab -> {
            annotations.add(ab);
            built[0] = true;
            return ClassBuilder.this;
        }, anno);
    }

    public ClassBuilder<T> implementing(String type) {
        implementsTypes.add(type);
        return this;
    }

    public static ClassBuilder<String> create(Object pkg, Object name) {
        return new ClassBuilder<>(pkg, name, new ClassBuilderStringFunction());
    }

    public static PackageBuilder forPackage(Object pkg) {
        return new PackageBuilder(pkg);
    }

    public ClassBuilder<ClassBuilder<T>> innerClass(String name) {
        return new ClassBuilder<>(null, name, cb -> {
            members.add(cb);
            return ClassBuilder.this;
        });
    }

    public boolean isInterface() {
        return "interface".equals(classType);
    }

    public boolean isEnum() {
        return "enum".equals(classType);
    }

    public boolean isAnnotationType() {
        return "@interface".equals(classType);
    }

    public ClassBuilder<T> makePublic() {
        return addModifier(PUBLIC);
    }

    public ClassBuilder<T> makeStatic() {
        return addModifier(STATIC);
    }

    public ClassBuilder<T> makeFinal() {
        return addModifier(FINAL);
    }

    public ClassBuilder<T> publicStatic() {
        return makePublic().makeStatic();
    }

    public ClassBuilder<T> publicStaticFinal() {
        return publicStatic().makeFinal();
    }

    public ClassBuilder<T> toEnum() {
        classType = "enum";
        return this;
    }

    public ClassBuilder<T> toInterface() {
        classType = "interface";
        return this;
    }

    public ClassBuilder<T> toAnnotationType() {
        classType = "@interface";
        return this;
    }

    public ClassBuilder<T> docComment(Object... cmts) {
        StringBuilder sb = new StringBuilder();
        for (Object o : cmts) {
            sb.append(o);
        }
        return docComment(sb.toString());
    }

    public ClassBuilder<T> docComment(String cmt) {
        if (docComment != null) {
            throw new IllegalStateException("Doc comment already set to '" + docComment + "'");
        }
        this.docComment = cmt;
        return this;
    }

    private final List<ConstructorBuilder<?>> constructors = new LinkedList<>();

    public ClassBuilder<T> constructor(Consumer<ConstructorBuilder<?>> c) {
        boolean[] built = new boolean[1];
        ConstructorBuilder<?> result = constructor(built);
        c.accept(result);
        if (!built[0]) {
            throw new IllegalStateException("Constructor not closed");
        }
        return this;
    }

    public ConstructorBuilder<ClassBuilder<T>> constructor() {
        return constructor(new boolean[1]);
    }

    private ConstructorBuilder<ClassBuilder<T>> constructor(boolean[] built) {
        return new ConstructorBuilder<>(cb -> {
            constructors.add(cb);
            built[0] = true;
            return ClassBuilder.this;
        });
    }

    private EnumConstantBuilder constants;

    public EnumConstantBuilder<ClassBuilder<T>> enumConstants() {
        if (constants != null) {
            return constants;
        }
        return new EnumConstantBuilder<>(ecb -> {
            constants = ecb;
            return ClassBuilder.this;
        });
    }

    public BlockBuilder<ClassBuilder<T>> staticBlock() {
        return staticBlock(new boolean[1]);
    }

    public ClassBuilder<T> staticBlock(Consumer<BlockBuilder<?>> c) {
        boolean[] built = new boolean[1];
        BlockBuilder<ClassBuilder<T>> block = staticBlock(built);
        c.accept(block);
        if (!built[0]) {
            throw new IllegalStateException("endBlock not called on static block - will not be added");
        }
        return this;
    }

    private BlockBuilder<ClassBuilder<T>> staticBlock(boolean[] built) {
        return new BlockBuilder<>(bb -> {
            members.add(new Composite(new Adhoc("static"), bb, new Dnl()));
            built[0] = true;
            return ClassBuilder.this;
        }, true);
    }

    public BlockBuilder<ClassBuilder<T>> block() {
        return block(new boolean[1]);
    }

    public ClassBuilder<T> block(Consumer<BlockBuilder<?>> c) {
        boolean[] built = new boolean[1];
        BlockBuilder<?> bldr = block(built);
        c.accept(bldr);
        if (!built[0]) {
            throw new IllegalStateException("Block not closed");
        }
        return this;
    }

    private BlockBuilder<ClassBuilder<T>> block(boolean[] built) {
        return new BlockBuilder<>(bb -> {
            members.add(bb);
            built[0] = true;
            return this;
        }, true);
    }

    static final class Composite implements BodyBuilder {

        private final BodyBuilder[] contents;

        Composite(BodyBuilder... all) {
            this.contents = all;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            for (int i = 0; i < contents.length; i++) {
                contents[i].buildInto(lines);
            }
        }
    }

    static final class Dnl implements BodyBuilder {

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.doubleNewline();
        }
    }

    public static final class EnumConstantBuilder<T> implements BodyBuilder {

        private final Function<EnumConstantBuilder<T>, T> converter;
        private final Set<BodyBuilder> constants = new LinkedHashSet<>();

        EnumConstantBuilder(Function<EnumConstantBuilder<T>, T> converter) {
            this.converter = converter;
        }

        public EnumConstantBuilder<T> add(String name) {
            constants.add(new Adhoc(name));
            return this;
        }

        public InvocationBuilder<EnumConstantBuilder<T>> addWithArgs(String name) {
            return new InvocationBuilder<>(ib -> {
                constants.add(ib);
                return EnumConstantBuilder.this;
            }, name);
        }

        public T endEnumConstants() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            Iterator<BodyBuilder> it = constants.iterator();
            while (it.hasNext()) {
                BodyBuilder bb = it.next();
                bb.buildInto(lines);
                if (it.hasNext()) {
                    lines.appendRaw(",");
                }
            }
            lines.appendRaw(";");
        }
    }

    public static final class ConstructorBuilder<T> implements BodyBuilder {

        private final Function<ConstructorBuilder<T>, T> converter;
        private BlockBuilder<?> body;
        private final List<AnnotationBuilder<?>> annotations = new LinkedList<>();
        private final Map<String, String> arguments = new LinkedHashMap<>();
        private final Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        private final Set<String> throwing = new TreeSet<>();

        ConstructorBuilder(Function<ConstructorBuilder<T>, T> converter) {
            this.converter = converter;
        }

        public T emptyBody() {
            return body().lineComment("do nothing").endBlock().endConstructor();
        }

        public ConstructorBuilder<T> throwing(String thrown) {
            throwing.add(thrown);
            return this;
        }

        public AnnotationBuilder<ConstructorBuilder<T>> annotatedWith(String what) {
            return new AnnotationBuilder<>(ab -> {
                annotations.add(ab);
                return this;
            }, what);
        }

        public ConstructorBuilder<T> addArgument(String type, String name) {
            arguments.put(name, type);
            return this;
        }

        public ConstructorBuilder<T> body(Consumer<BlockBuilder<?>> c) {
            boolean[] built = new boolean[1];
            BlockBuilder<ConstructorBuilder<T>> block = body(built);
            c.accept(block);
            if (!built[0]) {
                throw new IllegalStateException("endBlock() was not called on "
                        + "constructor body - not added");
            }
            return this;
        }

        public BlockBuilder<ConstructorBuilder<T>> body() {
            return body(new boolean[1]);
        }

        private BlockBuilder<ConstructorBuilder<T>> body(boolean[] built) {
            return new BlockBuilder<>(bb -> {
                body = bb;
                built[0] = true;
                return ConstructorBuilder.this;
            }, true);
        }

        public T endConstructor() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.parens(lb -> {
                for (Iterator<Map.Entry<String, String>> it = arguments.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, String> e = it.next();
                    lb.word(e.getValue()).word(e.getKey());
                    if (it.hasNext()) {
                        lb.appendRaw(",");
                    }
                }
            });
            if (!throwing.isEmpty()) {
                lines.word("throws");
                for (Iterator<String> it = throwing.iterator(); it.hasNext();) {
                    String th = it.next();
                    lines.word(th);
                    if (it.hasNext()) {
                        lines.appendRaw(",");
                    }
                }
            }
            if (body != null) {
                body.buildInto(lines);
            } else {
                lines.appendRaw(";");
            }
        }

        public ConstructorBuilder<T> addModifier(Modifier mod) {
            modifiers.add(mod);
            return this;
        }

        public void buildInto(LinesBuilder lb, String name) {
            lb.onNewLine();
            for (AnnotationBuilder<?> ab : annotations) {
                ab.buildInto(lb);
                lb.onNewLine();
            }
            for (Modifier m : modifiers) {
                lb.word(m.toString());
            }
            lb.word(name);
            buildInto(lb);
        }
    }

    public static final class PackageBuilder {

        private final Object pkg;

        PackageBuilder(Object pkg) {
            this.pkg = pkg;
        }

        public ClassBuilder<String> named(String name) {
            return ClassBuilder.create(pkg, name);
        }
    }

    public T build() {
        T result = converter.apply(this);
        if (CONTEXT.get() == this) {
            CONTEXT.set(prev);
        }
        return result;
    }

    private void checkTypeName(String name) {
        if (name.indexOf('.') > 0) {
            for (String nm : name.split("\\.")) {
                checkTypeName(nm);
            }
            return;
        }
        int max = name.length();
        for (int i = 0; i < max; i++) {
            char c = name.charAt(i);
            if (i == 0 && !Character.isJavaIdentifierStart(c)) {
                throw new IllegalArgumentException("A java identifier cannot start with '" + c + "': " + name);
            } else if (!Character.isJavaIdentifierPart(c)) {
                throw new IllegalArgumentException("A java identifier cannot contain '" + c + "': " + name);
            }
        }
    }

    public ClassBuilder<T> importing(String className, String... more) {
        if (!(converter.getClass() == ClassBuilderStringFunction.class)) {
            throw new IllegalStateException("Imports may only be added to top level classes");
        }
        imports.add(className);
        for (String s : more) {
            imports.add(s);
        }
        return this;
    }

    public ClassBuilder<T> staticImport(String... more) {
        if (!(converter.getClass() == ClassBuilderStringFunction.class)) {
            throw new IllegalStateException("Imports may only be added to top level classes");
        }
        for (String s : more) {
            imports.add("static " + s);
        }
        return this;
    }

    public ClassBuilder<T> addModifier(Modifier m) {
        switch (m) {
            case DEFAULT:
            case NATIVE:
            case STRICTFP:
            case SYNCHRONIZED:
            case TRANSIENT:
            case VOLATILE:
                throw new IllegalArgumentException(m + "");
            case PRIVATE:
                if (modifiers.contains(PROTECTED) || modifiers.contains(PUBLIC)) {
                    throw new IllegalStateException("Cannot be private and also protected or public");
                }
                break;
            case PROTECTED:
                if (modifiers.contains(PRIVATE) || modifiers.contains(PUBLIC)) {
                    throw new IllegalStateException("Cannot be private and also protected or public");
                }
                break;
            case PUBLIC:
                if (modifiers.contains(PRIVATE) || modifiers.contains(PROTECTED)) {
                    throw new IllegalStateException("Cannot be private and also protected or public");
                }
                break;
            case STATIC:
                if (converter.getClass() == ClassBuilderStringFunction.class) {
                    throw new IllegalStateException("Top level classes may not be declared static");
                }
        }
        modifiers.add(m);
        return this;
    }

    private void writeDocComment(LinesBuilder lb) {
        if (docComment == null) {
            return;
        }
        lb.onNewLine();
        lb.appendRaw("/**");
        lb.onNewLine().appendRaw(" * ");
        lb.withWrapPrefix(" * ", lb1 -> {
            for (String word : docComment.split("\\s")) {
                lb.word(word, false);
            }
        });
        lb.onNewLine();
        lb.appendRaw("**/");
    }

    @Override
    public void buildInto(LinesBuilder lines) {
        lines.onNewLine();
        if (constants != null && !"enum".equals(classType)) {
            throw new IllegalStateException(name + " is a " + classType
                    + " but has enum constants");
        }
        if (pkg != null) {
            lines.statement(sb -> {
                sb.word("package").word(pkg);
            });
        }
        lines.doubleNewline();
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                lines.statement("import " + imp);
            }
            lines.doubleNewline();
        }
        writeDocComment(lines);
        if (!annotations.isEmpty()) {
            for (BodyBuilder anno : annotations) {
                lines.onNewLine();
                anno.buildInto(lines);
            }
            lines.onNewLine();
        }
        for (Modifier m : modifiers) {
            lines.word(m.toString());
        }
        lines.word(classType);
        lines.word(name);
        if (extendsType != null) {
            lines.word("extends");
            lines.word(extendsType);
        }
        if (!implementsTypes.isEmpty()) {
            if (isInterface()) {
                if (extendsType != null) {
                    lines.appendRaw(",");
                } else {
                    lines.word("extends");
                }
            } else {
                lines.word("implements");
            }
            for (Iterator<String> it = implementsTypes.iterator(); it.hasNext();) {
                String type = it.next();
                boolean last = !it.hasNext();
                if (!last) {
                    lines.word(type + ",");
                } else {
                    lines.word(type);
                }
            }
        }
        lines.block(true, (lb) -> {
            if (constants != null) {
                constants.buildInto(lines);
            }
            if (loggerField && converter.getClass() == ClassBuilderStringFunction.class) {
                FieldBuilder<Void> fb = new FieldBuilder(f -> {
                    return null;
                }, "LOGGER");
                fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                        .withInitializer("Logger.getLogger(" + LinesBuilder.stringLiteral(pkg + "." + name) + ")")
                        .ofType("Logger");
                fb.buildInto(lines);
                if (generateDebugCode) {
                    lines.onNewLine();
                    lines.word("static");
                    lines.block(lg -> {
                        lg.statement(sb -> {
                            sb.word("LOGGER").appendRaw(".").word("setLevel(").word("Level").appendRaw(".").word("ALL").appendRaw(")");
                        });
//                        lg.statement("LOGGER.setLevel(Level.ALL)");
                    });
                }
            }
            boolean foundFields = false;
            for (BodyBuilder bb : this.members) {
                List<FieldBuilder<?>> staticFields = new ArrayList<>();
                List<FieldBuilder<?>> instanceFields = new ArrayList<>();

                if (bb instanceof FieldBuilder<?>) {
                    FieldBuilder<?> fb = (FieldBuilder<?>) bb;
                    if (fb.isStatic()) {
                        staticFields.add(fb);
                    } else {
                        instanceFields.add(fb);
                    }
                }
                if (!staticFields.isEmpty()) {
                    for (FieldBuilder<?> st : staticFields) {
                        st.buildInto(lb);
                    }
                    lb.doubleNewline();
                }
                for (FieldBuilder<?> i : instanceFields) {
                    i.buildInto(lb);
                }
                foundFields = !staticFields.isEmpty() || !instanceFields.isEmpty();
            }
            if (foundFields) {
                lb.doubleNewline();
            }
            boolean foundConstructors = !this.constructors.isEmpty();
            for (ConstructorBuilder<?> c : this.constructors) {
                c.buildInto(lb, name);
            }
            if (foundConstructors) {
                lb.doubleNewline();
            }
            for (BodyBuilder bb : this.members) {
                if (!(bb instanceof FieldBuilder<?>)) {
                    bb.buildInto(lines);
                }
            }
        });
    }

    public String text() {
        LinesBuilder lb = new LinesBuilder();
        buildInto(lb);
        return lb.toString();
    }

    public String toString() {
        return text();
    }

    public FieldBuilder<ClassBuilder<T>> field(String name) {
        return field(name, new boolean[1]);
    }

    public ClassBuilder<T> field(String name, Consumer<FieldBuilder<?>> c) {
        boolean[] built = new boolean[1];
        FieldBuilder<?> fb = field(name, built);
        c.accept(fb);
        if (!built[0]) {
            throw new IllegalStateException("Field builder not completed - call ofType()");
        }
        return this;
    }

    private FieldBuilder<ClassBuilder<T>> field(String name, boolean[] built) {
        return new FieldBuilder<>(fb -> {
            members.add(fb);
            built[0] = true;
            return ClassBuilder.this;
        }, name);
    }

    public ClassBuilder<T> method(String name, Consumer<MethodBuilder<?>> c) {
        boolean[] built = new boolean[1];
        MethodBuilder<ClassBuilder<T>> mb = method(name, built);
        c.accept(mb);
        if (!built[0]) {
            throw new IllegalStateException("Consumer exited without calling closeMethod() - will not be added");
        }
        return this;
    }

    public MethodBuilder<ClassBuilder<T>> method(String name) {
        return method(name, new boolean[1]);
    }

    public ClassBuilder<T> override(String name, Consumer<MethodBuilder<?>> c) {
        boolean[] built = new boolean[1];
        MethodBuilder<?> m = method(name, built).override();
        c.accept(m);
        if (!built[0]) {
            throw new IllegalStateException("Method not closed");
        }
        return this;
    }

    public MethodBuilder<ClassBuilder<T>> override(String name) {
        return method(name, new boolean[1]).override();
    }

    private MethodBuilder<ClassBuilder<T>> method(String name, boolean[] built) {
        return new MethodBuilder<>(mb -> {
            members.add(mb);
            built[0] = true;
            return ClassBuilder.this;
        }, name);
    }

    public static final class MethodBuilder<T> implements BodyBuilder {

        private final Function<MethodBuilder<T>, T> converter;
        private final Set<Modifier> modifiers = new TreeSet<>();
        private final List<BodyBuilder> annotations = new LinkedList<>();
        private final Set<BodyBuilder> throwing = new LinkedHashSet<>();
        private BodyBuilder block;
        private String type = "void";
        private final String name;

        MethodBuilder(Function<MethodBuilder<T>, T> converter, String name) {
            this.converter = converter;
            this.name = name;
        }

        public MethodBuilder<T> throwing(String throwable) {
            throwing.add(new Adhoc(throwable));
            return this;
        }

        public T closeMethod() {
            return converter.apply(this);
        }

        public MethodBuilder<T> returning(String type) {
            this.type = type;
            return this;
        }

        public MethodBuilder<T> withModifier(Modifier mod) {
            switch (mod) {
                case NATIVE:
                case STRICTFP:
                case TRANSIENT:
                case VOLATILE:
                    throw new IllegalArgumentException("Inappropriate modifier for method: " + mod);
                case PRIVATE:
                    if (modifiers.contains(PROTECTED) || modifiers.contains(PUBLIC)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
                case PROTECTED:
                    if (modifiers.contains(PRIVATE) || modifiers.contains(PUBLIC)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
                case PUBLIC:
                    if (modifiers.contains(PRIVATE) || modifiers.contains(PROTECTED)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
            }
            modifiers.add(mod);
            return this;
        }

        private List<String[]> args = new LinkedList<>();

        public MethodBuilder<T> addArgument(String type, String name) {
            args.add(new String[]{type, name});
            return this;
        }

        public MethodBuilder<T> annotateWith(String annotationType, Consumer<AnnotationBuilder<?>> c) {
            boolean[] built = new boolean[1];
            AnnotationBuilder<MethodBuilder<T>> bldr = annotateWith(annotationType, built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("closeAnnotation() not called");
            }
            return this;
        }

        public AnnotationBuilder<MethodBuilder<T>> annotateWith(String annotationType) {
            return annotateWith(annotationType, new boolean[1]);
        }

        private AnnotationBuilder<MethodBuilder<T>> annotateWith(String annotationType, boolean[] built) {
            return new AnnotationBuilder<>(ab -> {
                annotations.add(ab);
                built[0] = true;
                return MethodBuilder.this;
            }, annotationType);
        }

        public MethodBuilder<T> override() {
            return annotateWith("Override").closeAnnotation();
        }

        public MethodBuilder<T> body(Consumer<BlockBuilder<?>> c) {
            boolean[] built = new boolean[1];
            BlockBuilder<MethodBuilder<T>> block = body(built);
            c.accept(block);
            if (!built[0]) {
                throw new IllegalStateException("endBlock() not called on builder by " + c);
            }
            return this;
        }

        public BlockBuilder<MethodBuilder<T>> body() {
            return body(new boolean[1]);
        }

        private BlockBuilder<MethodBuilder<T>> body(boolean[] built) {
            return new BlockBuilder<>(bb -> {
                MethodBuilder.this.block = bb;
                built[0] = true;
                return MethodBuilder.this;
            }, false);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            if (!annotations.isEmpty()) {
                lines.doubleNewline();
                for (BodyBuilder bb : annotations) {
                    bb.buildInto(lines);
                }
            }
            for (Modifier m : modifiers) {
                lines.word(m.toString());
            }
            lines.word(type);
            lines.word(name);
            lines.parens(lb -> {
                for (Iterator<String[]> it = args.iterator(); it.hasNext();) {
                    String[] curr = it.next();
                    lb.word(curr[0]);
                    if (it.hasNext()) {
                        lb.word(curr[1] + ",");
                    } else {
                        lb.word(curr[1]);
                    }
                }
            });
            if (!throwing.isEmpty()) {
                lines.word("throws");
                for (Iterator<BodyBuilder> it = throwing.iterator(); it.hasNext();) {
                    BodyBuilder th = it.next();
                    th.buildInto(lines);
                    if (it.hasNext()) {
                        lines.appendRaw(",");
                    }
                }
            }
            if (block != null) {
                lines.block(lb -> {
                    block.buildInto(lb);
                });
            } else {
                lines.appendRaw(";");
            }
            lines.doubleNewline();
        }
    }

    public static final class AssignmentBuilder<T> implements BodyBuilder {

        private final Function<AssignmentBuilder<T>, T> converter;
        private String type;
        private BodyBuilder assignment;
        private final BodyBuilder varName;

        AssignmentBuilder(Function<AssignmentBuilder<T>, T> converter, BodyBuilder varName) {
            this.converter = converter;
            this.varName = varName;
        }

        public AssignmentBuilder<T> withType(String type) {
            this.type = type;
            return this;
        }

        public T to(String what) {
            assignment = new Adhoc(what);
            return converter.apply(this);
        }

        public InvocationBuilder<T> toInvocation(String of) {
            return new InvocationBuilder<>(ib -> {
                this.assignment = ib;
                return converter.apply(this);
            }, of);
        }

        public T toStringLiteral(String what) {
            this.type = "String";
            return to(LinesBuilder.stringLiteral(what));
        }

        public T toCharLiteral(char c) {
            this.type = "char";
            return to(LinesBuilder.escapeCharLiteral(c));
        }

        public ArrayLiteralBuilder<T> toArrayLiteral(String type) {
            return new ArrayLiteralBuilder<>(alb -> {
                this.assignment = alb;
                this.type = alb.type + "[]";
                return converter.apply(this);
            }, type);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.statement(lb -> {
                if (type != null && !(this.assignment instanceof ArrayLiteralBuilder)) {
                    lb.word(type);
                }
                if (varName != null) {
                    varName.buildInto(lines);
                }
                lb.word("=");
                assignment.buildInto(lb);
            });
        }
    }

    public static final class ArrayLiteralBuilder<T> implements BodyBuilder {

        private final List<BodyBuilder> all = new LinkedList<>();
        private final Function<ArrayLiteralBuilder<T>, T> converter;
        private final String type;

        ArrayLiteralBuilder(Function<ArrayLiteralBuilder<T>, T> converter, String type) {
            this.converter = converter;
            this.type = type;
        }

        public T closeArrayLiteral() {
            return converter.apply(this);
        }

        public ArrayLiteralBuilder<T> add(String what) {
            all.add(new Adhoc(what));
            return this;
        }

        public ArrayLiteralBuilder<T> addStringLiteral(String what) {
            all.add(new Adhoc(LinesBuilder.stringLiteral(what)));
            return this;
        }

        public ArrayLiteralBuilder<T> addCharLiteral(char what) {
            all.add(new Adhoc(LinesBuilder.escapeCharLiteral(what)));
            return this;
        }

        public InvocationBuilder<ArrayLiteralBuilder<T>> invoke(String what) {
            return new InvocationBuilder<>(ib -> {
                all.add(ib);
                return this;
            }, what);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word("new").word(type).appendRaw("[] {");
            for (Iterator<BodyBuilder> it = all.iterator(); it.hasNext();) {
                BodyBuilder bb = it.next();
                bb.buildInto(lines);
                if (it.hasNext()) {
                    lines.appendRaw(",");
                }
            }
            lines.appendRaw("}");
        }

    }

    public static final class InvocationBuilder<T> implements BodyBuilder {

        private final Function<InvocationBuilder<T>, T> converter;
        private final String name;
        private BodyBuilder on;
        private List<BodyBuilder> arguments = new LinkedList<>();

        InvocationBuilder(Function<InvocationBuilder<T>, T> converter, String name) {
            this.converter = converter;
            this.name = name;
        }

        public T on(String what) {
            this.on = new Adhoc(what);
            return converter.apply(this);
        }

        public InvocationBuilder<T> onInvocationOf(String methodName) {
            return new InvocationBuilder<>(ib2 -> {
                on = ib2;
                return converter.apply(this);
            }, methodName);
        }

        public T onSelf() {
            return on("this");
        }

        public T inScope() {
            on = null;
            return converter.apply(this);
        }

        public InvocationBuilder<T> withArrayArgument(Consumer<ArrayValueBuilder<?>> c) {
            boolean[] built = new boolean[1];
            ArrayValueBuilder<?> av = withArrayArgument(built);
            c.accept(av);
            if (!built[0]) {
                throw new IllegalStateException("Array builder not closed");
            }
            return this;
        }

        public ArrayValueBuilder<InvocationBuilder<T>> withArrayArgument() {
            return withArrayArgument(new boolean[1]);
        }

        private ArrayValueBuilder<InvocationBuilder<T>> withArrayArgument(boolean[] built) {
            return new ArrayValueBuilder<>('[', ']', av -> {
                arguments.add(av);
                built[0] = true;
                return this;
            });
        }

        public InvocationBuilder<T> withNewArrayArgument(String arrayType, Consumer<ArrayValueBuilder<?>> c) {
            boolean[] built = new boolean[1];
            ArrayValueBuilder<?> av = withNewArrayArgument(arrayType, built);
            c.accept(av);
            if (!built[0]) {
                throw new IllegalStateException("Array builder not closed");
            }
            return this;
        }

        public ArrayValueBuilder<InvocationBuilder<T>> withNewArrayArgument(String arrayType) {
            return withNewArrayArgument(arrayType, new boolean[1]);
        }

        private ArrayValueBuilder<InvocationBuilder<T>> withNewArrayArgument(String arrayType, boolean[] built) {
            return new ArrayValueBuilder<>('{', '}', av -> {
                arguments.add(new Composite(new Adhoc("new"), new Adhoc(arrayType + "[]"), av));
                built[0] = true;
                return this;
            });
        }

        public InvocationBuilder<T> withArgument(int arg) {
            arguments.add(new Adhoc(Integer.toString(arg)));
            return this;
        }

        public InvocationBuilder<T> withArgument(String arg) {
            arguments.add(new Adhoc(arg));
            return this;
        }

        public InvocationBuilder<T> withArgument(boolean arg) {
            arguments.add(new Adhoc(Boolean.toString(arg)));
            return this;
        }

        public InvocationBuilder<T> withStringLiteral(String arg) {
            return withArgument(LinesBuilder.stringLiteral(arg));
        }

        public InvocationBuilder<T> withArguments(String... args) {
            Set<String> dups = new HashSet<>();
            for (String arg : args) {
                if (dups.contains(arg)) {
                    throw new IllegalArgumentException("Duplicate argument name: " + arg);
                }
                withArgument(arg);
                dups.add(arg);
            }
            return this;
        }

        public InvocationBuilder<T> withArgumentFromInvoking(String name, Consumer<InvocationBuilder<?>> c) {
            boolean[] built = new boolean[1];
            InvocationBuilder<InvocationBuilder<T>> bldr = withArgumentFromInvoking(built, name);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("Invocation was not finished - call on()");
            }
            return this;
        }

        public InvocationBuilder<InvocationBuilder<T>> withArgumentFromInvoking(String name) {
            return withArgumentFromInvoking(new boolean[1], name);
        }

        private InvocationBuilder<InvocationBuilder<T>> withArgumentFromInvoking(boolean[] built, String name) {
            return new InvocationBuilder<>(ib -> {
                arguments.add(ib);
                built[0] = true;
                return InvocationBuilder.this;
            }, name);
        }

        public LambdaBuilder<InvocationBuilder<T>> withLambdaArgument() {
            return new LambdaBuilder<>(lb -> {
                arguments.add(lb);
                return this;
            });
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.newlineIfNewStatement();
            lines.wrappable(lbb -> {
                if (on != null) {
                    on.buildInto(lbb);
                    lbb.appendRaw(".");
                    lbb.appendRaw(name);
                } else {
                    lbb.word(name);
                }
                lbb.parens(lb -> {
                    for (Iterator<BodyBuilder> it = arguments.iterator(); it.hasNext();) {
                        BodyBuilder arg = it.next();
                        arg.buildInto(lb);
                        if (it.hasNext()) {
                            lb.appendRaw(",");
                        }
                    }
                });
            });
        }
    }

    public static final class SimpleLoopBuilder<T> implements BodyBuilder {

        private final Function<SimpleLoopBuilder<T>, T> converter;
        private final String loopVar;
        private String type;
        private BodyBuilder from;
        private BlockBuilder body;

        SimpleLoopBuilder(Function<SimpleLoopBuilder<T>, T> converter, String loopType, String loopVar) {
            this.converter = converter;
            this.type = loopType;
            this.loopVar = loopVar;
        }

        public BlockBuilder<T> over(String what) {
            this.from = new Adhoc(what);
            return new BlockBuilder<T>(bb -> {
                this.body = bb;
                return converter.apply(this);
            }, true);
        }

        public InvocationBuilder<BlockBuilder<T>> invoke(String name) {
            return new InvocationBuilder<>(ib -> {
                this.from = ib;
                return new BlockBuilder<T>(bb -> {
                    this.body = bb;
                    return converter.apply(this);
                }, true);
            }, name);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.word("for");
            lines.parens(lb -> {
                lb.word(type);
                lb.word(loopVar);
                lb.word(":");
                from.buildInto(lines);
            });
            body.buildInto(lines);
        }
    }

    public static final class ForVarBuilder<T> implements BodyBuilder {

        private final Function<ForVarBuilder<T>, T> converter;
        private String loopVarType = "int";
        private BodyBuilder initializedWith = new Adhoc("0");
        private boolean increment = true;
        private BodyBuilder condition;
        private final String loopVar;
        private BodyBuilder body;

        ForVarBuilder(Function<ForVarBuilder<T>, T> converter, String loopVar) {
            this.converter = converter;
            this.loopVar = loopVar;
        }

        public ForVarBuilder<T> decrement() {
            increment = false;
            return this;
        }

        public ForVarBuilder<T> initializedWith(Number num) {
            initializedWith = new Adhoc(num.toString());
            return this;
        }

        public ConditionComparisonBuilder<ForVarBuilder<T>> condition() {
            return new ConditionBuilder<ForVarBuilder<T>>(fcb -> {
                condition = fcb;
                return this;
            }).variable(loopVar);
        }

        public BlockBuilder<T> running() {
            return new BlockBuilder<>(bb -> {
                this.body = bb;
                return converter.apply(this);
            }, true);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.word("for");
            lines.parens(lb -> {
                lb.word(loopVarType).word(loopVar).word("=");
                initializedWith.buildInto(lines);
                lb.appendRaw(";");
                condition.buildInto(lines);
                lb.appendRaw(";");
                if (increment) {
                    lb.word(loopVar);
                    lb.appendRaw("++");
                } else {
                    lb.word(loopVar);
                    lb.appendRaw("--");
                }
            });
            body.buildInto(lines);
        }
    }

    private static abstract class AbstractBlockBuilder<T, I extends AbstractBlockBuilder<T, I>> implements BodyBuilder {

        private final String prefix;
        protected final Function<I, T> converter;
        protected BodyBuilder block;

        AbstractBlockBuilder(String prefix, Function<I, T> converter) {
            this.prefix = prefix;
            this.converter = converter;
        }

        BlockBuilder<I> block() {
            return new BlockBuilder<>(bb -> {
                block = bb;
                return (I) this;
            }, true);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(prefix + " ");
            onBeforeBlock(lines);
            assert block != null;
            block.buildInto(lines);
        }

        protected void onBeforeBlock(LinesBuilder lines) {

        }

    }

    public static final class TryBuilder<T> extends AbstractBlockBuilder<T, TryBuilder<T>> {

        private List<BodyBuilder> catches = new ArrayList<>();
        private BlockBuilder body;
        BodyBuilder finallyBlock;

        public TryBuilder(Function<TryBuilder<T>, T> converter) {
            super("try", converter);
        }

        T build() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            super.buildInto(lines);
            if (!catches.isEmpty()) {
                for (BodyBuilder b : catches) {
                    lines.backup();
                    b.buildInto(lines);
                }
            }
            if (finallyBlock != null) {
                lines.backup();
                lines.word("finally");
                finallyBlock.buildInto(lines);
            }
        }

        @Override
        BlockBuilder<TryBuilder<T>> block() {
            return super.block();
        }

        public BlockBuilder<CatchBuilder<TryBuilder<T>, T>> catching(String type, String... more) {
            List<Adhoc> all = new ArrayList<>();
            all.add(new Adhoc(type));
            if (more.length > 0) {
                for (int i = 0; i < more.length; i++) {
                    all.add(new Adhoc("|"));
                    all.add(new Adhoc(more[i]));
                }
            }
            BodyBuilder types = (new Composite(all.toArray(new BodyBuilder[all.size()])));
            return new CatchBuilder<TryBuilder<T>, T>(types, cb -> {
                catches.add(cb);
                return TryBuilder.this;
            }).block();
        }

        public BlockBuilder<T> fynalli() {
            return new BlockBuilder<T>(bb -> {
                finallyBlock = bb;
                return converter.apply(this);
            }, true);
        }
    }

    public static final class CatchBuilder<T extends TryBuilder<R>, R> extends AbstractBlockBuilder<T, CatchBuilder<T, R>> {

        private String exceptionName = "thrown";
        private final BodyBuilder types;

        public CatchBuilder(BodyBuilder types, Function<CatchBuilder<T, R>, T> converter) {
            super("catch", converter);
            this.types = types;
        }

        public CatchBuilder<T, R> namingException(String nm) {
            exceptionName = nm;
            return this;
        }

        public BlockBuilder<R> fynalli() {
            return new BlockBuilder<R>(bb -> {
                T t = converter.apply(this);
                t.finallyBlock = bb;
                return t.build();
            }, true);
        }

        @Override
        protected void onBeforeBlock(LinesBuilder lines) {
            lines.parens(lb -> {
                types.buildInto(lines);
                lb.word(exceptionName);
            });
        }

        public R endTryCatch() {
            return converter.apply(this).build();
        }
    }

    public static final class SynchronizedBlockBuilder<T> implements BodyBuilder {

        private final Function<SynchronizedBlockBuilder<T>, T> converter;
        private BlockBuilder<?> body;
        private final String on;

        SynchronizedBlockBuilder(Function<SynchronizedBlockBuilder<T>, T> converter, String on) {
            this.converter = converter;
            this.on = on;
        }

        BlockBuilder<T> block() {
            return new BlockBuilder<>(bb -> {
                body = bb;
                return converter.apply(this);
            }, true);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.word("synchronized");
            lines.parens(lb -> {
                lb.word(on);
            });
            body.buildInto(lines);
        }
    }

    public static final class LambdaBuilder<T> implements BodyBuilder {

        private final Function<LambdaBuilder<T>, T> converter;
        private BlockBuilder<?> body;
        private final LinkedHashMap<String, String> arguments = new LinkedHashMap<>();
        private Exception creation;

        LambdaBuilder(Function<LambdaBuilder<T>, T> converter) {
            this.converter = converter;
            creation = new Exception();
        }

        public LambdaBuilder<T> withArgument(String type, String arg) {
            arguments.put(arg, type);
            return this;
        }

        public LambdaBuilder<T> withArgument(String name) {
            arguments.put(name, "");
            return this;
        }

        public BlockBuilder<T> body() {
            return block();
        }

        BlockBuilder<T> block() {
            return new BlockBuilder<>(bb -> {
                body = bb;
                return converter.apply(this);
            }, true);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            if (arguments.isEmpty()) {
                lines.word("()");
            } else if (arguments.size() == 1 && "".equals(arguments.get(arguments.keySet().iterator().next()))) {
                String arg = arguments.get(arguments.keySet().iterator().next());
                lines.word(arg);
            } else {
                lines.parens(lb -> {
                    int argCount = 0;
                    for (Iterator<Map.Entry<String, String>> it = arguments.entrySet().iterator(); it.hasNext();) {
                        Map.Entry<String, String> e = it.next();
                        String type = e.getValue();
                        String name = e.getKey();
                        int ac = type == "" ? 1 : 2;
                        if (argCount != 0 && argCount != ac) {
                            throw new IllegalStateException("Lambda arguments mix "
                                    + "specifying type and not specifying type for "
                                    + arguments, creation);
                        }
                        argCount = ac;
                        if (ac == 1) {
                            lb.word(name);
                        } else {
                            lb.word(type);
                            lb.word(name);
                        }
                        if (it.hasNext()) {
                            lb.appendRaw(",");
                        }
                    }
                });
            }
            lines.word("->");
            body.buildInto(lines);
            lines.backup();
        }
    }

    public static final class BlockBuilder<T> implements BodyBuilder {

        private final List<BodyBuilder> statements = new LinkedList<>();
        private final Function<BlockBuilder<T>, T> converter;
        private final boolean openBlock;

        BlockBuilder(Function<BlockBuilder<T>, T> converter, boolean openBlock) {
            this.converter = converter;
            this.openBlock = openBlock;
            if (CONTEXT.get() != null) {
                if (CONTEXT.get().generateDebugCode) {
                    Exception ex = new Exception();
                    StackTraceElement[] els = ex.getStackTrace();
                    if (els != null && els.length > 0) {
                        String pkg = ClassBuilder.class.getPackage().getName();
                        for (StackTraceElement e : els) {
                            if (!e.getClassName().startsWith(pkg)) {
                                lineComment(stripPackage(e));
                                break;
                            }
                        }
                    }
                }
            }
        }

        private static String stripPackage(StackTraceElement el) {
            // Avoids generating gargantuan comments
            String s = el.toString();
            int ix = s.indexOf('(');
            if (ix < 0) { // ??
                return s;
            }
            int start = 0;
            for (int i = 0; i < ix; i++) {
                if (s.charAt(i) == '.') {
                    start = i + 1;
                }
            }
            return s.substring(start);
        }

        public BlockBuilder<T> debugLog(String line) {
            if ((CONTEXT.get() != null && CONTEXT.get().generateDebugCode)) {
                invoke("println").withStringLiteral(line).on("System.out");
            }
            return this;
        }

        public BlockBuilder<T> log(String line) {
            if (CONTEXT.get() != null) {
                CONTEXT.get().logger();
            }
            return invoke("log")
                    .withArgument("Level.INFO")
                    .withStringLiteral(line).on("LOGGER");
        }

        public BlockBuilder<T> log(Level level, Consumer<LogLineBuilder<?>> c) {
            boolean[] built = new boolean[1];
            LogLineBuilder b = log(level, built);
            c.accept(b);
            if (!built[0]) {
                throw new IllegalStateException("logging(line) not called - LogLineBuilder not completed");
            }
            return this;
        }

        public LogLineBuilder<BlockBuilder<T>> log(Level level) {
            return log(level, new boolean[1]);
        }

        private LogLineBuilder<BlockBuilder<T>> log(Level level, boolean[] built) {
            if (CONTEXT.get() != null) {
                CONTEXT.get().logger();
            }
            return new LogLineBuilder<>(llb -> {
                statements.add(llb);
                return this;
            }, level);
        }

        public static final class LogLineBuilder<T> implements BodyBuilder {

            private final Function<LogLineBuilder<T>, T> converter;
            private String line;
            private List<BodyBuilder> arguments = new ArrayList<>(5);
            private final Level level;

            public LogLineBuilder(Function<LogLineBuilder<T>, T> converter, Level level) {
                this.converter = converter;
                this.level = level;
            }

            public LogLineBuilder<T> argument(String arg) {
                arguments.add(new Adhoc(arg));
                return this;
            }

            public LogLineBuilder<T> stringLiteral(String arg) {
                arguments.add(new Adhoc(LinesBuilder.stringLiteral(arg)));
                return this;
            }

            public LogLineBuilder<T> argument(Number arg) {
                return argument(arg.toString());
            }

            public LogLineBuilder<T> argument(boolean arg) {
                return argument(Boolean.toString(arg));
            }

            public T logging(String line) {
                assert line != null : "line null";
                this.line = line;
                for (int i = 0; i < arguments.size(); i++) {
                    String searchFor = "{" + i + "}";
                    if (!line.contains(searchFor)) {
                        throw new IllegalArgumentException("At least "
                                + arguments.size() + " logger arguments "
                                + "present, but the template has no "
                                + "occurrence of " + searchFor + " - that"
                                + "argument will not be logged");
                    }
                }
                for (int i = arguments.size(); i < 100; i++) {
                    String searchFor = "{" + i + "}";
                    if (line.contains(searchFor)) {
                        throw new IllegalArgumentException("Line contains a "
                                + "string template " + searchFor + " but "
                                + "only " + arguments.size()
                                + " arguments are present");
                    }
                }
                return converter.apply(this);
            }

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.statement(sb -> {
                    sb.word("LOGGER.log");
                    sb.delimit('(', ')', llb -> {
                        llb.word("Level").appendRaw(".").appendRaw(level.getName()).appendRaw(",");
                        llb.word(LinesBuilder.stringLiteral(line));
                        switch (arguments.size()) {
                            case 0:
                                break;
                            case 1:
                                llb.appendRaw(",");
                                arguments.get(0).buildInto(llb);
                                break;
                            default:
                                llb.appendRaw(",");
                                llb.word("new").word("Object[]");
                                llb.delimit('{', '}', db -> {
                                    for (Iterator<BodyBuilder> it = arguments.iterator(); it.hasNext();) {
                                        it.next().buildInto(db);
                                        if (it.hasNext()) {
                                            db.appendRaw(",");
                                        }
                                    }
                                });
                        }
                    });
                });
            }

        }

        public BlockBuilder<T> log(String line, Level level, Object... args) {
            if (CONTEXT.get() != null) {
                CONTEXT.get().logger();
            }
            if (args.length == 0) {
                return invoke("log")
                        .withArgument("Level." + level.getName())
                        .withStringLiteral(line).on("LOGGER");
            } else {
                ArrayValueBuilder<InvocationBuilder<BlockBuilder<T>>> ab
                        = invoke("log").withArgument("Level." + level.getName())
                                .withStringLiteral(line).withNewArrayArgument("Object");
                for (Object o : args) {
                    if (o != null && o.getClass().isArray()) {
                        throw new IllegalArgumentException("Element of array "
                                + "is an actual Java array - this cannot "
                                + "possibly be what you want, as it will log as"
                                + "L[" + o.getClass().getName());
                    }
                    ab.value(Objects.toString(o));
                }
                ab.closeArray().on("LOGGER");
            }
            return this;
        }

        public LambdaBuilder<BlockBuilder<T>> lambda() {
            return lambda(new boolean[1]);
        }

        public BlockBuilder<T> blankLine() {
            statements.add(new NL());
            return this;
        }

        static final class NL implements BodyBuilder {

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.doubleNewline();
            }

        }

        public BlockBuilder<T> lambda(Consumer<LambdaBuilder<?>> c) {
            boolean[] built = new boolean[1];
            LambdaBuilder<BlockBuilder<T>> bldr = lambda(built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("closeBlock() not called on lambda - not added");
            }
            return this;
        }

        private LambdaBuilder<BlockBuilder<T>> lambda(boolean[] built) {
            return new LambdaBuilder<>(lb -> {
                statements.add(lb);
                built[0] = true;
                return this;
            });
        }

        public BlockBuilder<BlockBuilder<T>> synchronize() {
            return synchronizeOn("this");
        }

        public BlockBuilder<BlockBuilder<T>> synchronizeOn(String what) {
            return synchronizeOn(what, new boolean[1]);
        }

        public BlockBuilder<T> synchronize(String what, Consumer<BlockBuilder<BlockBuilder<T>>> c) {
            return synchronizeOn("this", c);
        }

        public BlockBuilder<T> synchronizeOn(String what, Consumer<BlockBuilder<BlockBuilder<T>>> c) {
            boolean[] built = new boolean[1];
            BlockBuilder<BlockBuilder<T>> bldr = synchronizeOn(what, built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("endBlock() not called on synchronized block builder - will not be added");
            }
            return this;
        }

        private BlockBuilder<BlockBuilder<T>> synchronizeOn(String what, boolean[] built) {
            SynchronizedBlockBuilder<BlockBuilder<T>> res = new SynchronizedBlockBuilder<>(sb -> {
                statements.add(sb);
                return this;
            }, what);
            return res.block();
        }

        public BlockBuilder<TryBuilder<BlockBuilder<T>>> trying() {
            return new TryBuilder<BlockBuilder<T>>(tb -> {
                statements.add(tb);
                return this;
            }).block();
        }

        public SimpleLoopBuilder<BlockBuilder<T>> simpleLoop(String type, String loopVarName) {
            return simpleLoop(type, loopVarName, new boolean[1]);
        }

        private SimpleLoopBuilder<BlockBuilder<T>> simpleLoop(String type, String loopVarName, boolean[] built) {
            return new SimpleLoopBuilder<>(slb -> {
                statements.add(slb);
                built[0] = true;
                return BlockBuilder.this;
            }, type, loopVarName);
        }

        public BlockBuilder<T> simpleLoop(String type, String loopVarName, Consumer<SimpleLoopBuilder<?>> consumer) {
            boolean[] built = new boolean[1];
            SimpleLoopBuilder<BlockBuilder<T>> bldr = simpleLoop(type, loopVarName, built);
            consumer.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("SimpleLoopBuilder was not built and added in consumer");
            }
            return this;
        }

        public BlockBuilder<T> forVar(String name, Consumer<ForVarBuilder<?>> consumer) {
            boolean[] built = new boolean[1];
            ForVarBuilder<BlockBuilder<T>> b = forVar(name, built);
            consumer.accept(b);
            if (!built[0]) {
                throw new IllegalStateException("ForVarBuilder was not closed and added in consumer");
            }
            return this;
        }

        public ForVarBuilder<BlockBuilder<T>> forVar(String name) {
            return forVar(name, new boolean[1]);
        }

        private ForVarBuilder<BlockBuilder<T>> forVar(String name, boolean[] built) {
            return new ForVarBuilder<>(fvb -> {
                built[0] = true;
                statements.add(fvb);
                return this;
            }, name);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            if (openBlock) {
                lines.block(lb -> {
                    for (BodyBuilder bb : statements) {
                        bb.buildInto(lb);
                    }
                });
            } else {
                for (BodyBuilder bb : statements) {
                    bb.buildInto(lines);
                }
            }
        }

        public BlockBuilder<T> lineComment(String cmt) {
            for (String s : cmt.split("\n")) {
                statements.add(new Composite(new Adhoc("// " + s.trim(), false), new ONL()));
            }
            return this;
        }

        static class ONL implements BodyBuilder {

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.onNewLine();
            }

        }

        public InvocationBuilder<BlockBuilder<T>> invoke(String method) {
            return new InvocationBuilder<>(ib -> {
                statements.add(new WrappedStatement(ib));
                return BlockBuilder.this;
            }, method);
        }

        public BlockBuilder<BlockBuilder<T>> block() {
            return new BlockBuilder<>(bk -> {
                statements.add(bk);
                return BlockBuilder.this;
            }, true);
        }

        public BlockBuilder<T> statement(String stmt) {
            statements.add(new OneStatement(stmt));
            return this;
        }

        public BlockBuilder<T> returning(String s) {
            statements.add(new OneStatement("return " + s));
            return this;
        }

        public BlockBuilder<T> returningStringLiteral(String s) {
            statements.add(new OneStatement("return " + LinesBuilder.stringLiteral(s)));
            return this;
        }

        public InvocationBuilder<BlockBuilder<T>> returningInvocationOf(String method) {
            return new InvocationBuilder<>(ib -> {
                statements.add(new ReturnBody(ib));
                return BlockBuilder.this;
            }, method);
        }

        private static final class ReturnBody implements BodyBuilder {

            private final BodyBuilder what;

            public ReturnBody(BodyBuilder what) {
                this.what = what;
            }

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.statement(st -> {
                    st.word("return");
                    what.buildInto(st);
                });
            }
        }

        public BlockBuilder<T> incrementVariable(String var) {
            statements.add(new OneStatement(var + "++"));
            return this;
        }

        public BlockBuilder<T> decrementVariable(String var) {
            statements.add(new OneStatement(var + "--"));
            return this;
        }

        public DeclarationBuilder<BlockBuilder<T>> declare(String what) {
            return new DeclarationBuilder<>(db -> {
                statements.add(db);
                return BlockBuilder.this;
            }, what);
        }

        public IfBuilder<BlockBuilder<T>> iff(String initialCondition) {
            return new IfBuilder<>(ib -> {
                statements.add(ib);
                return BlockBuilder.this;
            }, initialCondition);
        }

        public ConditionBuilder<IfBuilder<BlockBuilder<T>>> ifCondition() {
            return ifCondition(new boolean[1]);
        }

        public BlockBuilder<T> ifCondition(Consumer<ConditionBuilder<IfBuilder<BlockBuilder<T>>>> c) {
            boolean[] built = new boolean[1];
            ConditionBuilder<IfBuilder<BlockBuilder<T>>> bldr = ifCondition(built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("IfBuilder not finished");
            }
            return this;
        }

        private ConditionBuilder<IfBuilder<BlockBuilder<T>>> ifCondition(boolean[] built) {
            return new ConditionBuilder<>(fcb -> {
                return new IfBuilder<>(ib -> {
                    statements.add(ib);
                    built[0] = true;
                    return BlockBuilder.this;
                }, fcb);
            });
        }

        public SwitchBuilder<BlockBuilder<T>> switchingOn(String on) {
            return new SwitchBuilder<>(sb -> {
                statements.add(sb);
                return BlockBuilder.this;
            }, on);
        }

        public T endBlock() {
            return converter.apply(this);
        }

        static class WrappedStatement implements BodyBuilder {

            private final BodyBuilder wrapped;

            WrappedStatement(BodyBuilder wrapped) {
                this.wrapped = wrapped;
            }

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.statement(lb1 -> {
                    wrapped.buildInto(lb1);
                });
            }
        }

        static class OneStatement implements BodyBuilder {

            private final String text;

            OneStatement(String text) {
                this.text = text;
            }

            @Override
            public void buildInto(LinesBuilder lines) {
                lines.statement(text);
            }
        }
    }

    public static final class DeclarationBuilder<T> implements BodyBuilder {

        private final Function<DeclarationBuilder<T>, T> converter;
        private final String name;
        private String as;
        private BodyBuilder initializer;

        DeclarationBuilder(Function<DeclarationBuilder<T>, T> converter, String name) {
            this.converter = converter;
            this.name = name;
        }

        public T as(String type) {
            this.as = type;
            return converter.apply(this);
        }

        public DeclarationBuilder<T> initializedWith(String init) {
            this.initializer = new Adhoc(init);
            return this;
        }

        public DeclarationBuilder<T> initializedWithStringLiteral(String init) {
            this.initializer = new Adhoc(LinesBuilder.stringLiteral(init));
            return this;
        }

        public ConditionBuilder<T> initializedWithBooleanExpression() {
            return new ConditionBuilder<>(fcb -> {
                initializer = fcb;
                as = "boolean";
                return converter.apply(DeclarationBuilder.this);
            });
        }

        public InvocationBuilder<DeclarationBuilder<T>> initializedByInvoking(String what) {
            return new InvocationBuilder<>(ib -> {
                initializer = ib;
                return DeclarationBuilder.this;
            }, what);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.statement(l -> {
                l.word(as);
                l.word(name);
                if (initializer != null) {
                    l.word("=");
                    initializer.buildInto(l);
                }
            });
        }
    }

    public static final class IfBuilder<T> implements BodyBuilder {

        private final Function<IfBuilder<T>, T> converter;
        private final Map<BodyBuilder, BlockBuilder<?>> conditions = new LinkedHashMap<>();
        private BodyBuilder currCondition;

        IfBuilder(Function<IfBuilder<T>, T> converter, String currCondition) {
            this.converter = converter;
            this.currCondition = new Adhoc(currCondition);
        }

        IfBuilder(Function<IfBuilder<T>, T> converter, BodyBuilder currCondition) {
            this.converter = converter;
            this.currCondition = currCondition;
        }

        public BlockBuilder<IfBuilder<T>> thenDo() {
            return thenDo(new boolean[1]);
        }

        private BlockBuilder<IfBuilder<T>> thenDo(boolean[] built) {
            if (currCondition == null) {
                throw new IllegalStateException("Then block already entered; need a new condition");
            }
            return new BlockBuilder<>(bb -> {
                conditions.put(currCondition, bb);
                currCondition = null;
                built[0] = true;
                return IfBuilder.this;
            }, true);
        }

        public IfBuilder<T> thenDo(Consumer<BlockBuilder<?>> c) {
            boolean[] built = new boolean[1];
            BlockBuilder<?> bldr = thenDo(built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("then block not closed - call endBlock()");
            }
            return this;
        }

        public ConditionBuilder<BlockBuilder<IfBuilder<T>>> elseIf() {
            return new ConditionBuilder<>(cb -> {
                currCondition = cb;
                return thenDo();
            });
        }

        public BlockBuilder<IfBuilder<T>> elseIf(BodyBuilder condition) {
            if (conditions.containsKey(condition)) {
                boolean isClosingElse = condition instanceof Empty;
                throw new IllegalStateException("An else clause " + (isClosingElse ? ""
                        : " the condition '" + condition + "' is already present"));
            }
            currCondition = condition;
            return thenDo();
        }

        public BlockBuilder<IfBuilder<T>> elseDo() {
            return elseIf(new Empty());
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.onNewLine();
            boolean first = true;
            for (Iterator<Map.Entry<BodyBuilder, BlockBuilder<?>>> it = conditions.entrySet().iterator(); it.hasNext();) {
                Map.Entry<BodyBuilder, BlockBuilder<?>> e = it.next();
                if (!first) {
                    lines.withoutNewline().word("else");
                }
                if (!(e.getKey() instanceof Empty)) {
                    lines.word("if ");
                    lines.parens(lb -> {
                        e.getKey().buildInto(lines);
                    });
                    e.getValue().buildInto(lines);
                } else {
                    e.getValue().buildInto(lines);
                }
                first = false;
            }
        }

        public T endIf() {
            return converter.apply(this);
        }
    }

    static final class Empty implements BodyBuilder {

        @Override
        public void buildInto(LinesBuilder lines) {
            // do nothing
        }

        public boolean equals(Object o) {
            return o instanceof Empty;
        }

        public int hashCode() {
            return 0;
        }
    }

    public static final class ConditionBuilder<T> implements BodyBuilder {

        private final Function<FinishableConditionBuilder<T>, T> converter;
        private boolean negated;
        private boolean parenthesized;
        private BodyBuilder prev;
        private LogicalOperation op;
        private BodyBuilder clause;

        ConditionBuilder(Function<FinishableConditionBuilder<T>, T> converter) {
            this.converter = converter;
        }

        ConditionBuilder(Function<FinishableConditionBuilder<T>, T> converter, BodyBuilder prev, LogicalOperation op) {
            this.converter = converter;
            this.prev = prev;
            this.op = op;
        }

        public ConditionBuilder<T> not() {
            negated = !negated;
            return this;
        }

        public ConditionBuilder<T> parenthesized() {
            this.parenthesized = true;
            return this;
        }

        public InvocationBuilder<ConditionComparisonBuilder<T>> invoke(String what) {
            return new InvocationBuilder<>(ib -> {
                clause = ib;
                return new ConditionComparisonBuilder<>(ConditionBuilder.this);
            }, what);
        }

        public ConditionComparisonBuilder<T> variable(String name) {
            clause = new Adhoc(name);
            return new ConditionComparisonBuilder<>(this);
        }

        public ConditionComparisonBuilder<T> stringLiteral(String lit) {
            clause = new Adhoc(LinesBuilder.stringLiteral(lit));
            return new ConditionComparisonBuilder<>(this);
        }

        public ConditionComparisonBuilder<T> numericLiteral(Number val) {
            clause = new Adhoc(val.toString());
            return new ConditionComparisonBuilder<>(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            if (parenthesized) {
                lines.parens(this::doBuildInto);
            } else {
                doBuildInto(lines);
            }
        }

        private void doBuildInto(LinesBuilder lines) {
            if (prev != null) {
                prev.buildInto(lines);
                op.buildInto(lines);
            }
            clause.buildInto(lines);
        }
    }

    static final class Adhoc implements BodyBuilder {

        private final String what;
        private boolean hangingWrap;

        Adhoc(String what) {
            this(what, true);
        }

        Adhoc(String what, boolean hangingWrap) {
            this.what = what;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(what, hangingWrap);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.what);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Adhoc other = (Adhoc) obj;
            if (!Objects.equals(this.what, other.what)) {
                return false;
            }
            return true;
        }
    }

    public static final class ConditionComparisonBuilder<T> implements BodyBuilder {

        private final ConditionBuilder<T> leftSide;

        ConditionComparisonBuilder(ConditionBuilder<T> leftSide) {
            this.leftSide = leftSide;
        }

        public ConditionBuilder<T> or() {
            return new ConditionBuilder<>(leftSide.converter, leftSide, LogicalOperation.OR);
        }

        public ConditionBuilder<T> and() {
            return new ConditionBuilder<>(leftSide.converter, leftSide, LogicalOperation.AND);
        }

        public ConditionBuilder<T> orNot() {
            return or().not().parenthesized();
        }

        public ConditionBuilder<T> andNot() {
            return and().not().parenthesized();
        }

        public ConditionRightSideBuilder<T> equals() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.EQ);
        }

        public ConditionRightSideBuilder<T> notEquals() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.NE);
        }

        public ConditionRightSideBuilder<T> greaterThan() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.GT);
        }

        public ConditionRightSideBuilder<T> lessThan() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.LT);
        }

        public ConditionRightSideBuilder<T> greaterThanOrEqualto() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.GTE);
        }

        public ConditionRightSideBuilder<T> lessThanOrEqualto() {
            return new ConditionRightSideBuilder<>(leftSide.converter, leftSide, ComparisonOperation.LTE);
        }

        public T endCondition() {
            ConditionRightSideBuilder<T> right = new ConditionRightSideBuilder<>(leftSide.converter, leftSide, null);
            return new FinishableConditionBuilder<>(leftSide.converter, right, null).endCondition();
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            leftSide.buildInto(lines);
        }
    }

    public static final class ConditionRightSideBuilder<T> implements BodyBuilder {

        private final Function<FinishableConditionBuilder<T>, T> converter;
        private boolean negated;
        private final BodyBuilder leftSide;
        private final ComparisonOperation op;

        ConditionRightSideBuilder(Function<FinishableConditionBuilder<T>, T> converter, BodyBuilder leftSide, ComparisonOperation op) {
            this.converter = converter;
            this.leftSide = leftSide;
            this.op = op;
        }

        public ConditionRightSideBuilder<T> not() {
            negated = !negated;
            return this;
        }

        public FinishableConditionBuilder<T> literal(Number num) {
            return new FinishableConditionBuilder<>(converter, this, new Adhoc(num.toString()));
        }

        public FinishableConditionBuilder<T> literal(String val) {
            return new FinishableConditionBuilder<>(converter, this, new Adhoc(val));
        }

        public FinishableConditionBuilder<T> literal(char val) {
            return new FinishableConditionBuilder<>(converter, this, new Adhoc(LinesBuilder.escapeCharLiteral(val)));
        }

        public FinishableConditionBuilder<T> stringLiteral(String val) {
            return new FinishableConditionBuilder<>(converter, this, new Adhoc(LinesBuilder.stringLiteral(val)));
        }

        public ConditionBuilder<T> or() {
            return new ConditionBuilder<>(converter, this, LogicalOperation.OR);
        }

        public ConditionBuilder<T> and() {
            return new ConditionBuilder<>(converter, this, LogicalOperation.AND);
        }

        public InvocationBuilder<FinishableConditionBuilder<T>> invoke(String what) {
            return new InvocationBuilder<>(ib -> {
                return new FinishableConditionBuilder<>(converter, this, ib);
            }, what);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            if (negated) {
                lines.word("!");
                lines.parens(this::doBuildInto);
            } else {
                doBuildInto(lines);
            }
        }

        private void doBuildInto(LinesBuilder lines) {
            leftSide.buildInto(lines);
            if (op != null) {
                op.buildInto(lines);
            }
        }
    }

    public static final class FinishableConditionBuilder<T> implements BodyBuilder {

        private final Function<FinishableConditionBuilder<T>, T> converter;
        private final ConditionRightSideBuilder<T> leftSideAndOp;
        private final BodyBuilder rightSide;

        FinishableConditionBuilder(Function<FinishableConditionBuilder<T>, T> converter, ConditionRightSideBuilder<T> leftSideAndOp, BodyBuilder rightSide) {
            this.converter = converter;
            this.leftSideAndOp = leftSideAndOp;
            this.rightSide = rightSide;
        }

        public ConditionBuilder<T> or() {
            return new ConditionBuilder<>(converter, this, LogicalOperation.OR);
        }

        public ConditionBuilder<T> and() {
            return new ConditionBuilder<>(converter, this, LogicalOperation.AND);
        }

        public T endCondition() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            leftSideAndOp.buildInto(lines);
            if (rightSide != null) {
                rightSide.buildInto(lines);
            }
        }
    }

    private static enum UnaryOperator implements BodyBuilder {
        POSITIVE("+"),
        NEGATIVE("-"),
        INCREMENT("++"),
        DECREMENT("--"),
        NEGATE("!");
        private final String s;

        private UnaryOperator(String s) {
            this.s = s;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(s);
        }

    }

    private static enum ArithmenticOperator implements BodyBuilder {
        PLUS("+"),
        MINUS("-"),
        TIMES("*"),
        DIVIDED_BY("/"),
        REMAINDER("%");
        private final String s;

        private ArithmenticOperator(String s) {
            this.s = s;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(s);
        }
    }

    private static enum AssignmentOperator implements BodyBuilder {
        EQUALS("="),
        PLUS_EQUALS("+="),
        MINUS_EQUALS("-="),
        OR_EQUALS("|="),
        AND_EQUALS("&="),
        XOR_EQUALS("^="),
        MOD_EQUALS("%="),
        DIV_EQUALS("/="),
        MUL_EQUALS("*="),;

        private final String s;

        private AssignmentOperator(String s) {
            this.s = s;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(s);
        }
    ;

    }

    private static enum ComparisonOperation implements BodyBuilder {
        EQ("=="),
        GT(">"),
        LT("<"),
        GTE(">="),
        LTE("<="),
        NE("!=");
        String s;

        private ComparisonOperation(String s) {
            this.s = s;
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word(s);
        }
    }

    private static enum LogicalOperation implements BodyBuilder {
        OR,
        AND,;

        @Override
        public void buildInto(LinesBuilder lines) {
            switch (this) {
                case OR:
                    lines.word("||");
                    break;
                case AND:
                    lines.word("&&");
                    break;
                default:
                    throw new AssertionError(this);
            }
        }
    }

    public static final class SwitchBuilder<T> implements BodyBuilder {

        private final Function<SwitchBuilder<T>, T> converter;
        private final Map<String, BodyBuilder> cases = new LinkedHashMap<>();
        private final String what;

        SwitchBuilder(Function<SwitchBuilder<T>, T> converter, String on) {
            this.converter = converter;
            this.what = on;
        }

        public BlockBuilder<SwitchBuilder<T>> inStringCase(String what) {
            return inCase(LinesBuilder.stringLiteral(what));
        }

        public BlockBuilder<SwitchBuilder<T>> inDefaultCase() {
            return inCase("*");
        }

        public BlockBuilder<SwitchBuilder<T>> inCharCase(char c) {
            return inCase(LinesBuilder.escapeCharLiteral(c));
        }

        public BlockBuilder<SwitchBuilder<T>> inCase(Number num) {
            return inCase(num.toString());
        }

        public BlockBuilder<SwitchBuilder<T>> inCase(String what) {
            return new BlockBuilder<>(bb -> {
                cases.put(what, bb);
                return SwitchBuilder.this;
            }, false);
        }

        public T build() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.word("switch");
            lines.parens(lb -> {
                lb.word(what);
            });
            lines.block(lb -> {
                for (Map.Entry<String, BodyBuilder> e : cases.entrySet()) {
                    if ("*".equals(e.getKey())) {
                        lb.onNewLine();
                        lb.switchCase(null, (lb1) -> {
                            e.getValue().buildInto(lb1);
                        });
                    } else {
                        lb.switchCase(e.getKey(), (lb1) -> {
                            e.getValue().buildInto(lb1);
                        });
                    }
                }
            });
        }
    }

    public static final class ArrayValueBuilder<T> implements BodyBuilder {

        private final char end;

        private final Function<ArrayValueBuilder<T>, T> converter;
        private final List<BodyBuilder> values = new LinkedList<>();
        private final char start;

        ArrayValueBuilder(char start, char end, Function<ArrayValueBuilder<T>, T> converter) {
            this.end = end;
            this.converter = converter;
            this.start = start;
        }

        ArrayValueBuilder(Function<ArrayValueBuilder<T>, T> converter) {
            this('[', ']', converter);
        }

        public T closeArray() {
            return converter.apply(this);
        }

        public ArrayValueBuilder<T> stringLiteral(String s) {
            values.add(new Adhoc(LinesBuilder.stringLiteral(s)));
            return this;
        }

        public ArrayValueBuilder<T> charLiteral(char c) {
            values.add(new Adhoc(LinesBuilder.escapeCharLiteral(c)));
            return this;
        }

        public ArrayValueBuilder<T> number(Number n) {
            values.add(new Adhoc(n.toString()));
            return this;
        }

        public ArrayValueBuilder<T> value(String s) {
            values.add(new Adhoc(s));
            return this;
        }

        public AnnotationBuilder<ArrayValueBuilder<T>> annotation(String type) {
            return annotation(type, new boolean[1]);
        }

        public ArrayValueBuilder<T> annotation(String type, Consumer<AnnotationBuilder<?>> c) {
            boolean[] built = new boolean[1];
            AnnotationBuilder<?> bldr = annotation(type, built);
            c.accept(bldr);
            if (built[0] == false) {
                throw new IllegalStateException("Annotation was not closed - call closeAnnotation()");
            }
            return this;
        }

        private AnnotationBuilder<ArrayValueBuilder<T>> annotation(String type, boolean[] built) {
            return new AnnotationBuilder<>(ab -> {
                values.add(ab);
                return this;
            }, type);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.delimit(start, end, lb -> {
                for (Iterator<BodyBuilder> it = values.iterator(); it.hasNext();) {
                    it.next().buildInto(lb);
                    lb.backup();
                    if (it.hasNext()) {
                        lb.appendRaw(",");
                    }
                }
            });
        }
    }

    public static final class AnnotationBuilder<T> implements BodyBuilder {

        private final Function<AnnotationBuilder<T>, T> converter;
        private final String annotationType;
        private final Map<String, BodyBuilder> arguments = new LinkedHashMap<>();

        AnnotationBuilder(Function<AnnotationBuilder<T>, T> converter, String annotationType) {
            this.converter = converter;
            this.annotationType = annotationType;
        }

        public AnnotationBuilder<T> addArrayArgument(String name, Consumer<ArrayValueBuilder<?>> c) {
            boolean[] built = new boolean[1];
            ArrayValueBuilder<?> bldr = addArrayArgument(name, new boolean[1]);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("closeArray() not called");
            }
            return this;
        }

        public ArrayValueBuilder<AnnotationBuilder<T>> addArrayArgument(String name) {
            return addArrayArgument(name, new boolean[1]);
        }

        private ArrayValueBuilder<AnnotationBuilder<T>> addArrayArgument(String name, boolean[] built) {
            return new ArrayValueBuilder<>('{', '}', avb -> {
                arguments.put(name, avb);
                built[0] = true;
                return this;
            });
        }

        public AnnotationBuilder<AnnotationBuilder<T>> addAnnotationArgument(String name, String annotationType) {
            return addAnnotationArgument(name, annotationType, new boolean[1]);
        }

        public AnnotationBuilder<T> addAnnotationArgument(String name, String annotationType, Consumer<AnnotationBuilder<?>> c) {
            boolean[] built = new boolean[1];
            AnnotationBuilder<?> bldr = addAnnotationArgument(name, annotationType, built);
            c.accept(bldr);
            if (built[0] == false) {
                throw new IllegalStateException("closeAnnotation not called");
            }
            return this;
        }

        private AnnotationBuilder<AnnotationBuilder<T>> addAnnotationArgument(String name, String annotationType, boolean[] built) {
            return new AnnotationBuilder<>(ab -> {
                arguments.put(name, ab);
                built[0] = true;
                return this;
            }, annotationType);
        }

        public AnnotationBuilder<T> addArgument(String name, String value) {
            arguments.put(name, new Adhoc(value));
            return this;
        }

        public AnnotationBuilder<T> addArgument(String name, Number value) {
            arguments.put(name, new Adhoc(value.toString()));
            return this;
        }

        public AnnotationBuilder<T> addStringArgument(String name, String value) {
            arguments.put(name, new Adhoc(LinesBuilder.stringLiteral(value)));
            return this;
        }

        public AnnotationBuilder<T> addClassArgument(String name, String type) {
            arguments.put(name, new Adhoc(type + ".class"));
            return this;
        }

        public T closeAnnotation() {
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.wrappable(l -> {
                lines.onNewLine().word("@" + annotationType);
                if (!arguments.isEmpty()) {
                    lines.parens(lb -> {
                        if (arguments.size() == 1 && arguments.containsKey("value")) {
                            arguments.get("value").buildInto(lines);
                        } else {
                            lines.wrappable(wb -> {
                                for (Iterator<Map.Entry<String, BodyBuilder>> it = arguments.entrySet().iterator(); it.hasNext();) {
                                    Map.Entry<String, BodyBuilder> e = it.next();
                                    wb.word(e.getKey()).word("=");
                                    e.getValue().buildInto(lines);
                                    if (it.hasNext()) {
                                        wb.appendRaw(", ");
                                    }
                                }
                            });
                        }
                    });
                }
            });
            lines.onNewLine();
        }
    }

    public static final class FieldBuilder<T> implements BodyBuilder {

        private final Function<FieldBuilder<T>, T> converter;
        private String type;
        private BodyBuilder initializer;
        private final Set<Modifier> modifiers = new TreeSet<>();
        private final String name;

        FieldBuilder(Function<FieldBuilder<T>, T> converter, String name) {
            this.converter = converter;
            this.name = name;
        }

        boolean isStatic() {
            return modifiers.contains(Modifier.STATIC);
        }

        public T initializedWithStringLiteral(String lit) {
            if (initializer != null) {
                throw new IllegalStateException("Initializer already set");
            }
            initializer = new Adhoc(LinesBuilder.stringLiteral(lit));
            type = "String";
            return converter.apply(this);
        }

        @Override
        public void buildInto(LinesBuilder lines) {
            lines.statement(lb -> {
                for (Modifier m : modifiers) {
                    lb.word(m.toString());
                }
                if (type != null) {
                    lb.word(type);
                }
                lb.word(name);
                if (initializer != null) {
                    if (!(initializer instanceof AssignmentBuilder<?>)) {
                        lb.word("=");
                    }
                    initializer.buildInto(lb);
                }
            });
        }

        public FieldBuilder<T> withInitializer(String init) {
            if (this.initializer != null) {
                throw new IllegalStateException("Initializer already set");
            }
            this.initializer = new Adhoc(init);
            return this;
        }

        public FieldBuilder<T> initializedFromInvocationOf(String method, Consumer<InvocationBuilder<?>> c) {
            boolean[] built = new boolean[1];
            InvocationBuilder<?> bldr = initializedFromInvocationOf(method, built);
            c.accept(bldr);
            if (!built[0]) {
                throw new IllegalStateException("Invocation builder was not completed (call on())");
            }
            return this;
        }

        public InvocationBuilder<FieldBuilder<T>> initializedFromInvocationOf(String method) {
            return initializedFromInvocationOf(method, new boolean[1]);
        }

        private InvocationBuilder<FieldBuilder<T>> initializedFromInvocationOf(String method, boolean[] built) {
            return new InvocationBuilder<>(ib -> {
                this.initializer = ib;
                built[0] = true;
                return this;
            }, method);
        }

        public AssignmentBuilder<T> assignedTo() {
            return new AssignmentBuilder<>(ab -> {
                initializer = ab;
                this.type = ab.type;
                return converter.apply(this);
            }, null);
        }

        public FieldBuilder<T> withModifier(Modifier mod) {
            switch (mod) {
                case ABSTRACT:
                case DEFAULT:
                case NATIVE:
                case STRICTFP:
                case SYNCHRONIZED:
                    throw new IllegalArgumentException("Inappropriate modifier for field: " + mod);
                case VOLATILE:
                    if (modifiers.contains(FINAL)) {
                        throw new IllegalStateException("Cannot combined volatile and final");
                    }
                    break;
                case FINAL:
                    if (modifiers.contains(VOLATILE)) {
                        throw new IllegalStateException("Cannot combined volatile and final");
                    }
                    break;
                case PRIVATE:
                    if (modifiers.contains(PROTECTED) || modifiers.contains(PUBLIC)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
                case PROTECTED:
                    if (modifiers.contains(PRIVATE) || modifiers.contains(PUBLIC)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
                case PUBLIC:
                    if (modifiers.contains(PRIVATE) || modifiers.contains(PROTECTED)) {
                        throw new IllegalStateException("Cannot be private and also protected or public");
                    }
                    break;
            }
            modifiers.add(mod);
            return this;
        }

        public T ofType(String type) {
            this.type = type;
            return converter.apply(this);
        }
    }

    static final class ClassBuilderStringFunction implements Function<ClassBuilder<String>, String> {
        // Type is used for top level test

        @Override
        public String apply(ClassBuilder<String> cb) {
            return cb.text();
        }
    }
}
