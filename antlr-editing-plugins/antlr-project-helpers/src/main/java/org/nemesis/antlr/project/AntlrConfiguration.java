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
package org.nemesis.antlr.project;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.nemesis.antlr.project.impl.AntlrConfigurationFactory;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.nemesis.antlr.common.cachefile.CacheFileUtils;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Antlr configuration for a project, specifying folders and locations for
 * various things.
 *
 * @author Tim Boudreau
 */
public final class AntlrConfiguration {

    final Path antlrImportDir;
    final Path antlrSourceDir;
    final Path outputDir;
    final boolean listener;
    final boolean visitor;
    final boolean atn;
    final boolean forceATN;
    final String includePattern;
    final String excludePattern;
    final Charset encoding;
    final Path buildDir;
    final String createdByStrategy;
    final boolean isGuessedConfig;
    final Path buildOutput;
    final Path testOutput;
    final Path sources;
    final Path testSources;

    private static int MAGIC = 12903;

    AntlrConfiguration(Path antlrImportDir, Path antlrSourceDir, Path outDir, boolean listener, boolean visitor,
            boolean atn, boolean forceATN, String includePattern, String excludePattern, Charset encoding,
            Path buildDir, String createdByStrategy, boolean isGuessedConfig,
            Path buildOutput, Path testOutput, Path sources, Path testSources) {
        this.antlrImportDir = antlrImportDir;
        this.antlrSourceDir = antlrSourceDir;
        this.outputDir = outDir;
        this.listener = listener;
        this.visitor = visitor;
        this.atn = atn;
        this.forceATN = forceATN;
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
        this.encoding = encoding;
        this.buildDir = buildDir;
        this.createdByStrategy = createdByStrategy;
        this.isGuessedConfig = isGuessedConfig;
        this.buildOutput = buildOutput;
        this.testOutput = testOutput;
        this.sources = sources;
        this.testSources = testSources;
    }

    public boolean isPresent(Folders folder) {
        switch (folder) {
            case ANTLR_GRAMMAR_SOURCES:
                return antlrSourceDir != null;
            case ANTLR_IMPORTS:
                return antlrImportDir != null;
            case JAVA_GENERATED_SOURCES:
                return outputDir != null;
            case JAVA_SOURCES:
                return sources != null;
            case CLASS_OUTPUT:
                return buildOutput != null;
            case JAVA_TEST_SOURCES:
                return testSources != null;
            case TEST_CLASS_OUTPUT:
                return testOutput != null;
            default:
                return false;
        }
    }

    public boolean isImportDirChildOfSourceDir() {
        if (antlrSourceDir == null || antlrImportDir == null) {
            return false;
        }
        return antlrImportDir.startsWith(antlrSourceDir);
    }

    public static boolean isAntlrProject(Project project) {
        boolean result = FoldersHelperTrampoline.getDefault().isRecognized(project);
        if (result) {
            AntlrConfiguration config = forProject(project);
            result = config != null && !config.isGuessedConfig
                    && config.antlrSourceDir != null && Files.exists(config.antlrSourceDir);
        }
        return result;
    }

    public static Set<Path> potentialBuildFilePaths(Project project) {
        if (project == null) {
            return Collections.emptySet();
        }
        File f = FileUtil.toFile(project.getProjectDirectory());
        if (f == null) {
            return Collections.emptySet();
        }
        Path projectPath = f.toPath();
        Set<Path> result = new HashSet<>();
        for (Path p : FoldersHelperTrampoline.getDefault().buildFileRelativePaths()) {
            result.add(projectPath.resolve(p));
        }
        return result;
    }

    boolean evict() {
        // We don't actually know the project directory, so find it:
        Path path = null;
        for (Path p : new Path[]{this.antlrSourceDir, antlrImportDir, buildDir, sources, testSources}) {
            if (p != null) {
                path = p;
                break;
            }
        }
        if (path != null && Files.exists(path)) {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            if (fo != null) {
                Project project = FileOwnerQuery.getOwner(fo);
                if (project != null) {
                    File file = FileUtil.toFile(project.getProjectDirectory());
                    if (file != null) {
                        Path p = file.toPath();
                        FoldersLookupStrategy.pathEvicted(path);
                        return AntlrConfigurationCache.evict(path);
                    }
                }
            }
        }
        return false;
    }

    public Path buildOutput() {
        return buildOutput;
    }

    public Path testOutput() {
        return testOutput;
    }

    public Path javaSources() {
        return sources;
    }

    public Path testSources() {
        return testSources;
    }

    /**
     * If true, this is an approximated config generated by heuristic (and may
     * return null for some folder types).
     *
     * @return Whether or not the configuration is guessed
     */
    public boolean isGuessedConfig() {
        return isGuessedConfig;
    }

