package org.nemesis.registration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import com.mastfrog.annotation.processor.Key;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.annotation.AnnotationUtils.stripMimeType;
import com.mastfrog.annotation.validation.MethodTestBuilder;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Tim Boudreau
 */
//@SupportedAnnotationTypes({KEYBINDING_ANNO, GOTO_ANNOTATION})
//@SupportedOptions(AnnotationUtils.AU_LOG)
//@ServiceProvider(service = Processor.class)
public class KeybindingsAnnotationProcessor extends LayerGeneratingDelegate {

    static final String KEYBINDING_ANNO = "org.nemesis.antlr.spi.language.keybindings.Keybindings";

    private static final String ABSTRACT_ACTION_TYPE = "javax.swing.AbstractAction";
    private static final String ACTION_TYPE = "javax.swing.Action";
    private static final String ACTION_EVENT_TYPE = "java.awt.event.ActionEvent";
    private static final String J_TEXT_COMPONENT_TYPE = "javax.swing.text.JTextComponent";
    private static final String DOCUMENT_TYPE = "javax.swing.text.Document";
    private static final String BASE_ACTION_TYPE = "org.netbeans.editor.BaseAction";
    private static final String ANTLR_UTILS_TYPE = "org.nemesis.antlr.spi.language.NbAntlrUtils";
    private static final String EXTRACTION_TYPE = "org.nemesis.extraction.Extraction";
    public static final String ANTLR_ACTION_ANNO_TYPE = "org.nemesis.antlr.spi.language.AntlrAction";
    private static final Set<String> legalMethodArguments = new HashSet<>(
            Arrays.asList(J_TEXT_COMPONENT_TYPE, DOCUMENT_TYPE, ACTION_EVENT_TYPE, EXTRACTION_TYPE));
    private final Map<String, KeysFile> files = new HashMap<>();
    private final Map<KeysFile, String> profileForKeysFile = new HashMap<>();
    private final Map<String, Element> targetElementForMimeType = new HashMap<>();
    private final Map<KeysFile, Element> elementForFile = new HashMap<>();
    private static int actionPosition = 11;

    private BiPredicate<? super AnnotationMirror, ? super Element> validator;

    void updateTargetElement(String mimeType, Element targetElement) {
        if (!targetElementForMimeType.containsKey(mimeType)) {
            targetElementForMimeType.put(mimeType, targetElement);
        }
    }

    static Key<String> actionsKey(String mimeType) {
        return key(String.class, "actionTargets-" + mimeType);
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        return validator.test(mirror, element);
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        validator = utils.multiAnnotations().whereAnnotationType(KEYBINDING_ANNO, bldr -> {
            bldr.testMember("mimeType").validateStringValueAsMimeType()
                    .build()
                    .testMember("name")
                    .stringValueMustNotContainWhitespace()
                    .stringValueMustNotContain('/', '\\', '"', '.')
                    .build();
            bldr.testMemberAsAnnotation("keybindings")
                    .testMember("modifiers", modifiersTest -> {
                        modifiersTest.enumValuesMayNotCombine("CTRL_OR_COMMAND", "EXPLICIT_CTRL")
                                .enumValuesMayNotCombine("ALT_OR_OPTION", "EXPLICIT_ALT");
                    })
                    .build();
            bldr.whereMethodIsAnnotated(methodVal -> {
                methodVal.doesNotHaveModifier(PRIVATE)
                        .hasModifier(STATIC)
                        .ifReturnTypeAssignableAs(ACTION_TYPE)
                        .ifTrue((MethodTestBuilder.MTB rt) -> {
                            rt.mustNotTakeArguments().build();
                        }).ifFalse((MethodTestBuilder.STB rt) -> {
                    rt.returnType().isType("void").build();
                });
            });
        }).whereAnnotationType(GOTO_ANNOTATION, bldr -> {
            bldr.testMember("mimeType").validateStringValueAsMimeType().build();
        }).build();
    }

    Element targetElement(String mimeType) {
        Element result = targetElementForMimeType.get(mimeType);
        if (result == null) {
            throw new IllegalArgumentException("No element recorded for " + mimeType);
        }
        return result;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if (mimeType != null) {
            updateTargetElement(mimeType, var);
            KeysFile keysFile = keysFile(mimeType, "NetBeans", false);
            keysFile.add(new KeybindingInfo("D-B", false, "goto-declaration"), var);
        }
        return false;
    }

