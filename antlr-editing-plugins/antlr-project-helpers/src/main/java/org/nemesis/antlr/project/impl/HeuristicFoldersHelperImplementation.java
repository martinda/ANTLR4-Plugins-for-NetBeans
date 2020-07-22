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
package org.nemesis.antlr.project.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 * Attempts to find Maven folders based on heuristics about where such folders
 * frequently live. A real implementation is preferred.
 *
 * @author Tim Boudreau
 */
public final class HeuristicFoldersHelperImplementation implements FolderLookupStrategyImplementation {

    private final Project project;
    private FolderQuery initialQuery;

    HeuristicFoldersHelperImplementation(Project project, FolderQuery initialQuery) {
        this.project = project;
        this.initialQuery = initialQuery;
    }

    FolderQuery initialQuery() {
        return initialQuery;
    }

    public HeuristicFoldersHelperImplementation() {
        this.project = null;
    }

    @Override
    public <T> T get(Class<T> type) {
        if (AntlrConfigurationImplementation.class == type) {
            return type.cast(new HeuristicAntlrConfigurationImplementation(this));
        }
        return FolderLookupStrategyImplementation.super.get(type);
    }

    @Override
    public Iterable<Path> find(Folders folder, FolderQuery query) {
        if (probableMavenProject()) {
            Path result = maybeFindMavenFolders(folder);
            if (result != null) {
                return iterable(result);
            }
        }
        switch (folder) {
            case JAVA_SOURCES:
                return sourceDir(folder, query);
            case ANTLR_GRAMMAR_SOURCES:
                return scan(folder, query, ANTLR_DIR_CANDIDATES);
            case ANTLR_IMPORTS:
                return scan(folder, query, ANTLR_IMPORT_DIR_CANDIDATES);
            case CLASS_OUTPUT:
                return scan(folder, query, CLASS_OUTPUT_CANDIDATES);
            case JAVA_GENERATED_SOURCES:
                return scan(folder, query, GEN_SOURCES_CANDIDATES);
            case ANTLR_TEST_GRAMMAR_SOURCES:
                return scan(folder, query, ANTLR_TEST_DIR_CANDIDATES);
            case ANTLR_TEST_IMPORTS:
                return scan(folder, query, ANTLR_TEST_IMPORT_DIR_CANDIDATES);
            case JAVA_TEST_SOURCES:
                return scan(folder, query, TEST_SOURCE_DIR_CANDIDATES);
            case RESOURCES:
                return scan(folder, query, RESOURCES_CANDIDATES);
            case TEST_RESOURCES:
                return scan(folder, query, TEST_RESOURCES_CANDIDATES);
            case JAVA_TEST_GENERATED_SOURCES:
        }
        return empty();
    }

    private Path maybeFindMavenFolders(Folders folder) {
        String relPath = null;
        switch (folder) {
            case ANTLR_GRAMMAR_SOURCES:
            case ANTLR_IMPORTS:
                relPath = "src/main/antlr4";
                break;
            case CLASS_OUTPUT:
                relPath = "src/main/java";
                break;
            case RESOURCES:
                relPath = "src/main/resources";
                break;
            case TEST_RESOURCES:
                relPath = "src/test/resources";
                break;
            default:
                return null;
        }
        FileObject prjDir = project.getProjectDirectory();
        if (prjDir.getFileObject(relPath) != null) {
            Path base = FileUtil.toFile(prjDir).toPath();
            switch (folder) {
                case ANTLR_GRAMMAR_SOURCES:
                    return base.resolve("src/main/antlr4");
                case ANTLR_IMPORTS:
                    return base.resolve("src/main/antlr4/imports");
                case CLASS_OUTPUT:
                    return base.resolve("target/classes");
            }
        }
        return null;
    }

    private boolean probableMavenProject() {
        return project != null && project.getProjectDirectory().getFileObject("pom.xml") != null;
    }

    @Override
    public String name() {
        return HEURISTIC;
    }
    static final String HEURISTIC = "Heuristic";

    @Override
    public String toString() {
        return name();
    }

