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
package org.nemesis.antlr.refactoring.usages;

import com.mastfrog.range.IntRange;
import com.mastfrog.util.cache.TimedCache;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.mutableSetOf;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.refactoring.AbstractRefactoringContext;
import org.nemesis.antlr.refactoring.AntlrRefactoringPluginFactory;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.extraction.attribution.ImportKeySupplier;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 * A very simple, but not terribly performant implementation of UsagesFinder
 * suitable for simple projects with few files that could possibly use the file
 * in question, by simply recursively scanning the root folders of all source
 * groups in the owning project, or if none are found, all files from the
 * project root, matching any that are of the same mime type as the file in
 * question, and then parsing those and using any ImportFinder registered to the
 * file type to find any imports and check if they are the same file as the one
 * in question.
 * <p>
 * Where possible, it is best to provide a complete alternate implementation
 * that uses the indexing API to look up references quickly, or if not that, at
 * a minimum, provide an implementation that narrows the set of files that need
 * to be scanned (usually based on the project type and some set of source
 * directories).
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class SimpleImportersFinder extends ImportersFinder {

    private static final String[] DEFAULT_SOURCE_GROUPS_TO_SCAN = new String[]{
        Sources.TYPE_GENERIC, "java", "resources", "modules", "antlr"};

    private final TimedCache<ProjectAndMimeType, Iterable<FileObject>, Exception> IMPORTERS_CACHE
            = TimedCache.createThrowing(60000, key -> {
                BooleanSupplier cancelled = currentCanceller.get();
                Iterable<FileObject> result = possibleImportersOf(cancelled, key.originator());
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                return result;
            }, ConcurrentHashMap::new);

    private void recursiveScan(Set<? super FileObject> addTo, String mimeType, FileObject file, Set<FileObject> seenDirs) {
        if (file.isFolder()) {
            if (seenDirs.contains(file)) {
                return;
            }
            seenDirs.add(file);
            for (FileObject child : file.getChildren()) {
                recursiveScan(addTo, mimeType, child, seenDirs);
            }
        } else if (mimeType.equals(file.getMIMEType())) {
            addTo.add(file);
        }
    }

    static final class ProjectAndMimeType {

        final Project project;
        final String mime;
        final Path projectPath;
        final Path originator;

        public ProjectAndMimeType(Project project, String mime, FileObject originator) {
            this.project = project;
            this.mime = mime;
            File file = FileUtil.toFile(project.getProjectDirectory());
            projectPath = file == null ? Paths.get("/") : file.toPath();
            this.originator = FileUtil.toFile(originator).toPath();
        }

        FileObject originator() {
            return FileUtil.toFileObject(FileUtil.normalizeFile(originator.toFile()));
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof ProjectAndMimeType)) {
                return false;
            }
            ProjectAndMimeType p = (ProjectAndMimeType) o;
            return projectPath.equals(p.projectPath) && mime.equals(p.mime);
        }

        @Override
        public int hashCode() {
            return mime.hashCode() + (71 * projectPath.hashCode());
        }

        @Override
        public String toString() {
            return projectPath + ";" + mime;
        }
    }

    /**
     * Find all file objects that could conceivably contain an import of the
     * passed file. Subclasses should override this method to quickly find only
     * those files that are interesting more quickly than recursively scanning
     * the project whenever possible.
     *
     * @param file A file that might be imported
     * @return An iterable of files that may or may not import the passed file
     */
    protected Iterable<FileObject> possibleImportersOf(BooleanSupplier cancelled, FileObject file) {
        try {
            Project prj = FileOwnerQuery.getOwner(file);
            if (prj != null) {
                return scanSourcesForProject(prj, file.getMIMEType());
            }
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Collections.emptySet();
    }

    private static final ThreadLocal<BooleanSupplier> currentCanceller = new ThreadLocal();

    private Iterable<FileObject> importersOfUsingCache(BooleanSupplier cancelled, FileObject file) {
        BooleanSupplier old = currentCanceller.get();
        currentCanceller.set(cancelled);
        try {
            Project prj = FileOwnerQuery.getOwner(file);
            if (prj != null) {
                try {
                    Iterable<FileObject> result = IMPORTERS_CACHE.get(new ProjectAndMimeType(prj, file.getMIMEType(), file));
                    if (result != null) { // cacnelled
                        return result;
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            return Collections.emptySet();
        } finally {
            currentCanceller.set(old);
        }
    }

    Set<FileObject> scanSourcesForProject(Project prj, String mime) {
        Sources sources = prj.getLookup().lookup(Sources.class);
        if (sources != null) {
            Set<FileObject> files = new HashSet<>();
            boolean scannedAny = false;
            Set<FileObject> seenDirs = new HashSet<>();
            for (String groupName : DEFAULT_SOURCE_GROUPS_TO_SCAN) {
                SourceGroup[] groups = sources.getSourceGroups(groupName);
                if (groups != null) {
                    for (SourceGroup grp : groups) {
                        FileObject root = grp.getRootFolder();
                        recursiveScan(files, mime, root, seenDirs);
                        scannedAny = true;
                    }
                }
            }
            if (!scannedAny) {
                recursiveScan(files, mime, prj.getProjectDirectory(), seenDirs);
            }
            return files;
        }
        return Collections.emptySet();
    }

    @Override
    public final Problem usagesOf(BooleanSupplier cancelled, FileObject file,
            NamedRegionKey<?> optionalImportKey,
            PetaFunction<IntRange<? extends IntRange<?>>, String, FileObject, ExtractionKey<?>, Extraction> usageConsumer) {
        Contextish ctxi = new Contextish();
        return ctxi.ctx(() -> {
            Problem result = null;
            ImportFinder imports = ImportFinder.forMimeType(file.getMIMEType());
            if (imports.isAlwaysEmpty()) {
                return result;
            }
            if (!(imports instanceof ImportKeySupplier)) {
                if (optionalImportKey == null) {
                    Logger.getLogger(AntlrRefactoringPluginFactory.class.getName()).log(Level.WARNING, "Import finder for {0} does not " + "implement {1}, so import names cannot be " + "tied to specific keys", new Object[]{file.getMIMEType(), ImportKeySupplier.class.getName()});
                } else {
                    Iterable<FileObject> all = importersOfUsingCache(cancelled, file);
                    if (cancelled.getAsBoolean()) {
                        return result;
                    }
                    Set<FileObject> seen = new HashSet<>();
                    seen.add(file);
                    for (FileObject fo : all) {
                        if (seen.contains(fo)) {
                            continue;
                        }
                        if (cancelled.getAsBoolean()) {
                            return result;
                        }
                        if (fo.getMIMEType().equals(file.getMIMEType())) {
                            try {
                                Extraction ext = parse(fo);
                                if (ext == null) {
                                    result = chainProblems(result, new Problem(false, "Null extraction for " + fo));
                                    continue;
                                }
                                NamedSemanticRegions<?> imported = ext.namedRegions(optionalImportKey);
                                if (!imported.isEmpty()) {
                                    for (GrammarSource<?> src : imports.allImports(ext, CollectionUtils.blackHoleSet())) {
                                        if (cancelled.getAsBoolean()) {
                                            return result;
                                        }
                                        Optional<FileObject> importedFile = src.lookup(FileObject.class);
                                        if (importedFile.isPresent() && file.equals(importedFile.get())) {
                                            String importName = src.name();
                                            NamedSemanticRegion<?> region = imported.regionFor(importName);
                                            if (region != null) {
                                                Problem p = usageConsumer.accept(region, region.name(), fo, optionalImportKey, ext);
                                                result = chainProblems(result, p);
                                                if (cancelled.getAsBoolean() || (p != null && p.isFatal())) {
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                Exceptions.printStackTrace(ex);
                                return chainProblems(result, new Problem(true, ex.getMessage()));
                            }
                        }
                    }
                }
            } else {
                ImportKeySupplier kimports = (ImportKeySupplier) imports;
                Set<NamedRegionKey<?>> keys = mutableSetOf(kimports.get());
                if (optionalImportKey != null) {
                    keys.add(optionalImportKey);
                }
                Iterable<FileObject> all = importersOfUsingCache(cancelled, file);
                Set<FileObject> seen = new HashSet<>();
                seen.add(file);
                for (FileObject fo : all) {
                    if (seen.contains(fo)) {
                        continue;
                    }
                    if (cancelled.getAsBoolean()) {
                        return result;
                    }
                    for (NamedRegionKey<?> key : keys) {
                        if (fo.getMIMEType().equals(file.getMIMEType())) {
                            try {
                                Extraction ext = parse(fo);
                                if (ext == null) {
                                    ext = parse(fo);
                                    if (ext == null) {
                                        result = chainProblems(result, new Problem(false, "Null extraction for " + fo));
                                        continue;
                                    }
                                }
                                NamedSemanticRegions<?> imported = ext.namedRegions(key);
                                if (!imported.isEmpty()) {
                                    for (GrammarSource<?> src : imports.allImports(ext, CollectionUtils.blackHoleSet())) {
                                        Optional<FileObject> importedFile = src.lookup(FileObject.class);
                                        if (importedFile.isPresent() && file.equals(importedFile.get())) {
                                            String importName = src.name();
                                            NamedSemanticRegion<?> region = imported.regionFor(importName);
                                            if (region != null) {
                                                usageConsumer.accept(region, region.name(), fo, key, ext);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                Exceptions.printStackTrace(ex);
                                String msg = ex.getMessage();
                                if (msg == null) {
                                    msg = ex.toString();
                                }
                                result = chainProblems(result, new Problem(true, msg));
                            }
                        }
                    }
                }
            }
            return result;
        });
    }

    static class Contextish extends AbstractRefactoringContext {

        <T> T ctx(Supplier<T> supp) {
            return AbstractRefactoringContext.inParsingContext(supp);
        }
    }
}