    @Override
    protected boolean processMethodAnnotation(ExecutableElement method, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if (mimeType == null) {
            return true;
        }
        updateTargetElement(mimeType, method);
        List<AnnotationMirror> bindings = utils().annotationValues(mirror, "keybindings", AnnotationMirror.class);
        if (bindings.isEmpty()) {
            utils().fail("Keybindings list may not be empty", method, mirror);
        }

        TypeElement parent = AnnotationUtils.enclosingType(method);
        String actionName = (parent.getQualifiedName() + "-" + method.getSimpleName()).replace('.', '-').replace('$', '-').toLowerCase();
        Set<KeysFile> files = new HashSet<>();
        for (AnnotationMirror binding : bindings) {
            Set<String> modifiers = utils().enumConstantValues(binding, "modifiers");
            String key = utils().enumConstantValue(binding, "key");
            boolean mac = utils().annotationValue(binding, "appleSpecific", Boolean.class, false);
            List<String> profiles = utils().annotationValues(binding, "profiles", String.class);
            if (profiles.isEmpty()) {
                profiles = new ArrayList<>(Arrays.asList("NetBeans"));
            }
            for (String p : profiles) {
                KeysFile file = keysFile(mimeType, p, mac);
                String keybinding = keybinding(key, modifiers, method);
                file.add(new KeybindingInfo(keybinding, mac, actionName), method);
                files.add(file);
                elementForFile.put(file, method);
                profileForKeysFile.put(file, p);
                writeLayerEntriesForKeysFile(file, p, method, mimeType);
            }
        }
        generateActionImplementation(actionName, method, mimeType, mirror);
        return false;
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws IOException {
        for (KeysFile file : files.values()) {
            writeOneKeysFileAndActions(file, elementForFile.get(file));
        }
        files.clear();
        return false;
    }

    private void generateActionRegistrationForMethodReturningAction(String actionName, ExecutableElement method, String mimeType, AnnotationMirror mirror) throws IOException {
        TypeElement owningClass = AnnotationUtils.enclosingType(method);
        String fqn = owningClass.getQualifiedName() + "." + method.getSimpleName();
//        String actionRegistration = "Editors/" + mimeType + "/Actions/" + fqn.replace('.', '-') + ".instance";
        String fqnDashes = fqn.replace('.', '-');
        String actionRegistration = "Actions/" + stripMimeType(mimeType) + "/"
                + fqnDashes + ".instance";
        LayerBuilder layer = layer(method);
        layer.file(actionRegistration)
                .methodvalue("instanceCreate", owningClass.getQualifiedName().toString(), method.getSimpleName().toString())
                .stringvalue("instanceOf", ACTION_TYPE)
                .intvalue("position", actionPosition).write();
        layer.shadowFile(actionRegistration, "Editors/" + mimeType + "/Actions", fqnDashes)
                .intvalue("position", actionPosition)
                .write();
//        String stubClassName = AnnotationUtils.topLevelType(method).getSimpleName() + "_"
//                + method.getSimpleName() + "Stub";
//        ClassBuilder<String> cl = ClassBuilder.forPackage(utils().packageName(method))
//                .named(stubClassName)
//                .importing(ANTLR_ACTION_ANNO_TYPE, ACTION_TYPE)
//                .withModifier(PUBLIC, FINAL)
//                .publicMethod("actionStub", mb -> {
//                    mb.withModifier(STATIC)
//                            .annotatedWith(simpleName(ANTLR_ACTION_ANNO_TYPE))
//                            .addStringArgument("mimeType", mimeType)
//                            .addArgument("order", actionPosition)
//                            .closeAnnotation()
//                            .returning("Action")
//                            .body()
//                            .returningInvocationOf(method.getSimpleName().toString())
//                            .on(AnnotationUtils.enclosingType(method).getSimpleName().toString())
//                            .endBlock();
//                });
//        writeOne(cl);
        super.share(actionsKey(mimeType), owningClass.getQualifiedName() + "." + method.getSimpleName() + "()");
        actionPosition++;
    }

    private void generateActionImplementation(String actionName, ExecutableElement method, String mimeType, AnnotationMirror mirror) throws IOException, LayerGenerationException {
        // check return type
        if (utils().isAssignable(method.getReturnType(), ACTION_TYPE)) {
            if (!method.getParameters().isEmpty()) {
                utils().fail("Methods which return an action may not take parameters", method);
                return;
            }
            generateActionRegistrationForMethodReturningAction(actionName, method, mimeType, mirror);
            return;
        }
        String displayName = utils().annotationValue(mirror, "displayName", String.class);
        String programmaticName = utils().annotationValue(mirror, "name", String.class);
        if (programmaticName != null) {
            if (programmaticName.indexOf(' ') >= 0 || programmaticName.indexOf('\n') >= 0) {
                utils().fail("Value of name() may not contain whitespace, but found '" + programmaticName + "'");
            }
        }

        TypeElement owningClass = AnnotationUtils.enclosingType(method);
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(method);

        String defaultBundle = pkg.getQualifiedName() + "Bundle";

        String generatedClassName = owningClass.getSimpleName() + "_" + method.getSimpleName() + "_Action";
        List<String> argumentTypes = new ArrayList<>();
        for (VariableElement var : method.getParameters()) {
            String arg = var.asType().toString();
            if (!legalMethodArguments.contains(arg)) {
                utils().fail("Not one of " + legalMethodArguments + ": " + arg, method);
            }
            argumentTypes.add(arg);
        }
        String toSubclass;
        if (argumentTypes.isEmpty() || (argumentTypes.size() == 1 && argumentTypes.contains(ACTION_EVENT_TYPE))) {
            toSubclass = ABSTRACT_ACTION_TYPE;
        } else {
            toSubclass = BASE_ACTION_TYPE;
        }
//        boolean lockDocument = utils().annotationValue(mirror, "lockDocument", Boolean.class, false);
        boolean async = argumentTypes.contains(EXTRACTION_TYPE) || utils().annotationValue(mirror, "asynchronous", Boolean.class, false);
        ClassBuilder<String> clazz = ClassBuilder.forPackage(pkg.getQualifiedName()).named(generatedClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(toSubclass, ACTION_EVENT_TYPE, J_TEXT_COMPONENT_TYPE, ANTLR_ACTION_ANNO_TYPE)
                .importing(argumentTypes)
                .annotatedWith(simpleName(ANTLR_ACTION_ANNO_TYPE))
                .addStringArgument("mimeType", mimeType)
                .addArgument("order", actionPosition)
                .closeAnnotation()
                .extending(simpleName(toSubclass))
                // ActionEvent evt, JTextComponent target
                .override("actionPerformed", mb -> {
                    mb.withModifier(PUBLIC)
                            .addArgument(simpleName(ACTION_EVENT_TYPE), "evt");
                    if (BASE_ACTION_TYPE.equals(toSubclass)) {
                        mb.addArgument(simpleName(J_TEXT_COMPONENT_TYPE), "target");
                    }
                    Map<String, String> typeToVar = new HashMap<>();
                    typeToVar.put(ACTION_EVENT_TYPE, "evt");
                    typeToVar.put(J_TEXT_COMPONENT_TYPE, "target");
                    typeToVar.put(EXTRACTION_TYPE, "extraction");
                    Consumer<BlockBuilder<?>> invokeTheMethod = bb -> {
                        if (argumentTypes.contains(DOCUMENT_TYPE)) {
                            bb.declare("doc").initializedByInvoking("getDocument").on("target").as(simpleName(DOCUMENT_TYPE));
                            typeToVar.put(DOCUMENT_TYPE, "doc");
                        }
                        ClassBuilder.InvocationBuilder<?> invoker = bb.invoke(method.getSimpleName().toString());
                        for (int i = 0; i < argumentTypes.size(); i++) {
                            String argType = argumentTypes.get(i);
                            String var = typeToVar.get(argType);
                            invoker.withArgument(var);
                        }
                        invoker.on(owningClass.getQualifiedName().toString());
                        bb.endBlock();
                    };
                    if (!BASE_ACTION_TYPE.equals(toSubclass) && async) {
                        mb.body(bb -> {
                            bb.invoke("post").withLambdaArgument()
                                    .body(invokeTheMethod).on("EventQueue");
                            bb.lambda().body(invokeTheMethod);
                        });
                    } else {
                        if (argumentTypes.contains(EXTRACTION_TYPE)) {
                            mb.body(bb -> {
                                bb.invoke("parseImmediately").withArgumentFromInvoking("getDocument").on(typeToVar.get(J_TEXT_COMPONENT_TYPE))
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument(simpleName(EXTRACTION_TYPE), "extraction")
                                                    .withArgument("Exception", "thrown")
                                                    .body().ifCondition().variable("thrown").notEquals().literal("null")
                                                    .endCondition().invoke("printStackTrace").withArgument("thrown").on("Exceptions")
                                                    .endBlock().elseDo(invokeTheMethod).endBlock();
                                        }).on(simpleName(ANTLR_UTILS_TYPE));

                                bb.endBlock();
                            });
                        } else {
                            mb.body(invokeTheMethod);
                        }
                    }
                });
        if (displayName != null) {
            clazz.importing(ACTION_TYPE);
            clazz.constructor().setModifier(PUBLIC)
                    .body(bb -> {
                        if (displayName.contains("#")) {
                            clazz.importing("org.openide.util.NbBundle");
                            int ix = displayName.indexOf('#');
                            String bundleLookup = ix == 0 ? defaultBundle + displayName
                                    : displayName.substring(ix + 1);

                            bb.declare("description").initializedByInvoking("getMessage")
                                    .withArgument(generatedClassName + ".class")
                                    .withStringLiteral(bundleLookup)
                                    .on("NbBundle");

                            if (programmaticName != null) {
                                bb.invoke("putValue").withArgument("Action.NAME")
                                        .withStringLiteral(programmaticName).inScope();
                            } else {
                                bb.invoke("putValue").withArgument("Action.NAME")
                                        .withArgument("description").inScope();
                            }
                            bb.invoke("putValue").withArgument("Action.SHORT_DESCRIPTION").
                                    withArgument("description").inScope();
                        } else {
                            bb.invoke("putValue").withArgument("Action.NAME")
                                    .withStringLiteral(programmaticName == null
                                            ? displayName : programmaticName);
                            bb.invoke("putValue").withArgument("Action.SHORT_DESCRIPTION")
                                    .withStringLiteral(displayName).inScope();
                        }
                    });
        }
        if (argumentTypes.contains(EXTRACTION_TYPE)) {
            clazz.importing("org.openide.util.Exceptions", ANTLR_UTILS_TYPE);
        }
        if (async && BASE_ACTION_TYPE.equals(toSubclass)) {
            // yes, "asynchonous", not asynchronous
            clazz.override("asynchonous").returning("boolean").withModifier(PUBLIC)
                    .body().returning("true").endBlock();
        } else if (!BASE_ACTION_TYPE.equals(toSubclass) && async) {
            clazz.importing("java.awt.EventQueue");
        }
        super.share(actionsKey(mimeType), "new " + clazz.fqn() + "()");
        writeOne(clazz);
        LayerBuilder layer = layer(method);
        String fqnDashes = programmaticName == null ? clazz.fqn().replace('.', '-') : programmaticName;
        String actionRegistration = "Actions/" + stripMimeType(mimeType) + "/"
                + fqnDashes + ".instance";
        LayerBuilder.File layerFile = layer
                .file(actionRegistration)
                .intvalue("position", actionPosition)
                .stringvalue("instanceClass", clazz.fqn())
                .stringvalue("instanceof", ACTION_TYPE)
                .intvalue("position", 100);
        if (displayName != null) {
            if (displayName.contains("#")) {
                String bundleLookup = displayName;
                if (displayName.startsWith("#")) {
                    layerFile.bundlevalue("displayName", defaultBundle, bundleLookup);
                } else {
                    layerFile.bundlevalue("displayName", bundleLookup);
                }
            } else {
                layerFile.stringvalue("displayName", displayName);
            }
        }
        layer.shadowFile(actionRegistration, "Editors/" + mimeType + "/Actions", fqnDashes)
                .intvalue("position", actionPosition)
                .write();
        layerFile.write();
        actionPosition++;
    }