    private static final String[] GEN_SOURCES_CANDIDATES = new String[]{
        "target/generated-sources/antlr4", "target/generated-sources/antlr3",
        "build/generated-sources/antlr4", "build/generated-sources/antlr3",
        "build/generated-sources/antlr", "target/generated-sources/antlr",
        "target/generated-sources",
        "generated-sources", "generated"
    };

    private static final String[] CLASS_OUTPUT_CANDIDATES = new String[]{
        "target/classes", "build/classes", "classes", "build"
    };

    private static final String[] SOURCE_DIR_CANDIDATES = new String[]{
        "src/main/java",
        "source/main/java", "src/java", "source/java",
        "java/source", "java/src", "src", "java"
    };
    private static final String[] TEST_SOURCE_DIR_CANDIDATES = new String[]{
        "src/test/unit/java", "source/test/unit/java",
        "src/test/java", "src/test/unit", "source/test/unit",
        "source/test/java", "test/java/source", "test/java/src", "test/java/source",
        "src/test", "source/test",
        "test/source", "test/src", "src", "test", "tests"
    };
    private static final String[] TEST_CLASS_OUT_CANDIDATES = new String[]{
        "build/test/unit/classes",
        "build/test/classes",
        "target/test-classes"
    };
    private static final String[] TEST_RESOURCES_CANDIDATES = new String[]{
        "test/resources",
        "src/test/resources",
        "src/test-resources",
        "test-resources"
    };
    private static final String[] RESOURCES_CANDIDATES = new String[]{
        "src/main/resources",
        "src/resources",
        "resources"
    };
    private static final String[] ANTLR_DIR_CANDIDATES = new String[]{
        "src/main/antlr4", "src/main/antlr3", "src/main/antlr",
        "source/main/antlr4", "src/antlr4", "src/antlr3", "src/antlr",
        "source/antlr4", "source/antlr3", "source/antlr",
        "antlr", "antlr4", "antlrsrc", "grammar"
    };
    private static final String[] ANTLR_TEST_DIR_CANDIDATES = new String[]{
        "src/test/antlr4", "src/test/antlr3", "src/test/antlr",
        "source/test/antlr4", "test/antlr4", "test/antlr3", "test/antlr",
        "test/antlr3", "test/antlr"
    };
    private static final String[] ANTLR_TEST_IMPORT_DIR_CANDIDATES = new String[]{
        "src/test/antlr4/imports", "src/test/antlr3/imports", "src/test/antlr/imports",
        "source/test/antlr4/imports", "test/antlr4/imports", "test/antlr3/imports", "test/antlr/imports",
        "test/antlr3/imports", "test/antlr/imports", "grammar/imports"
    };

    static final String[] ANTLR_IMPORT_DIR_CANDIDATES;

    static {
        ANTLR_IMPORT_DIR_CANDIDATES = new String[(ANTLR_DIR_CANDIDATES.length * 2) + 2];
        System.arraycopy(ANTLR_DIR_CANDIDATES, 0, ANTLR_IMPORT_DIR_CANDIDATES, 0, ANTLR_DIR_CANDIDATES.length);
        System.arraycopy(ANTLR_DIR_CANDIDATES, 0, ANTLR_IMPORT_DIR_CANDIDATES, ANTLR_DIR_CANDIDATES.length, ANTLR_DIR_CANDIDATES.length);
        for (int i = 0; i < ANTLR_IMPORT_DIR_CANDIDATES.length - 2; i++) {
            ANTLR_IMPORT_DIR_CANDIDATES[i] += i < ANTLR_DIR_CANDIDATES.length ? "/imports" : "/import";
        }
        ANTLR_IMPORT_DIR_CANDIDATES[ANTLR_IMPORT_DIR_CANDIDATES.length - 2] = "imports";
        ANTLR_IMPORT_DIR_CANDIDATES[ANTLR_IMPORT_DIR_CANDIDATES.length - 1] = "import";
        Arrays.sort(ANTLR_IMPORT_DIR_CANDIDATES, (a, b) -> {
            return -Integer.compare(a.length(), b.length());
        });
    }

    public Iterable<Path> sourceDir(Folders folder, FolderQuery query) {
        return scan(folder, query, SOURCE_DIR_CANDIDATES);
    }

