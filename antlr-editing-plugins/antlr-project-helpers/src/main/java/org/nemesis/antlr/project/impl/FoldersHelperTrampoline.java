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

import com.mastfrog.util.strings.Escaper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.FoldersLookupStrategy;
import org.nemesis.antlr.project.spi.addantlr.AddAntlrCapabilities;
import org.nemesis.antlr.project.spi.AntlrConfigurationImplementation;
import org.netbeans.api.project.Project;
import org.openide.util.Lookup;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.nemesis.antlr.project.spi.FoldersLookupStrategyImplementationFactory;
import org.nemesis.antlr.project.spi.addantlr.NewAntlrConfigurationInfo;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public abstract class FoldersHelperTrampoline {

    public static FoldersHelperTrampoline DEFAULT;

    public static FoldersHelperTrampoline getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = FoldersLookupStrategy.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public static Supplier<FolderQuery> QUERY_SUPPLIER;

    static Supplier<FolderQuery> querySupplier() {
        if (QUERY_SUPPLIER != null) {
            return QUERY_SUPPLIER;
        }
        ensureFolderQueryInitializesQuerySupplier();
        assert QUERY_SUPPLIER != null : "Not initialized correctly";
        return QUERY_SUPPLIER;
    }

    public static Predicate<Iterable<?>> SINGLE_ITERABLE_TEST;
    public static Predicate<Iterable<?>> EMPTY_ITERABLE_TEST;
    public static Function<Object, Iterable<Object>> SINGLE_ITERABLE_FACTORY;
    public static Iterable<?> EMPTY_ITERABLE;
    public static AntlrConfigurationFactory antlrConfigFactory;

    static AntlrConfigurationFactory configFactory() {
        if (antlrConfigFactory != null) {
            return antlrConfigFactory;
        }
        Class<?> type = AntlrConfiguration.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert antlrConfigFactory != null : "Not initialized correctly";
        return antlrConfigFactory;
    }

    public String nameOf(FolderLookupStrategyImplementation spi) {
        return spi.name();
    }

    public AntlrConfiguration newAntlrConfiguration(Path importDir,
            Path sourceDir, Path outDir, boolean listener, boolean visitor,
            boolean atn, boolean forceATN, String includePattern,
            String excludePattern, Charset encoding,
            Path buildDir, String createdBy, boolean isGuessedConfig,
            Path buildOutput, Path testOutput, Path sources, Path testSources) {
        return configFactory().create(importDir, sourceDir, outDir,
                listener, visitor, atn, forceATN, includePattern,
                excludePattern, encoding, buildDir, createdBy,
                isGuessedConfig, buildOutput, testOutput, sources, testSources);
    }

    public boolean evictConfiguration(Path projectPath) {
        return configFactory().evict(projectPath);
    }

    public boolean evictConfiguration(AntlrConfiguration config) {
        return configFactory().evict(config);
    }

    public AntlrConfiguration antlrConfiguration(FolderLookupStrategyImplementation spi) {
        AntlrConfigurationImplementation config = spi.get(AntlrConfigurationImplementation.class);
        if (config != null) {
            return newAntlrConfiguration(config.antlrImportDir(), config.antlrSourceDir(),
                    config.antlrOutputDir(), config.listener(), config.visitor(),
                    config.atn(), config.forceATN(), config.includePattern(),
                    config.excludePattern(), config.encoding(), config.buildDir(),
                    spi.name(), config.isGuessedConfig(), config.buildOutput(),
                    config.testOutput(),
                    config.javaSources(), config.testSources());
        }
        return null;
    }

    public boolean isSingleIterable(Iterable<?> it) {
        return singleIterableTest().test(it);
    }

    public boolean isEmptyIterable(Iterable<?> it) {
        return emptyIterableTest().test(it);
    }

    @SuppressWarnings("unchecked")
    public <T> Iterable<T> newSingleIterable(T obj) {
        if (obj == null) {
            return newEmptyIterable();
        }
        return (Iterable<T>) singleIterableFactory().apply(obj);
    }

    @SuppressWarnings("unchecked")
    public <T> Iterable<T> newEmptyIterable() {
        return (Iterable<T>) emptyIterable();
    }

    static Iterable<?> emptyIterable() {
        if (EMPTY_ITERABLE != null) {
            return EMPTY_ITERABLE;
        }
        ensureFolderQueryInitializesQuerySupplier();
        assert EMPTY_ITERABLE != null : "Not initialized correctly";
        return EMPTY_ITERABLE;
    }

    static Function<Object, Iterable<Object>> singleIterableFactory() {
        if (SINGLE_ITERABLE_FACTORY != null) {
            return SINGLE_ITERABLE_FACTORY;
        }
        ensureFolderQueryInitializesQuerySupplier();
        assert SINGLE_ITERABLE_TEST != null : "Not initialized correctly";
        return SINGLE_ITERABLE_FACTORY;
    }

    private static void ensureFolderQueryInitializesQuerySupplier() {
        Class<?> type = FolderQuery.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }

    static Predicate<Iterable<?>> singleIterableTest() {
        if (SINGLE_ITERABLE_TEST != null) {
            return SINGLE_ITERABLE_TEST;
        }
        ensureFolderQueryInitializesQuerySupplier();
        assert SINGLE_ITERABLE_TEST != null : "Not initialized correctly";
        return SINGLE_ITERABLE_TEST;
    }

    static Predicate<Iterable<?>> emptyIterableTest() {
        if (EMPTY_ITERABLE_TEST != null) {
            return EMPTY_ITERABLE_TEST;
        }
        ensureFolderQueryInitializesQuerySupplier();
        assert EMPTY_ITERABLE_TEST != null : "Not initialized correctly";
        return EMPTY_ITERABLE_TEST;
    }

    public FolderQuery newQuery() {
        return querySupplier().get();
    }

    private static final Set<String> CHECKED = new HashSet<>();

    public static AddAntlrCapabilities addAntlrCapabilities(Project project) {
        for (FoldersLookupStrategyImplementationFactory factory : Lookup.getDefault().lookupAll(FoldersLookupStrategyImplementationFactory.class)) {
            try {
                Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> result = factory.antlrSupportAdder(project);
                if (result != null) {
                    return factory.addAntlrCapabilities();
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return null;
    }

    public static Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> antlrAdder(Project project) {
        for (FoldersLookupStrategyImplementationFactory factory : Lookup.getDefault().lookupAll(FoldersLookupStrategyImplementationFactory.class)) {
            try {
                Function<NewAntlrConfigurationInfo, CompletionStage<Boolean>> result = factory.antlrSupportAdder(project);
                if (result != null) {
                    return result;
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return null;
    }

    public static String findBestJavaPackageSuggestionForGrammarsWhenAddingAntlr(Project project) {
        FolderQuery fq = querySupplier().get().duplicate().project(project);
        Set<String> packageRoots = new TreeSet<>();
        Set<Path> relativePackagePaths = new HashSet<>(5);
        for (Path path : new HeuristicFoldersHelperImplementation(project, fq).find(Folders.JAVA_SOURCES, fq)) {
            if (Files.isDirectory(path)) {
                try {
                    System.out.println("WALK " + path);
                    FileVisitor<Path> scanner = new JavaSourceScanner(path, packageRoots, relativePackagePaths);
                    Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 15, scanner);
                } catch (IOException ex) {
                    Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.FINE, null, ex);
                }
            }
        }
        List<Path> packagePaths = new ArrayList<>(relativePackagePaths);
        Collections.sort(packagePaths, (a, b) -> {
            int result = Integer.compare(a.getNameCount(), b.getNameCount());
            if (result == 0) {
                result = -a.toString().compareTo(b.toString());
            }
            return result;
        });
        System.out.println("packageRoots " + packageRoots);
        System.out.println("packagePaths " + packagePaths);
        if (!packagePaths.isEmpty()) {
            return packagePaths.get(0).toString().replace('/', '.')
                    .replace('\\', '.');
        }

        String basePackage = packageRoots.isEmpty() ? "com" : packageRoots.iterator().next();

        ProjectInformation info = ProjectUtils.getInformation(project);
        return basePackage + '.' + Escaper.JAVA_IDENTIFIER_DELIMITED.escape(info.getDisplayName()).toLowerCase();
    }

    static final class JavaSourceScanner implements FileVisitor<Path> {

        private final Path root;
        private final Set<String> packageRoots;
        private final Set<Path> containingJavaFiles;
        private int encounteredAt = Integer.MAX_VALUE;
        private int currentDepth;

        public JavaSourceScanner(Path root, Set<String> packageRoots, Set<Path> containingJavaFiles) {
            this.root = root;
            this.packageRoots = packageRoots;
            this.containingJavaFiles = containingJavaFiles;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (root.equals(dir.getParent())) {
                packageRoots.add(dir.getFileName().toString());
            }
            currentDepth++;
            if (currentDepth > encounteredAt) {
                currentDepth--;
                return FileVisitResult.SKIP_SUBTREE;
            } else {
                return FileVisitResult.CONTINUE;
            }
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String nm = file.getFileName().toString();
            if (nm.endsWith(".java") || nm.endsWith(".class") || nm.endsWith(".groovy")) {
                Path rel = root.relativize(file.getParent());
                if (rel != null && rel.toString().length() > 0) {
                    containingJavaFiles.add(rel);
                    encounteredAt = Math.min(encounteredAt, currentDepth);
                    return FileVisitResult.SKIP_SIBLINGS;
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            currentDepth--;
            if (exc != null) {
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

    }

    public Set<Path> buildFileRelativePaths() {
        Set<Path> all = new HashSet<>(10);
        for (FoldersLookupStrategyImplementationFactory factory
                : Lookup.getDefault().lookupAll(FoldersLookupStrategyImplementationFactory.class)) {
            if (!CHECKED.contains(factory.getClass().getName())) {
                List<Path> items = new ArrayList<>(3);
                factory.buildFileNames(items);
                items.forEach((p) -> {
                    // This would be a security issue
                    String asString = p.toString();
                    if (asString.length() > 0 && asString.charAt(0) == '/') {
                        IOException ioe = new IOException(factory + " (" + factory + ") returns a "
                                + "non-relative build file path '" + p + "'. Fix the module.");
                        Logger.getLogger(FoldersHelperTrampoline.class.getName()).log(Level.WARNING,
                                "Invalid build file relative paths", ioe);
                        p = Paths.get(asString.substring(1));
                    }
                    all.add(p);
                });
                CHECKED.add(factory.getClass().getName());
            } else {
                factory.buildFileNames(all);
            }
        }
        return all;
    }

    public FolderLookupStrategyImplementation implementationFor(Project project, FolderQuery initialQuery) {
        if (project == null) {
            return NONE;
        }
        FolderLookupStrategyImplementation result = project.getLookup().lookup(FolderLookupStrategyImplementation.class);
        if (result == null) {
            for (FoldersLookupStrategyImplementationFactory factory : Lookup.getDefault().lookupAll(FoldersLookupStrategyImplementationFactory.class)) {
                FolderLookupStrategyImplementation impl = factory.create(project, initialQuery);
                if (impl != null) {
                    return impl;
                }
            }
        }
        if (result == null) {
            result = new HeuristicFoldersHelperImplementation(project, initialQuery);
        }
        return result;
    }

    public boolean isRecognized(Project project) {
        FolderLookupStrategyImplementation impl = implementationFor(project, newQuery());
        return impl != null && impl != NONE && !(impl instanceof HeuristicFoldersHelperImplementation);
    }

    public static boolean isKnownImplementation(String name) {
        Set<String> set = new HashSet<>(10);
        for (FoldersLookupStrategyImplementationFactory impl : Lookup.getDefault().lookupAll(FoldersLookupStrategyImplementationFactory.class)) {
            impl.collectImplementationNames(set);
        }
        return set.contains(name);
    }

    public abstract FoldersLookupStrategy forImplementation(FolderLookupStrategyImplementation impl);

    public Iterable<Path> find(FolderLookupStrategyImplementation spi, Folders folder, FolderQuery query) {
        try {
            return spi.find(folder, query);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return Collections.emptyList();
        }
    }

    static final FolderLookupStrategyImplementation NONE = new NoImplementation();

    public <T> T lookupObject(FolderLookupStrategyImplementation spi, Class<T> what) {
        return spi.get(what);
    }

    public Iterable<Path> allFiles(FolderLookupStrategyImplementation spi, Folders type) {
        return spi.allFiles(type);
    }

    static final class NoImplementation implements FolderLookupStrategyImplementation {

        @Override
        public Iterable<Path> find(Folders folder, FolderQuery query) {
            return Collections.emptyList();
        }

        @Override
        public String name() {
            return "None";
        }

        @Override
        public Iterable<Path> allFiles(Folders type) {
            return Collections.emptySet();
        }
    }
}