    /**
     * If true, some folders require by this configuration do not actually
     * exist, so this configuration should be seen as the default configuration
     * this project <i>would</i> have if it <i>were</i> actually set up as an
     * Antlr project.
     *
     * @return True if the configuration is not actually viable
     */
    public boolean isSpeculativeConfig() {
        Path p = antlrSourceDir();
        return p == null || !Files.exists(p);
    }

    /**
     * Name of the project-type plugin that created this config, for logging
     * purposes.
     *
     * @return A name
     */
    public String createdBy() {
        return createdByStrategy;
    }

    public static AntlrConfiguration forProject(Project project) {
        if (project == null) {
            return null;
        }
        AntlrConfiguration config = null;
        if (config == null) {
            File file = FileUtil.toFile(project.getProjectDirectory());
            if (file == null) { // virtual file
                return null;
            }
            config = AntlrConfigurationCache.instance().get(file.toPath(), () -> {
                return _forProject(project);
            });
        }
        return config;
    }

    private static AntlrConfiguration _forProject(Project project) {
        return FoldersLookupStrategy.get(project).antlrConfig();
    }

    public static AntlrConfiguration forFile(FileObject file) {
        Project project = FileOwnerQuery.getOwner(notNull("file", file));
        if (project != null) {
            File prjDir = FileUtil.toFile(project.getProjectDirectory());
            if (prjDir != null) {
                AntlrConfiguration cachedConfig = AntlrConfigurationCache.instance().getCached(prjDir.toPath());
                // If we have a cached configuration based on minimal information - just a project
                // or not even that, evict it since we have better information in this query
                if (cachedConfig != null && cachedConfig.isGuessedConfig()) {
                    Folders primary = Folders.primaryFolderFor(file);
                    if (primary.isSourceFolder() && !cachedConfig.isPresent(primary)) {
                        cachedConfig.evict();
                    }
                }
                return AntlrConfigurationCache.instance().get(prjDir.toPath(),
                        () -> {
                            FoldersLookupStrategy strat = FoldersLookupStrategy.get(project, prjDir.toPath());
                            if ("Heuristic".equals(strat.name())) {
                                // The heuristic config, if using InferredConfig, needs
                                // to be primed with a request for at least one Java or
                                // Antlr file to trigger scanning, so force one down its
                                // throat while we have one
                                Folders fld = Folders.primaryFolderFor(file);
                                if (fld != null) {
                                    File fl = FileUtil.toFile(file);
                                    if (fl != null) {
                                        strat.find(fld, fl.toPath());
                                    }
                                }
                            }
                            return FoldersLookupStrategy.get(project, prjDir.toPath()).antlrConfig();
                        });
            }
            return FoldersLookupStrategy.get(project, file).antlrConfig();
        } else {
            return FoldersLookupStrategy.get(file).antlrConfig();
        }
    }

    public static AntlrConfiguration forFile(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : forFile(fo);
    }

    public Path antlrImportDir() {
        return antlrImportDir;
    }

    public Path antlrSourceDir() {
        return antlrSourceDir;
    }

    public Path antlrSourceOutputDir() {
        return outputDir;
    }

    public Path buildDir() {
        return buildDir;
    }

    public Charset encoding() {
        return encoding;
    }

    public String includePattern() {
        return includePattern;
    }

    public String excludePattern() {
        return excludePattern;
    }

    public boolean listener() {
        return listener;
    }

    public boolean visitor() {
        return visitor;
    }

    public boolean atn() {
        return atn;
    }

    public boolean forceATN() {
        return forceATN;
    }

    /**
     * Get a function which can add Antlr support to the passed project, if a
     * module has registered an object which can modify the build file
     * appropriately, and if the project does not already have Antlr support.
     *
     * @param prj The project
     * @return A function or null if support cannot be added
     */
    public static Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrAdder(Project prj) {
        return FoldersHelperTrampoline.antlrAdder(prj);
    }

    public static AddAntlrCapabilities addAntlrCapabilities(Project context) {
        return FoldersHelperTrampoline.addAntlrCapabilities(context);
    }

