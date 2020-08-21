/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.memory;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Lng;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.PrintStream;
import org.nemesis.antlr.memory.tool.MemoryTool;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * Can run Antlr in-memory over an instance of JFS.
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerator {

    private static final Logger LOG = Logger.getLogger(AntlrGenerator.class.getName());
    private final Charset grammarEncoding;
    private final boolean generateAll;
    private final String packageName;
    final Supplier<JFS> jfs;
    private final JavaFileManager.Location grammarSourceLocation;
    private final UnixPath virtualSourcePath;
    private final UnixPath virtualImportDir;
    private final Set<AntlrGenerationOption> opts = EnumSet.noneOf(AntlrGenerationOption.class);
    private final JavaFileManager.Location outputLocation;
    private final Path originalFile;
    private final String originalTokensHash;
    private JFSPathHints pathHints;
    final RerunInterceptor interceptor;

    static AntlrGenerator fromResult(AntlrGenerationResult result) {
        return new AntlrGenerator(AntlrGeneratorBuilder.fromResult(result));
    }

    public AntlrGenerator(Charset grammarEncoding, boolean generateAll, String packageName,
            Supplier<JFS> jfs, JavaFileManager.Location grammarSourceLocation, UnixPath virtualSourcePath,
            UnixPath virtualImportDir, JavaFileManager.Location outputLocation,
            Path originalFile, String originalTokensHash, JFSPathHints pathHints, RerunInterceptor interceptor) {
        this.grammarEncoding = grammarEncoding;
        this.generateAll = generateAll;
        this.packageName = packageName;
        this.jfs = jfs;
        this.grammarSourceLocation = grammarSourceLocation;
        this.virtualSourcePath = virtualSourcePath;
        this.virtualImportDir = virtualImportDir;
        this.outputLocation = outputLocation;
        this.originalFile = notNull("originalFile", originalFile);
        this.originalTokensHash = originalTokensHash;
        this.pathHints = pathHints == null ? JFSPathHints.NONE : pathHints;
        this.interceptor = interceptor;
    }

    public JFSPathHints hints() {
        return pathHints == null ? JFSPathHints.NONE : pathHints;
    }

    public Path originalFile() {
        return originalFile;
    }

    public String tokensHash() {
        return originalTokensHash;
    }

    public AntlrGenerator withFileInfo(Path originalPath, String tokensHash) {
        if (Objects.equals(originalFile, originalPath) && Objects.equals(tokensHash, originalTokensHash)) {
            return this;
        }
        return new AntlrGenerator(grammarEncoding, generateAll,
                packageName, jfs, grammarSourceLocation, virtualSourcePath,
                virtualImportDir, outputLocation, originalFile, originalTokensHash, pathHints, interceptor);
    }

    public JavaFileManager.Location sourceLocation() {
        return grammarSourceLocation;
    }

    public JavaFileManager.Location outputLocation() {
        return outputLocation;
    }

    public String packageName() {
        return packageName;
    }

    public Charset encoding() {
        return grammarEncoding;
    }

    public UnixPath importDir() {
        return virtualImportDir();
    }

    public UnixPath sourcePath() {
        return virtualSourcePath;
    }

    /**
     * If running using classloader isolation, these are the packages that
     * directly touch Antlr (which may be a different version than on the module
     * classpath).
     *
     * @return
     */
    public static String[] antlrPackages() {
        return new String[]{MemoryTool.class.getPackage().getName()};
    }

    /**
     * If running using classloader isolation, these are the packages which need
     * to be visible to both the Antlr classloader and the parent classloader.
     *
     * @return
     */
    public static String[] accessiblePackagesFromParentClassloader() {
        return new String[]{
            ParsedAntlrError.class.getPackage().getName(),
            AntlrGenerator.class.getPackage().getName(),
            JFS.class.getPackage().getName(),
            "org.nemesis.jfs.javac",
            "org.nemesis.jfs.nio",
            "org.nemesis.jfs.spi",
            JFSUtilities.getDefault().getClass().getPackage().getName()
        };
    }

    public static AntlrGenerator create(AntlrGeneratorBuilder<?> bldr) {
        return new AntlrGenerator(bldr);
    }

    public AntlrGeneratorBuilder<AntlrGenerationResult> toBuilder() {
        return AntlrGeneratorBuilder.fromGenerator(this);
    }

    AntlrGenerator(AntlrGeneratorBuilder<?> b) {
        this.jfs = b.jfs;
        if (b.genListener) {
            opts.add(AntlrGenerationOption.GENERATE_LISTENER);
        }
        if (b.genVisitor) {
            opts.add(AntlrGenerationOption.GENERATE_VISITOR);
        }
        if (b.generateATNDot) {
            opts.add(AntlrGenerationOption.GENERATE_ATN);
        }
        if (b.genDependencies) {
            opts.add(AntlrGenerationOption.GENERATE_DEPENDENCIES);
        }
        if (b.longMessages) {
            opts.add(AntlrGenerationOption.LONG_MESSAGES);
        }
        if (b.log) {
            opts.add(AntlrGenerationOption.LOG);
        }
        if (b.forceAtn) {
            opts.add(AntlrGenerationOption.FORCE_ATN);
        }
        this.generateAll = b.generateAll;
        this.grammarEncoding = b.grammarEncoding;
        this.grammarSourceLocation = b.grammarSourceInputLocation;
        this.outputLocation = b.javaSourceOutputLocation;
        this.virtualSourcePath = b.sourcePath;
        this.packageName = b.packageName;
        this.virtualImportDir = b.importDir;
        this.originalTokensHash = b.tokensHash;
        this.originalFile = notNull("b.originalFile", b.originalFile);
        this.pathHints = b.pathHints;
        this.interceptor = b.interceptor;
    }

    public static <T> AntlrGeneratorBuilder<T> builder(Supplier<JFS> jfs, Function<? super AntlrGeneratorBuilder<T>, T> func) {
        return new AntlrGeneratorBuilder<>(jfs, func);
    }

    public static AntlrGeneratorBuilder<AntlrGenerator> builder(Supplier<JFS> jfs) {
        return builder(jfs, AntlrGenerator::new);
    }

    public JFS jfs() {
        return jfs.get();
    }

    public Supplier<JFS> jfsSupplier() {
        return jfs;
    }

    public UnixPath packagePath() {
        return UnixPath.get(packageName.replace('.', '/'));
    }

    public UnixPath grammarFilePath(String fileName) {
        String baseName = fileName;
        if (fileName.indexOf('.') < 0) {
            fileName += ".g4";
        }
        UnixPath result = pathHints.firstPathForRawName(baseName, "g4", "g");
        if (result == null) {
            result = packagePath().resolve(fileName);
        }
        JFSFileObject fo = jfs.get().get(sourceLocation(), result);
        if (fo == null) {
            result = packagePath().resolve(UnixPath.get(baseName).rawName() + ".g");
        }
        return result;
    }

    private UnixPath resolveSourcePath(String grammarFileName) {
        // In the case of grammars in the default package, there may be
        // some issues with null parents and resolving siblings, because of
        // how UnixPath works.
        UnixPath result = virtualSourcePath;
        if (result == null || result.toString().isEmpty()) {
            return grammarFilePath(grammarFileName);
        }
        return result;
    }

    private String listJFS() {
        StringBuilder sb = new StringBuilder("Input JFS:");
        jfs.get().list(sourceLocation(), (loc, jfo) -> {
            sb.append('\n').append(jfo.getName()).append(" len ").append(jfo.length());
        });
        return sb.toString();
    }

    public interface ReRunner {

        AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate);
    }

    public interface RerunInterceptor {

        AntlrGenerationResult rerun(String grammarFileName, PrintStream logStream, boolean generate, AntlrGenerator originator, ReRunner localRerunner);
    }

    public AntlrGenerationResult run(String grammarFileName, PrintStream logStream, boolean generate) {
        if (interceptor == null) {
            return internalRun(grammarFileName, logStream, generate);
        } else {
            return interceptor.rerun(grammarFileName, logStream, generate, this, this::internalRun);
        }
    }

    private AntlrGenerationResult internalRun(String grammarFileName, PrintStream logStream, boolean generate) {
//        System.out.println("RUN " + grammarFileName);
//        System.out.println(listJFS());
        return Debug.runObject(this, "Generate " + grammarFileName + " - " + generate, () -> {
            logStream.println("Begin generation of '" + grammarFileName + "' generated=" + generate
                    + " generateAll? " + generateAll);
            List<ParsedAntlrError> errors = new ArrayList<>();
            List<String> infos = new ArrayList<>();
            Throwable thrown = null;
            int code = -1;
            Map<JFSFileObject, Long> modificationDates = new HashMap<>();
            Set<JFSCoordinates.Resolvable> files = new HashSet<>();
//            JFSFileObject[] grammarFile = new JFSFileObject[1];
//            long[] grammarFileLastModified = new long[]{0};
            Set<Grammar> grammars = new HashSet<>();
            Obj<Grammar> mainGrammar = Obj.create();
//            Grammar[] mainGrammar = new Grammar[1];
//            boolean[] success = new boolean[]{true};
            Bool success = Bool.create(true);
            Lng grammarFileLastModified = Lng.create();
            Obj<Map<UnixPath, Set<UnixPath>>> outputFiles = Obj.of(Collections.emptyMap());
            Obj<Map<UnixPath, Set<UnixPath>>> dependencies = Obj.of(Collections.emptyMap());
            Obj<Map<String, Set<UnixPath>>> inputFiles = Obj.of(Collections.emptyMap());
            Obj<Map<String, UnixPath>> primaryInputFileForGrammarName = Obj.of(Collections.emptyMap());
            Obj<JFSCoordinates.Resolvable> grammarFile = Obj.create();
            Obj<String> gn = Obj.of("--");
            Lng timestamp = Lng.of(System.currentTimeMillis());
            JFS jfs = this.jfs.get();
            try {
                String[] args = AntlrGenerationOption.toAntlrArguments(
                        resolveSourcePath(grammarFileName),
                        opts,
                        grammarEncoding,
                        packageName,
                        virtualImportDir());
                MemoryTool.run(virtualSourcePath, jfs, grammarSourceLocation,
                        outputLocation, logStream, args, tool -> {
                            if (this.pathHints != null) {
                                tool.hints = this.pathHints;
                            }
                            UnixPath grammarFilePath;
                            String grammarName = "--";
                            tool.generate_ATN_dot = opts.contains(AntlrGenerationOption.GENERATE_ATN);
                            tool.grammarEncoding = grammarEncoding.name();
                            tool.gen_dependencies = opts.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
                            tool.longMessages = opts.contains(AntlrGenerationOption.LONG_MESSAGES);
                            tool.log = opts.contains(AntlrGenerationOption.LOG);
                            tool.force_atn = opts.contains(AntlrGenerationOption.FORCE_ATN);
                            tool.genPackage = packageName;
                            BiConsumer<Location, JFSFileObject> modificationDateCollector = (loc, fo) -> {
                                files.add(fo.toReference());
                                modificationDates.put(fo, fo.getLastModified());
                            };
                            jfs.list(grammarSourceLocation, modificationDateCollector);
                            jfs.list(outputLocation, modificationDateCollector);

                            grammarFilePath = grammarFilePath(grammarFileName);
                            logStream.println("Grammar File Path:\t" + grammarFilePath);

                            timestamp.set(System.currentTimeMillis());
                            mainGrammar.set(tool.withCurrentPath(grammarFilePath, () -> {
                                Grammar result = tool.loadGrammar(grammarFileName, fo -> {
                                    grammarFileLastModified.set(fo.getLastModified());
                                    grammarFile.set(fo.toReference());
                                });
                                logStream.println("loaded main grammar " + (result == null ? "null"
                                        : (result.name + " / " + result.fileName + " " + result.getTypeString())));
                                if (result != null) {
                                    grammars.add(result);
                                }
                                if (generateAll) {
                                    tool.withCurrentPath(grammarFilePath, () -> {
                                        generateAllGrammars(tool, result, new HashSet<>(), generate, grammars, jfs);
                                        return null;
                                    });
                                }
                                return result;
                            }));
                            if (mainGrammar.isSet()) {
                                grammarName = mainGrammar.get().name;
                                gn.set(grammarName);
                                Debug.success("Generated " + grammarName, this::toString);
                            } else {
                                Debug.failure("Not-generated " + grammarFileName, this::toString);
                                success.set(false);
                            }
                            outputFiles.set(tool.outputFiles());
                            inputFiles.set(tool.inputFiles());
                            primaryInputFileForGrammarName.set(tool.primaryInputFiles());
                            dependencies.set(tool.dependencies());
                            List<ParsedAntlrError> errs = tool.errors();
                            errors.addAll(errs);
                            logStream.println("Raw error count " + tool.originalErrorCount()
                                    + " with coalesce/epsilon processing " + errs.size());
                            infos.addAll(tool.infoMessages());
                            return null;
                        });
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Error loading grammar " + grammarFileName, ex);
                ex.printStackTrace(logStream);
                thrown = ex;
                success.set(false);
//                LOG.log(Level.SEVERE, gn.get(), ex);
            }
            Set<JFSCoordinates.Resolvable> postFiles = new HashSet<>();
            Map<JFSCoordinates.Resolvable, Long> touchedLastModified = new HashMap<>();
            try {
                if (!errors.isEmpty()) {
                    LOG.log(Level.FINE, "Errors generating virtual Antlr sources");
                    if (LOG.isLoggable(Level.FINEST)) {
                        for (ParsedAntlrError e : errors) {
                            LOG.log(Level.FINEST, "Antlr error: {0}", e);
                        }
                    }
                }
                if (success.get() && !errors.isEmpty()) {
                    for (ParsedAntlrError e : errors) {
                        if (e.isError()) {
                            success.set(false);
                            break;
                        }
                    }
                }
                BiConsumer<Location, JFSFileObject> modUpdater = (loc, file) -> {
                    if (LOG.isLoggable(Level.FINEST) && !files.contains(file)) {
                        LOG.log(Level.FINEST, "New file in {0}: {1}",
                                new Object[]{loc, file.path()});
                    }
                    Long mod = modificationDates.get(file);
                    long currentLastModified = file.getLastModified();
                    JFSCoordinates.Resolvable ref = file.toReference();
                    if (mod == null) {
                        touchedLastModified.put(ref, currentLastModified);
                    } else if (mod < currentLastModified) {
                        touchedLastModified.put(ref, currentLastModified);
                    } else if (file.storageKind().isMasqueraded()) {
                        touchedLastModified.put(ref, currentLastModified);
                    }
                    postFiles.add(ref);
                };

                jfs.list(grammarSourceLocation, modUpdater);
                jfs.list(outputLocation, modUpdater);

                postFiles.removeAll(files);
                code = MemoryTool.attemptedExitCode(thrown);
            } catch (Exception ex) {
                thrown = ex;
            }
            return new AntlrGenerationResult(success.getAsBoolean(), code, thrown, gn.get(),
                    mainGrammar.get(), errors, grammarFile.get(),
                    grammarFileLastModified.getAsLong(),
                    infos, postFiles, touchedLastModified, grammars, jfs,
                    grammarSourceLocation, outputLocation, packageName,
                    virtualSourcePath, virtualImportDir, this.generateAll,
                    this.opts, this.grammarEncoding, originalTokensHash,
                    originalFile, this.jfs, outputFiles.get(), inputFiles.get(),
                    primaryInputFileForGrammarName.get(), dependencies.get(),
                    timestamp.get(), pathHints, interceptor);
        });
    }

    private static String keyFor(Grammar g) {
        return g.name + ":" + g.getTypeString();
    }

    private UnixPath virtualImportDir() {
        return virtualImportDir == null ? UnixPath.get("imports") : virtualImportDir;
    }

    private void generateAllGrammars(MemoryTool tool, Grammar g,
            Set<String> seen, boolean generate, Set<Grammar> grammars, JFS jfs) {
        if (g != null && !seen.contains(keyFor(g))) {
            LOG.log(Level.FINEST, "MemoryTool generating {0}", g.fileName);
            seen.add(keyFor(g));
            if (g.implicitLexer != null) {
                tool.process(g.implicitLexer, generate);
            }
            try {
                tool.process(g, generate);
            } catch (RuntimeException ex) {
                if ("set is empty".equals(ex.getMessage())) {
                    // bad source - a partially written
                    // character set, e.g. fragment FOO : [\p{...];
                    LOG.log(Level.INFO, "Bad character set", ex);
                    tool.errMgr.emit(ErrorType.ERROR_READING_IMPORTED_GRAMMAR,
                            new ANTLRMessage(ErrorType.ERROR_READING_IMPORTED_GRAMMAR, ex, null));
                }
            }
            if (g.isCombined()) {
                String suffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParser.LEXER);
                String lexer = g.name + suffix + ".g4";
                UnixPath srcPath = packagePath().resolve(lexer);
                JFSFileObject lexerFo = jfs.get(grammarSourceLocation, srcPath);
                if (lexerFo == null) {
                    pathHints.firstPathForRawName(g.name, "g4", "g");
                }
                if (lexerFo == null) {
                    lexer = g.name + suffix + ".g";
                    srcPath = packagePath().resolve(lexer);
                    lexerFo = jfs.get(grammarSourceLocation, srcPath);
                }
                if (lexerFo == null) {
                    srcPath = virtualImportDir().resolve(lexer);
                    lexerFo = jfs.get(grammarSourceLocation, srcPath);
                }
                if (lexerFo != null) {
                    try {
                        JFSFileObject finalLexerFo = lexerFo;
                        Grammar lexerGrammar = tool.withCurrentPathThrowing(lexerFo.path(), () -> {
                            Grammar result = tool.loadDependentGrammar(g.name, finalLexerFo);
                            LOG.log(Level.FINEST, "Generate lexer {0}", result.fileName);
                            return result;
                        });
                        grammars.add(lexerGrammar);
                        generateAllGrammars(tool, lexerGrammar, seen, generate, grammars, jfs);
                    } catch (IOException ioe) {
                        throw new IllegalStateException(ioe);
                    }
                }
            }
            grammars.addAll(tool.allGrammars());
        }
    }

    @Override
    public String toString() {
        return "AntlrRunner{" + "grammarEncoding=" + grammarEncoding
                + ", generateAll=" + generateAll + ", packageName="
                + packageName + ", sourceLocation=" + grammarSourceLocation
                + ", virtualSourcePath=" + virtualSourcePath
                + ", virtualImportDir=" + virtualImportDir()
                + ", opts=" + opts
                + ", outputLocation=" + outputLocation + '}';
    }
}