    private Iterable<Path> scan(Folders folder, FolderQuery query, String[] candidates) {
        if (project == null) {
            return empty();
        }
        FileObject dir = project.getProjectDirectory();
        Path path = toPath(findFirst(dir, candidates));
        if (path != null) {
            switch (folder) {
                case JAVA_GENERATED_SOURCES:
                    boolean useSubfolders = false;
                    Path annos = path.resolve("annotations");
                    if (Files.exists(annos)) {
                        useSubfolders = true;
                    } else {
                        if (Files.exists(path.resolve("antlr4"))) {
                            useSubfolders = true;
                        }
                    }
                    if (useSubfolders) {
                        List<Path> all = new ArrayList<>();
                        try (Stream<Path> str = Files.list(path)) {
                            str.filter(pth -> Files.isDirectory(pth)).forEach(all::add);
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        return all;
                    }
            }
        }
        // Last resort:  guess the parent of the queried g4 file *is* the source dir
        if (query.relativeTo() != null && query.relativeTo().toString().endsWith(".g4")
                && folder == Folders.ANTLR_GRAMMAR_SOURCES) {

            if (query.project() == null) {
                FileObject fo = toFileObject(query.relativeTo());
                if (fo != null) {
                    Project proj = FileOwnerQuery.getOwner(fo);
                    if (proj != null && proj != FileOwnerQuery.UNOWNED) {
                        query = query.duplicate().project(proj);
                    }
                }
            }
            if (query.project() != null) {
                InferredConfig inferred = InferredConfig.get(project, folder, query);
                if (inferred != null) {
                    Iterable<Path> result = inferred.query(folder, query);
                    if (result.iterator().hasNext()) {
                        return result;
                    }
                }
            }
            return iterable(query.relativeTo().getParent());
        }

        return iterable(path);
    }

    private static FileObject findFirst(FileObject dir, String... subdirs) {
        for (String sub : subdirs) {
            FileObject found = dir.getFileObject(sub);
            if (found != null && found.isFolder()) {
                return found;
            }
        }
        return null;
    }

    @Override
    public Iterable<Path> allFiles(Folders type) {
        List<Path> all = new ArrayList<>();
        Set<Path> seen = new HashSet<>(3);
        Set<String> exts = type.defaultTargetFileExtensions();
        Predicate<Path> filter = p -> {
            for (String ext : exts) {
                boolean result = p.getFileName().toString().endsWith("." + ext);
                if (result) {
                    return result;
                }
            }
            return false;
        };
        for (Path p : find(type, initialQuery)) {
            if (seen.contains(p)) {
                continue;
            }
            try (Stream<Path> str = Files.walk(p)) {
                str.filter(pth -> {
                    return !Files.isDirectory(pth);
                }).filter(filter).forEach(all::add);
            } catch (IOException ex) {
                Logger.getLogger(HeuristicFoldersHelperImplementation.class.getName())
                        .log(Level.INFO, "Failed walking " + p, ex);
            }
            seen.add(p);
        }
        return all;
    }

    @ServiceProvider(service = HeuristicImplementationFactory.class, position = Integer.MAX_VALUE)
    public static final class HeuristicImplementationFactory implements FoldersLookupStrategyImplementationFactory {

        private static final List<Path> DEFAULT_BUILD_FILES = Arrays.asList(new Path[]{
            Paths.get("pom.xml"),
            Paths.get("build.xml"),
            Paths.get("build.gradle"),
            Paths.get("Makefile")});

        private static final FolderLookupStrategyImplementation INSTANCE = new HeuristicFoldersHelperImplementation();

        @Override
        public FolderLookupStrategyImplementation create(Project project, FolderQuery initialQuery) {
            if (project == null) {
                return INSTANCE;
            }
            return new HeuristicFoldersHelperImplementation(project, initialQuery);
        }

        @Override
        public void buildFileNames(Collection<? super Path> buildFileNameGatherer) {
            buildFileNameGatherer.addAll(DEFAULT_BUILD_FILES);
        }

        @Override
        public void collectImplementationNames(Set<? super String> into) {
            into.add(HEURISTIC);
        }
    }
}