    @Override
    public String toString() {
        return "AntlrPluginInfo{\n" + " importDir\t" + antlrImportDir
                + "\n sourceDir\t" + antlrSourceDir + "\n outputDir\t" + outputDir
                + "\n listener\t" + listener + "\n visitor\t" + visitor
                + "\n atn\t" + atn + "\n forceATN\t" + forceATN
                + "\n includePattern\t" + includePattern
                + "\n excludePattern\t" + excludePattern
                + "\n encoding\t" + encoding.name()
                + "\n buildDir\t" + buildDir
                + "\n createdBy\t" + createdByStrategy
                + "\n sources\t" + sources
                + "\n testSources\t" + testSources
                + "\n buildOutput\t" + buildOutput
                + "\n testOutput\t" + testOutput
                + "\n guessed\t" + isGuessedConfig
                + "\n}";
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.antlrImportDir);
        hash = 53 * hash + Objects.hashCode(this.antlrSourceDir);
        hash = 53 * hash + Objects.hashCode(this.outputDir);
        hash = 53 * hash + (this.listener ? 1 : 0);
        hash = 53 * hash + (this.visitor ? 1 : 0);
        hash = 53 * hash + (this.atn ? 1 : 0);
        hash = 53 * hash + (this.forceATN ? 1 : 0);
        hash = 53 * hash + Objects.hashCode(this.includePattern);
        hash = 53 * hash + Objects.hashCode(this.excludePattern);
        hash = 53 * hash + Objects.hashCode(this.encoding);
        hash = 53 * hash + Objects.hashCode(this.buildDir);
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
        final AntlrConfiguration other = (AntlrConfiguration) obj;
        if (this.listener != other.listener) {
            return false;
        }
        if (this.visitor != other.visitor) {
            return false;
        }
        if (this.atn != other.atn) {
            return false;
        }
        if (this.forceATN != other.forceATN) {
            return false;
        }
        if (!Objects.equals(this.includePattern, other.includePattern)) {
            return false;
        }
        if (!Objects.equals(this.excludePattern, other.excludePattern)) {
            return false;
        }
        if (!Objects.equals(this.antlrImportDir, other.antlrImportDir)) {
            return false;
        }
        if (!Objects.equals(this.antlrSourceDir, other.antlrSourceDir)) {
            return false;
        }
        if (!Objects.equals(this.outputDir, other.outputDir)) {
            return false;
        }
        if (!Objects.equals(this.encoding, other.encoding)) {
            return false;
        }
        return Objects.equals(this.buildDir, other.buildDir);
    }

    private static final class DefaultConfigFactory extends AntlrConfigurationFactory {

        @Override
        protected AntlrConfiguration create(Path importDir, Path sourceDir,
                Path outDir, boolean listener, boolean visitor, boolean atn,
                boolean forceATN, String includePattern, String excludePattern,
                Charset encoding, Path buildDir, String createdBy,
                boolean isGuessedConfig, Path buildOutput, Path testOutput, Path sources, Path testSources) {
            return new AntlrConfiguration(importDir, sourceDir, outDir, listener,
                    visitor, atn, forceATN, includePattern, excludePattern,
                    encoding, buildDir, createdBy, isGuessedConfig, buildOutput,
                    testOutput, sourceDir, testSources);
        }

        protected boolean evict(Path projectPath) {
            return AntlrConfigurationCache.evict(projectPath);
        }

        protected boolean evict(AntlrConfiguration config) {
            return config.evict();
        }
    }

    static {
        FoldersHelperTrampoline.antlrConfigFactory = new DefaultConfigFactory();
    }

    <C extends WritableByteChannel & SeekableByteChannel> int writeTo(C channel) throws IOException {
        boolean[] params = new boolean[]{listener, visitor, atn, forceATN, isGuessedConfig};
        return CacheFileUtils.create(MAGIC).write(channel, w -> {
            w.writeString(createdByStrategy)
                    .writeBooleanArray(params)
                    .writePath(antlrImportDir)
                    .writePath(antlrSourceDir)
                    .writePath(outputDir)
                    .writePath(buildDir)
                    .writePath(buildOutput)
                    .writePath(testOutput)
                    .writePath(sources)
                    .writePath(testSources)
                    .writeString(includePattern)
                    .writeString(excludePattern)
                    .writeString(encoding.name());
        });
    }

    static <C extends ReadableByteChannel & SeekableByteChannel> AntlrConfiguration readFrom(C channel) throws IOException {
        return CacheFileUtils.create(MAGIC).read(channel, r -> {
            String createdByStrategy = r.readString();
            if (!FoldersHelperTrampoline.isKnownImplementation(createdByStrategy)) {
                return null;
            }
            boolean[] params = r.readBooleans(5);
            boolean listener = params[0];
            boolean visitor = params[1];
            boolean atn = params[2];
            boolean forceATN = params[3];
            boolean isGuessedConfig = params[4];
            Path importDir = r.readPath();
            Path sourceDir = r.readPath();
            Path outputDir = r.readPath();
            Path buildDir = r.readPath();
            Path buildOutput = r.readPath();
            Path testOutput = r.readPath();
            Path sources = r.readPath();
            Path testSources = r.readPath();

            String includePattern = r.readString();
            String excludePattern = r.readString();
            String encodingName = r.readString();
            Charset encoding = Charset.forName(encodingName);
            AntlrConfiguration result = new AntlrConfiguration(importDir, sourceDir, outputDir,
                    listener, visitor, atn, forceATN, includePattern,
                    excludePattern, encoding, buildDir, createdByStrategy,
                    isGuessedConfig, buildOutput, testOutput, sources,
                    testSources);
            return result;
        });
    }
}
