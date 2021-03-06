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

import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import org.nemesis.jfs.result.UpToDateness;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.tools.JavaFileManager.Location;
import org.antlr.v4.tool.Grammar;
import org.nemesis.antlr.memory.AntlrGenerator.RerunInterceptor;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.debug.api.Trackables;
import org.nemesis.jfs.JFSFileModifications;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.JFSFileModifications.FileChanges;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.result.ProcessingResult;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrGenerationResult implements ProcessingResult {

    public final boolean success;
    public final int code;
    public final Throwable thrown;
    public final String grammarName;
    public final Grammar mainGrammar;
    public final List<ParsedAntlrError> errors;
    public final long grammarFileLastModified;
    public final Set<Grammar> allGrammars;
    public final JFS jfs;
    public final Location grammarSourceLocation;
    public final Location javaSourceOutputLocation;
    public final String packageName;
    public final JFSCoordinates grammarFile;
    public final UnixPath sourceDir;
    public final UnixPath importDir;
    public final boolean generateAll;
    public final Set<AntlrGenerationOption> options;
    public final Charset grammarEncoding;
    public final String tokensHash;
    public final Path originalFilePath;
    public final Supplier<JFS> jfsSupplier;
    public final Set<JFSCoordinates> outputFiles;
    public final Set<JFSCoordinates> inputFiles;
    public final Map<JFSCoordinates, Set<JFSCoordinates>> outputDependencies;
    public final Map<String, Set<JFSCoordinates>> inputFilesForGrammarName;
    public final Map<String, JFSCoordinates> primaryFileForGrammarName;
    public final Map<JFSCoordinates, Set<JFSCoordinates>> dependencies;
    public final long timestamp;
    public final JFSPathHints hints;
    final RerunInterceptor interceptor;
    private final JFSFileModifications outputFileModifications;
    private final JFSFileModifications inputFileModifications;

    @SuppressWarnings("LeakingThisInConstructor")
    AntlrGenerationResult(boolean success, int code, Throwable thrown,
            String grammarName, Grammar grammar, List<ParsedAntlrError> errors,
            JFSCoordinates grammarFile, long grammarFileLastModified,
            Set<Grammar> allGrammars,
            JFS jfs, Location inputLocation, Location outputLocation,
            String packageName, UnixPath virtualSourceDir, UnixPath virtualInputDir,
            boolean generateAll, Set<AntlrGenerationOption> options,
            Charset grammarEncoding, String tokensHash, Path originalFilePath,
            Supplier<JFS> jfsSupplier, Map<JFSCoordinates, Set<JFSCoordinates>> outputFiles,
            Map<String, Set<JFSCoordinates>> inputFiles, Map<String, JFSCoordinates> primaryFiles,
            Map<JFSCoordinates, Set<JFSCoordinates>> dependencies,
            long timestamp, JFSPathHints hints, RerunInterceptor interceptor,
            Set<JFSCoordinates> allOutputFiles, Set<JFSCoordinates> allInputFiles,
            JFSFileModifications outputFileModifications,
            JFSFileModifications inputFileModifications) {
        this.outputFiles = allOutputFiles;
        this.inputFiles = allInputFiles;
        this.outputFileModifications = outputFileModifications;
        this.inputFileModifications = inputFileModifications;
        this.success = success;
        this.jfsSupplier = jfsSupplier;
        this.code = code;
        this.thrown = thrown;
        this.timestamp = timestamp;
        this.grammarName = grammarName;
        this.mainGrammar = grammar;
        this.errors = Collections.unmodifiableList(errors);
        this.grammarFileLastModified = grammarFileLastModified;
        this.allGrammars = Collections.unmodifiableSet(allGrammars);
        this.jfs = jfs;
        this.grammarSourceLocation = inputLocation;
        this.javaSourceOutputLocation = outputLocation;
        this.grammarFile = grammarFile;
        this.packageName = packageName;
        this.sourceDir = virtualSourceDir;
        this.importDir = virtualInputDir;
        this.generateAll = generateAll;
        this.options = Collections.unmodifiableSet(EnumSet.copyOf(options));
        this.grammarEncoding = grammarEncoding;
        this.tokensHash = tokensHash;
        this.originalFilePath = notNull("originalFilePath", originalFilePath);
        this.primaryFileForGrammarName = primaryFiles;
        this.outputDependencies = outputFiles;
        this.inputFilesForGrammarName = inputFiles;
        this.dependencies = dependencies;
        this.hints = hints;
        this.interceptor = interceptor;
        Trackables.track(AntlrGenerationResult.class, this);
    }

    /**
     * Get a new AntlrGenerationResult with the contents of this one, but with
     * the JFSSupplier's current JFS and file modification sets which will only
     * show a modification if one of the files they track is modified in the
     * future.
     *
     * @return A new result
     */
    public AntlrGenerationResult recycle() {
        JFS newJFS = jfsSupplier.get();
        JFSFileModifications inModifications = inputFileModifications == null ? null
                : inputFileModifications.overJFS(jfs).withUpdatedState();
        JFSFileModifications outModifications = outputFileModifications == null ? null
                : outputFileModifications.overJFS(jfs).withUpdatedState();
        return new AntlrGenerationResult(success, code, thrown, grammarName, mainGrammar,
                errors, grammarFile, grammarFileLastModified, allGrammars,
                newJFS, grammarSourceLocation, javaSourceOutputLocation, packageName,
                sourceDir, importDir, generateAll, options, grammarEncoding, tokensHash,
                originalFilePath, jfsSupplier, outputDependencies, inputFilesForGrammarName,
                primaryFileForGrammarName, dependencies, System.currentTimeMillis(),
                hints, interceptor, outputFiles, inputFiles, outModifications,
                inModifications);
    }

    public JFSFileModifications inputFileModifications() {
        return inputFileModifications == null ? JFSFileModifications.empty()
                : inputFileModifications.snapshot();
    }

    public JFSFileModifications outputFileModifications() {
        return inputFileModifications == null ? JFSFileModifications.empty()
                : inputFileModifications.snapshot();
    }

    public AntlrGenerationResult cleanOldOutput() throws IOException {
        if (!jfs.isEmpty()) {
            jfs.whileWriteLocked(() -> {
                Set<JFSFileObject> set = new HashSet<>();
                for (JFSCoordinates f : outputFiles) {
                    JFSFileObject ob = f.resolve(jfs);
                    if (ob != null) {
                        if (ob.storageKind().isMasqueraded()) {
                            continue;
                        }
                        set.add(ob);
                    }
                }
                for (JFSFileObject fo : set) {
                    fo.delete();
                }
                return null;
            });
        }
        return this;
    }

    /**
     * Create a copy of this result, as if it were for building a sibling
     * grammar that was built when this one was, if that sibling grammar was
     * built at the same time as this one - get a build result for a lexer that
     * had to be built for the grammar to be built.
     *
     * @param originalFilePath
     * @param jfsPath
     * @return
     */
    public AntlrGenerationResult forSiblingGrammar(Path originalFilePath, UnixPath jfsPath, String tokensHash) {
        if (jfsPath.equals(grammarFile.path())) {
            return this;
        }
        JFS jfs = jfsSupplier.get();
        JFSCoordinates targetGrammarCoordinates = JFSCoordinates.forPath(jfsPath, inputFiles);
        JFSFileObject fo = null;
        if (targetGrammarCoordinates == null) {
            fo = jfs.get(grammarSourceLocation, jfsPath);
            if (fo != null) {
                targetGrammarCoordinates = fo.toCoordinates();
            }
        }
        String filePath = jfsPath.toString();
        Grammar target = null;
        for (Grammar g : allGrammars) {
            if (filePath.equals(g.fileName)) {
                target = g;
                break;
            }
        }
        String targetGrammarName = target == null || target.name == null ? jfsPath.rawName() : target.name;
        Map<String, JFSCoordinates> fakePrimaries = new HashMap<>();
        String mainGrammarName = this.grammarName == null ? "" : this.grammarName;
        for (Map.Entry<String, JFSCoordinates> e : primaryFileForGrammarName.entrySet()) {
            if (mainGrammarName.equals(e.getKey())) {
                if (!grammarFile.equals(e.getValue())) {
                    fakePrimaries.put(targetGrammarName, e.getValue());
                    continue;
                }
            }
            fakePrimaries.put(e.getKey(), e.getValue());
        }
        if (target == null) {
            return null;
        }
        if (fo == null) {
            fo = targetGrammarCoordinates.resolve(jfs);
        }
        if (fo == null) {
            return null;
        }
        List<ParsedAntlrError> filteredErrors = errors.isEmpty()
                ? Collections.emptyList() : new ArrayList<>(errors.size());
        if (!errors.isEmpty()) {
            for (ParsedAntlrError pae : errors) {
                if (jfsPath.equals(pae.path())) {
                    filteredErrors.add(pae);
                }
            }
        }
        long ts = fo.getLastModified();

        return new AntlrGenerationResult(success, code, thrown, target.name, target, filteredErrors,
                fo.toReference(), ts,
                allGrammars, jfs, targetGrammarCoordinates.location(), javaSourceOutputLocation, packageName,
                sourceDir, importDir, generateAll, options,
                grammarEncoding, tokensHash, originalFilePath, jfsSupplier, outputDependencies, inputFilesForGrammarName,
                fakePrimaries, dependencies, timestamp, hints, interceptor, outputFiles, inputFiles,
                outputFileModifications, inputFileModifications);
    }

    public boolean areOutputFilesUpToDate() {
        if (grammarFile == null) {
            return false;
        }
        return areOutputFilesUpToDate(grammarFile);
    }

    public boolean areOutputFilesUpToDate(JFSCoordinates of) {
        return areOutputFilesUpToDate(of, jfsSupplier.get());
    }

    public boolean areOutputFilesUpToDate(UnixPath of) {
        return areOutputFilesUpToDate(of, jfsSupplier.get());
    }

    public boolean areOutputFilesUpToDate(UnixPath of, JFS in) {
        JFSFileModifications.FileChanges changes = outputFileModifications.changes(in);
        JFSCoordinates coords = JFSCoordinates.forPath(of, inputFiles);
        if (coords == null) {
            return false;
        }
        return areOutputFilesUpToDate(coords, in);
    }

    public boolean areOutputFilesUpToDate(JFSCoordinates coords, JFS in) {
        JFSFileObject fo = coords.resolve(in);
        if (fo == null) {
            return false;
        }
        long lastModified = fo.getLastModified();
        if (lastModified <= 0) {
            return false;
        }
        Set<JFSCoordinates> output = outputDependencies.get(coords);
        if (output == null) {
            return false;
        }
        for (JFSCoordinates c : output) {
            JFSFileObject outFile = c.resolve(in);
            if (outFile == null) {
                return false;
            }
            long lm = outFile.getLastModified();
            if (lm <= 0) {
                // deleted since we resolved it
                return false;
            }
            if (lm < lastModified) {
                return false;
            }
        }
        return true;
    }

    public boolean isUpToDate() {
        return isUpToDate(jfs);
    }

    public boolean isUpToDate(JFS jfs) {
        if (grammarFile != null) {
            return areOutputFilesUpToDate(grammarFile, jfs);
        }
        boolean result = inputFileModifications.changes().isUpToDate();
        if (result) {
            for (JFSCoordinates coords : outputFiles) {
                JFSFileObject fo = coords.resolve(jfs);
                if (fo == null) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    public boolean isReusable() {
        return isUsable() && exitCode() == 0 && isSuccess() && isUpToDate();
    }

    public AntlrGenerator toGenerator() {
        return new AntlrGenerator(toBuilder());
    }

    public AntlrGenerationResult rebuild() {
        return rebuild(false);
    }

    public AntlrGenerationResult rebuild(boolean force) {
        if (!force && isReusable()) {
            return this;
        }
        try (PrintStream printStream = AntlrLoggers.getDefault().printStream(originalFilePath, AntlrLoggers.STD_TASK_GENERATE_ANTLR)) {
            return toGenerator().run(grammarName, printStream, true);
        }
    }

    public FileChanges inputChanges() {
        return inputFileModifications == null ? FileChanges.forNoFiles() : inputFileModifications.changes();
    }

    public FileChanges outputChanges() {
        return outputFileModifications == null ? FileChanges.forNoFiles() : outputFileModifications.changes();
    }

    public AntlrGeneratorBuilder<AntlrGenerationResult> toBuilder() {
        AntlrGeneratorBuilder<AntlrGenerationResult> result = new AntlrGeneratorBuilder<>(jfsSupplier,
                new BuildConvert(sourceDir, importDir));
        result.forceAtn = options.contains(AntlrGenerationOption.FORCE_ATN);
        result.generateATNDot = options.contains(AntlrGenerationOption.GENERATE_ATN);
        result.genDependencies = options.contains(AntlrGenerationOption.GENERATE_DEPENDENCIES);
        result.generateAll = generateAll;
        result.genListener = options.contains(AntlrGenerationOption.GENERATE_LISTENER);
        result.genVisitor = options.contains(AntlrGenerationOption.GENERATE_VISITOR);
        result.jfs = jfsSupplier;
        result.importDir = importDir;
        result.sourcePath = sourceDir;
        result.grammarSourceInputLocation = grammarSourceLocation;
        result.javaSourceOutputLocation = javaSourceOutputLocation;
        result.log = options.contains(AntlrGenerationOption.LOG);
        result.longMessages = options.contains(AntlrGenerationOption.LONG_MESSAGES);
        result.packageName = packageName;
        result.originalFile = originalFilePath;
        result.tokensHash = tokensHash;
        result.pathHints = hints;
        result.interceptor = interceptor;
        return result;
    }

    private static final class BuildConvert implements Function<AntlrGeneratorBuilder<AntlrGenerationResult>, AntlrGenerationResult> {

        private final UnixPath importDir;
        private final UnixPath sourceDir;

        BuildConvert(UnixPath sourceDir, UnixPath importDir) {
            this.sourceDir = sourceDir;
            this.importDir = importDir;
        }

        public AntlrGenerationResult apply(AntlrGeneratorBuilder<AntlrGenerationResult> bldr) {
            return bldr.building(sourceDir, importDir);
        }
    }

    public UnixPath sourceDir() {
        return sourceDir;
    }

    public UnixPath importDir() {
        return importDir;
    }

    public Location grammarSourceLocation() {
        return grammarSourceLocation;
    }

    public Location javaSourceOutputLocation() {
        return javaSourceOutputLocation;
    }

    public String packageName() {
        return packageName;
    }

    public String grammarName() {
        return grammarName;
    }

    public boolean isSuccess() {
        return success;
    }

    public int exitCode() {
        return code;
    }

    public Grammar mainGrammar() {
        return mainGrammar;
    }

    public List<ParsedAntlrError> errors() {
        return errors;
    }

    @Override
    public boolean isUsable() {
        return success
                && inputFiles != null && outputFiles != null
                && !inputFiles.isEmpty() && !outputFiles.isEmpty();
    }

    @Override
    public UpToDateness currentStatus() {
        if (outputFiles == null || inputFiles == null || outputFiles.isEmpty() || inputFiles.isEmpty()) {
            return UpToDateness.UNKNOWN;
        }
        if (areOutputFilesUpToDate()) {
            return UpToDateness.CURRENT;
        }
        return UpToDateness.STALE;
    }

    public JFS jfs() {
        return jfsSupplier.get();
    }

    public Optional<Throwable> thrown() {
        return thrown == null ? Optional.empty() : Optional.of(thrown);
    }

    public void rethrow() throws Throwable {
        if (thrown != null) {
            throw thrown;
        }
    }

    public Optional<Grammar> findGrammar(String name) {
        if (mainGrammar != null && name.equals(mainGrammar.name)) {
            return Optional.of(mainGrammar);
        }
        for (Grammar g : allGrammars) {
            if (g == mainGrammar) {
                continue;
            }
            if (name.equals(g.name)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "AntlrRunResult{" + "success=" + success + ", code=" + code
                + ", thrown=" + thrown + ", grammarName=" + grammarName
                + ", errors=" + errors
                + ", inputFiles=" + inputFiles
                + ", outputFiles=" + outputFiles + '}';
    }

    private ObjectGraph<UnixPath> depGraph;

    public ObjectGraph<UnixPath> dependencyGraph() {
        if (depGraph != null) {
            return depGraph;
        }
        Set<UnixPath> all = new HashSet<>();
        for (Map.Entry<String, UnixPath> e : toPathMapSingle(primaryFileForGrammarName).entrySet()) {
            all.add(e.getValue());
        }
        for (Map.Entry<String, Set<UnixPath>> in : toPathMapNames(inputFilesForGrammarName).entrySet()) {
            all.addAll(in.getValue());
        }
        for (Map.Entry<UnixPath, Set<UnixPath>> dep : toPathMap(this.outputDependencies).entrySet()) {
            all.add(dep.getKey());
            all.addAll(dep.getValue());
        }
        Map<UnixPath, Set<UnixPath>> localDependencies = toPathMap(dependencies);
        for (Map.Entry<UnixPath, Set<UnixPath>> dep : localDependencies.entrySet()) {
            all.add(dep.getKey());
            all.addAll(dep.getValue());
        }
        List<UnixPath> sorted = new ArrayList<>(all);
        Collections.sort(sorted);
        BitSet[] references = new BitSet[sorted.size()];
        BitSet[] reverseReferences = new BitSet[sorted.size()];
        for (int i = 0; i < references.length; i++) {
            references[i] = new BitSet(references.length);
            reverseReferences[i] = new BitSet(references.length);
        }
        for (int i = 0; i < sorted.size(); i++) {
            UnixPath path = sorted.get(i);
            Set<UnixPath> direct = localDependencies.get(path);
            if (direct != null) {
                for (UnixPath dep : direct) {
                    int dix = sorted.indexOf(dep);
                    if (i == dix) {
                        continue;
                    }
                    references[i].set(dix);
                    reverseReferences[dix].set(i);
                }
            }
//            Set<UnixPath> out = outputDependencies.get(path);
//            if (out != null) {
//                for (UnixPath oneOutputFile : out) {
//                    int dix = sorted.indexOf(oneOutputFile);
//                    references[i].set(dix);
//                    reverseReferences[dix].set(i);
//                }
//            }
        }
        IntGraph ig = IntGraph.create(references, reverseReferences);
        ObjectGraph<UnixPath> og = ig.toObjectGraph(sorted);
        return depGraph = og;
    }

    public JFSCoordinates pathForGrammar(String grammarName) {
        for (Grammar g : allGrammars) {
            if (Objects.equals(g.name, grammarName)) {
                JFSCoordinates result = primaryFileForGrammarName.get(g.name);
                if (result == null) {
                    result = primaryFileForGrammarName.get(g.fileName);
                }
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public Set<JFSCoordinates> outputFilesForGrammar(String grammarName) {
        JFSCoordinates path = pathForGrammar(grammarName);
        if (path == null) {
            return Collections.emptySet();
        }
        Set<JFSCoordinates> out = outputDependencies.get(path);
        if (out == null) {
            out = Collections.emptySet();
        }
        return out;
    }

    private static Map<UnixPath, Set<UnixPath>> toPathMap(Map<JFSCoordinates, Set<JFSCoordinates>> m) {
        if (m.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UnixPath, Set<UnixPath>> result = new HashMap<>(m.size());
        for (Map.Entry<JFSCoordinates, Set<JFSCoordinates>> e : m.entrySet()) {
            UnixPath up = e.getKey().path();
            if (!e.getValue().isEmpty()) {
                Set<UnixPath> set = new HashSet<>(e.getValue().size());
                result.put(up, set);
                for (JFSCoordinates coord : e.getValue()) {
                    set.add(coord.path());
                }
            }
        }
        return result;
    }

    private static Map<String, UnixPath> toPathMapSingle(Map<String, JFSCoordinates> m) {
        if (m.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, UnixPath> result = new HashMap<>(m.size());
        for (Map.Entry<String, JFSCoordinates> e : m.entrySet()) {
            String up = e.getKey();
            result.put(up, e.getValue().path());
        }
        return result;
    }

    private static Map<String, Set<UnixPath>> toPathMapNames(Map<String, Set<JFSCoordinates>> m) {
        if (m.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<UnixPath>> result = new HashMap<>(m.size());
        for (Map.Entry<String, Set<JFSCoordinates>> e : m.entrySet()) {
            String up = e.getKey();
            if (!e.getValue().isEmpty()) {
                Set<UnixPath> set = new HashSet<>(e.getValue().size());
                result.put(up, set);
                for (JFSCoordinates coord : e.getValue()) {
                    set.add(coord.path());
                }
            }
        }
        return result;
    }
}