    private void writeOneKeysFileAndActions(KeysFile file, Element method) throws IOException {
        Filer filer = processingEnv.getFiler();
        String path = packagePath(file.mimeType()) + file.name;
        FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path, method);
        try (OutputStream out = resource.openOutputStream()) {
            out.write(file.toString().getBytes(UTF_8));
        }
    }

    private void writeLayerEntriesForKeysFile(KeysFile file, String profile, Element method, String mimeType) throws IOException {
        String layerDir = "Editors/" + mimeType + "/Keybindings";
        String layerPath = layerDir + "/" + profile + "/Defaults/" + file.name;
        String path = packagePath(file.mimeType()) + file.name;
        layer(method).file(layerPath).url("nbres:/" + path).write();
    }

    private String keysFileNamePrefix(String mimeType) {
        int ix = mimeType.indexOf('/');
        if (ix > 0 && ix < mimeType.length() - 1) {
            mimeType = mimeType.substring(ix + 1);
        }
        if (mimeType.startsWith("x-")) {
            mimeType = mimeType.substring(2);
        }
        return mimeType;
    }

    private String keysFileName(String mimeType, String profile, boolean mac) {
        String base
                = keysFileNamePrefix(mimeType).toLowerCase().replace('+', '-')
                + "-" + profile.toLowerCase() + "-keybindings";
        return (mac ? base + "-mac" : base) + ".xml";
    }

    private String packagePath(String mimeType) {
        PackageElement el = processingEnv.getElementUtils().getPackageOf(targetElement(mimeType));
        return el.getQualifiedName().toString().replace('.', '/') + '/';
    }

    private KeysFile keysFile(String mimeType, String profile, boolean mac) throws IOException, SAXException {
        String keysFileName = keysFileName(mimeType, profile, mac);
        KeysFile file = files.get(keysFileName);
        if (file == null) {
            file = new KeysFile(keysFileName, mimeType);
            files.put(keysFileName, file);
            String path = packagePath(mimeType) + keysFileName;
            Filer filer = processingEnv.getFiler();
            try {
                FileObject existing = filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
                try (InputStream in = existing.openInputStream()) {
                    file.loadExisting(in);
                }
            } catch (FileNotFoundException | NoSuchFileException e) {
                // ok
            }
        }
        return file;
    }

    private static class KeysFile {

        final String name;
        private final Set<String> bindings = new TreeSet<>();
        private final Map<String, Element> elements = new HashMap<>();
        private final String mimeType;

        public KeysFile(String name, String mimeType) {
            this.name = name;
            this.mimeType = mimeType;
        }

        String mimeType() {
            return mimeType;
        }

        void add(KeybindingInfo info, Element element) {
            String val = info.toString();
            bindings.add(val);
            elements.put(val, element);
        }

        void addAll(Collection<KeybindingInfo> all) {
            for (KeybindingInfo k : all) {
                bindings.add(k.toString());
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 61 * hash + Objects.hashCode(this.name);
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
            final KeysFile other = (KeysFile) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }

        private String elementToComment(Element el) {
            TypeElement type = AnnotationUtils.enclosingType(el);
            return "    <!-- " + type.getQualifiedName() + "." + el + " -->\n";
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE bindings PUBLIC \"-//NetBeans//DTD Editor KeyBindings settings 1.1//EN\" \"http://www.netbeans.org/dtds/EditorKeyBindings-1_1.dtd\">\n\n");
            sb.append("<bindings>\n");
            List<String> all = new ArrayList<>(bindings);
            Collections.sort(all);
            for (String k : all) {
                Element el = elements.get(k);
                if (el != null) {
                    sb.append(elementToComment(el));
                }
                sb.append("    ").append(k).append('\n');
            }
            sb.append("</bindings>\n");
            return sb.toString();
        }

        private void loadExisting(InputStream in) throws IOException, SAXException {
            Document doc = XMLUtil.parse(new InputSource(in), true, true, ERROR_HANDLER, ENTITY_RESOLVER);
            NodeList rootNodes = doc.getChildNodes();
            int rootLength = rootNodes.getLength();
            for (int i = 0; i < rootLength; i++) {
                // fontsColors node
                Node root = rootNodes.item(i);
                NodeList coloringEntries = root.getChildNodes();
                int entryCount = coloringEntries.getLength();
                for (int j = 0; j < entryCount; j++) {
                    Node fc = coloringEntries.item(j);
                    if (fc instanceof org.w3c.dom.Element) {
                        org.w3c.dom.Element fcEl = (org.w3c.dom.Element) fc;
//                        String nm = fcEl.getNodeName();
                        String actionName = fcEl.getAttribute("actionName");
                        String key = fcEl.getAttribute("key");
                        if ((actionName == null || actionName.isEmpty()) || (key == null || key.isEmpty())) {
                            continue;
                        }
                        String entry = "<bind actionName=\"" + name + "\" key=\"" + key + "\"/>";
                        bindings.add(entry);
                    }
                }
            }
        }

        private static final String LOCAL_DTD_RESOURCE = "/org/nemesis/registration/EditorKeyBindings-1_1.dtd";
        private static final String PUBLIC_DTD_ID = "-//NetBeans//DTD Editor KeyBindings settings 1.1//EN";
        private static final String NETWORK_DTD_URL = "http://www.netbeans.org/dtds/EditorKeyBindings-1_1.dtd";
        private static final ErrorHandler ERROR_HANDLER = new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        };

        private static final EntityResolver ENTITY_RESOLVER = new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                if (PUBLIC_DTD_ID.equals(publicId)) {
                    return new InputSource(KeybindingsAnnotationProcessor.class.getResource(LOCAL_DTD_RESOURCE).toString());
//                    return new InputSource(new URL(NETWORK_DTD_URL).openStream());
                } else {
                    return null;
                }
            }
        };

    }

    private static final class KeybindingInfo implements Comparable<KeybindingInfo> {

        final String keybinding;
        final boolean macSpecific;
        private final String actionName;

        public KeybindingInfo(String keybinding, boolean macSpecific, String actionName) {
            this.keybinding = keybinding;
            this.macSpecific = macSpecific;
            this.actionName = actionName;
        }

        @Override
        public String toString() {
            return "<bind actionName=\"" + actionName + "\" key=\"" + keybinding + "\"/>";
        }

        @Override
        public int compareTo(KeybindingInfo o) {
            int result = keybinding.compareTo(o.keybinding);
            if (result == 0) {
                if (macSpecific && !o.macSpecific) {
                    return 1;
                } else if (!macSpecific && o.macSpecific) {
                    return -1;
                }
            }
            return result;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 11 * hash + Objects.hashCode(this.keybinding);
            hash = 11 * hash + (this.macSpecific ? 1 : 0);
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
            final KeybindingInfo other = (KeybindingInfo) obj;
            if (this.macSpecific != other.macSpecific) {
                return false;
            }
            return Objects.equals(this.keybinding, other.keybinding);
        }

    }

    private String keybinding(String keyEnumConstant, Set<String> modifierEnumConstants, Element el) {
        return modifiersPrefix(modifierEnumConstants, el) + keyEventConstantForEnumName(keyEnumConstant, el);
    }

    private String modifiersPrefix(Set<String> names, Element el) {
        if (names.isEmpty()) {
            return "";
        }
        List<String> l = new ArrayList<>(names.size());
        for (String name : names) {
            l.add(modifierConstantForEnumName(name, el));
        }
        Collections.sort(l);
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            sb.append(s);
        }
        return sb.append("-").toString();
    }

    private String modifierConstantForEnumName(String name, Element el) {
        // SHIFT, CTRL_OR_COMMAND, ALT_OR_OPTION, EXPLICIT_CTRL, EXPLICIT_ALT
        switch (name) {
            case "SHIFT":
                return "S";
            case "CTRL_OR_COMMAND":
                return "D";
            case "ALT_OR_OPTION":
                return "O";
            case "EXPLICIT_CTRL":
                return "C";
            case "EXPLICIT_ALT":
                return "A";
            default:
                utils().fail("Unknown KeyModifier constant ", el);
                return "-";
        }
    }

    private String keyEventConstantForEnumName(String name, Element el) {
        switch (name) {
            case "A":
            case "ACCEPT":
            case "ADD":
            case "AGAIN":
            case "ALL_CANDIDATES":
            case "ALPHANUMERIC":
            case "ALT":
            case "ALT_GRAPH":
            case "AMPERSAND":
            case "ASTERISK":
            case "AT":
            case "B":
            case "BACK_QUOTE":
            case "BACK_SLASH":
            case "BACK_SPACE":
            case "BEGIN":
            case "BRACELEFT":
            case "BRACERIGHT":
            case "C":
            case "CANCEL":
            case "CAPS_LOCK":
            case "CIRCUMFLEX":
            case "CLEAR":
            case "CLOSE_BRACKET":
            case "CODE_INPUT":
            case "COLON":
            case "COMMA":
            case "COMPOSE":
            case "CONTEXT_MENU":
            case "CONTROL":
            case "CONVERT":
            case "COPY":
            case "CUT":
            case "D":
            case "DEAD_ABOVEDOT":
            case "DEAD_ABOVERING":
            case "DEAD_ACUTE":
            case "DEAD_BREVE":
            case "DEAD_CARON":
            case "DEAD_CEDILLA":
            case "DEAD_CIRCUMFLEX":
            case "DEAD_DIAERESIS":
            case "DEAD_DOUBLEACUTE":
            case "DEAD_GRAVE":
            case "DEAD_IOTA":
            case "DEAD_MACRON":
            case "DEAD_OGONEK":
            case "DEAD_SEMIVOICED_SOUND":
            case "DEAD_TILDE":
            case "DEAD_VOICED_SOUND":
            case "DECIMAL":
            case "DELETE":
            case "DIVIDE":
            case "DOLLAR":
            case "DOWN":
            case "E":
            case "END":
            case "ENTER":
            case "EQUALS":
            case "ESCAPE":
            case "EURO_SIGN":
            case "EXCLAMATION_MARK":
            case "F":
            case "F1":
            case "F10":
            case "F11":
            case "F12":
            case "F13":
            case "F14":
            case "F15":
            case "F16":
            case "F17":
            case "F18":
            case "F19":
            case "F2":
            case "F20":
            case "F21":
            case "F22":
            case "F23":
            case "F24":
            case "F3":
            case "F4":
            case "F5":
            case "F6":
            case "F7":
            case "F8":
            case "F9":
            case "FINAL":
            case "FIND":
            case "FULL_WIDTH":
            case "G":
            case "GREATER":
            case "H":
            case "HALF_WIDTH":
            case "HELP":
            case "HIRAGANA":
            case "HOME":
            case "I":
            case "INPUT_METHOD_ON_OFF":
            case "INSERT":
            case "INVERTED_EXCLAMATION_MARK":
            case "J":
            case "JAPANESE_HIRAGANA":
            case "JAPANESE_KATAKANA":
            case "JAPANESE_ROMAN":
            case "K":
            case "KANA":
            case "KANA_LOCK":
            case "KANJI":
            case "KATAKANA":
            case "KP_DOWN":
            case "KP_LEFT":
            case "KP_RIGHT":
            case "KP_UP":
            case "L":
            case "LEFT":
            case "LEFT_PARENTHESIS":
            case "LESS":
            case "M":
            case "META":
            case "MINUS":
            case "MODECHANGE":
            case "MULTIPLY":
            case "N":
            case "NONCONVERT":
            case "NUMBER_SIGN":
            case "NUMPAD0":
            case "NUMPAD1":
            case "NUMPAD2":
            case "NUMPAD3":
            case "NUMPAD4":
            case "NUMPAD5":
            case "NUMPAD6":
            case "NUMPAD7":
            case "NUMPAD8":
            case "NUMPAD9":
            case "NUM_LOCK":
            case "O":
            case "OPEN_BRACKET":
            case "P":
            case "PAGE_DOWN":
            case "PAGE_UP":
            case "PASTE":
            case "PAUSE":
            case "PERIOD":
            case "PLUS":
            case "PREVIOUS_CANDIDATE":
            case "PRINTSCREEN":
            case "PROPS":
            case "Q":
            case "QUOTE":
            case "QUOTEDBL":
            case "R":
            case "RIGHT":
            case "RIGHT_PARENTHESIS":
            case "ROMAN_CHARACTERS":
            case "S":
            case "SCROLL_LOCK":
            case "SEMICOLON":
            case "SEPARATER":
            case "SEPARATOR":
            case "SHIFT":
            case "SLASH":
            case "SPACE":
            case "STOP":
            case "SUBTRACT":
            case "T":
            case "TAB":
            case "U":
            case "UNDEFINED":
            case "UNDERSCORE":
            case "UNDO":
            case "UP":
            case "V":
            case "W":
            case "WINDOWS":
            case "X":
            case "Y":
            case "Z":
                return name;
            case "EIGHT":
                return "8";
            case "FIVE":
                return "5";
            case "FOUR":
                return "4";
            case "NINE":
                return "9";
            case "ONE":
                return "1";
            case "SEVEN":
                return "7";
            case "SIX":
                return "6";
            case "THREE":
                return "3";
            case "TWO":
                return "2";
            case "ZERO":
                return "0";
            default:
                utils().fail("Unkonwn key constant '" + name + "'", el);
                return "";
        }
    }
}
