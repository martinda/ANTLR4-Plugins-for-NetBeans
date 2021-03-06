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
package org.nemesis.antlr.project.spi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Implementation which can look up Antlr folders for a specific project.
 *
 * Contains a number of utility methods for filtering and creating 0- and 1-
 * element iterables which should be used.
 * <p>
 * There are two distinct use-cases for FoldersLookupStrategyImplementation:: 1.
 * As a way to look up folders based on the configuration of the project that
 * are well-defined; and 2., To generate a speculative set of folders that can
 * be used to create a workable JFS layout for Antlr files found randomly
 * sprinkled throughout a project (that is why the lookup methods return
 * iterables, while AntlrConfiguration returns single files - if you find Antlr
 * sources sprinkled into three different Java source packages, you want to
 * coalesce <i>all</i> of the detected antlr source roots (which may just be the
 * parent folders of those discovered files) in order to create a JFS that
 * contains all of these files in the same folder [handling name clashes!]).
 * <p>
 * A concrete, configuration-reading implementation should just
 * straightfrowardly read the config and return what it finds.  A speculative
 * configuration should be cached per-project, and incrementally update itself
 * as it learns more about the project by being queried for files within it,
 * possibly scanning some or all folders in the project to improve its
 * inferences.  Such implementations should call
 * <code>FoldersLookupStrategyImplementationFactory.evict()</code> with the
 * project folder when new information is discovered that would invalidate
 * previous assumptions, to ensure cached instances with less information are
 * not retained.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface FolderLookupStrategyImplementation {

    /**
     * Find an iterable of folders of the requested type. Use the convenience
     * methods on this interface to create iterables of zero or one object
     * efficiently.
     *
     * @param folder The folder type
     * @param query The query, which may contain hints about what folder to
     * choose, such as the file this query is relative to.
     * @return An iterable
     * @throws IOException If something goes wrong
     */
    Iterable<Path> find(Folders folder, FolderQuery query) throws IOException;

    /**
     * The name, for logging purposes, such as "Maven".
     *
     * @return A name
     */
    String name();

    /**
     * Look up implementation of optional interfaces, such as
     * AntlrConfigurationImplementation.
     *
     * @param <T> The type
     * @param type The type
     * @return An instance or null
     */
    default <T> T get(Class<T> type) {
        if (getClass().isInstance(type)) {
            return type.cast(this);
        }
        return null;
    }

    /**
     * Convert a file object to a path.
     *
     * @param fo A file object
     * @return A path
     */
    default Path toPath(FileObject fo) {
        if (fo == null) {
            return null;
        }
        File file = FileUtil.toFile(fo);
        if (file != null) {
            return file.toPath();
        }
        return null;
    }

    /**
     * Convert a path to a file object; returns null if the path does not exist
     * on disk.
     *
     * @param path A path
     * @return A file obejct or null
     */
    default FileObject toFileObject(Path path) {
        if (path == null) {
            return null;
        }
        return FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
    }

    /**
     * Find a file object of the specified name if it exists in the passed
     * directory, returning it as a path.
     *
     * @param dir The folder - if null is passed, null is returned
     * @param name The file name
     * @return A path
     */
    default Path findOne(FileObject dir, String name) {
        if (dir == null) {
            return null;
        }
        FileObject fo = dir.getFileObject(name);
        if (fo == null) {
            return null;
        }
        return toPath(fo);
    }

    /**
     * Return either an empty iterable, or if it exists, an iterable with one
     * element - the child fileobject of the passed directory matching the name,
     * as a path.
     *
     * @param dir A directory
     * @param name A complete file name
     * @return An iterable
     */
    default Iterable<Path> findOneIter(FileObject dir, String name) {
        if (dir == null) {
            return empty();
        }
        return iterable(findOne(dir, name));
    }

    /**
     * Returns an empty iterable.
     *
     * @param <T> A type
     * @return An iterable
     */
    default <T> Iterable<T> empty() {
        return SingleIterable.empty();
    }

    /**
     * Returns an optimized iterable containing a single object, or an empty
     * iterable if null is passed.
     *
     * @param <T> The type
     * @param obj An object
     * @return An iterable
     */
    default <T> Iterable<T> iterable(T obj) {
        return obj == null ? empty() : new SingleIterable(obj);
    }

    /**
     * Find an iterable of all direct child file objects of the passed directory
     * which match the passed predicate, as paths.
     *
     * @param dir The directory - if null, returns an empty iterable
     * @param pred A predicate
     * @return An iterable
     * @throws IOException
     */
    default Iterable<Path> scanFor(FileObject dir, Predicate<Path> pred) throws IOException {
        if (dir == null) {
            return empty();
        }
        List<Path> result = null;
        for (FileObject fo : dir.getChildren()) {
            Path p = toPath(fo);
            if (pred.test(p)) {
                if (result == null) {
                    result = new ArrayList<>(3);
                }
                result.add(p);
            }
        }
        return result == null ? SingleIterable.empty()
                : result.size() == 1 ? new SingleIterable(result.get(0))
                : result;
    }

    /**
     * Find an iterable of all direct child file objects of the passed directory
     * which match the passed predicate, as paths.
     *
     * @param dir The directory - if null, returns an empty iterable
     * @param pred A predicate
     * @return An iterable
     * @throws IOException
     */
    default Iterable<Path> scanFor(Path dir, Predicate<Path> pred) throws IOException {
        if (dir == null) {
            return empty();
        }
        List<Path> result = null;
        try (Stream<Path> all = Files.list(dir)) {
            Iterator<Path> it = all.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (pred.test(p)) {
                    if (result == null) {
                        result = new ArrayList<>(3);
                    }
                    result.add(p);
                }
            }
        }
        return result == null ? SingleIterable.empty()
                : result.size() == 1 ? new SingleIterable(result.get(0))
                : result;
    }

    /**
     * Create a predicate which can filter paths based on them matching an
     * Ant-style glob expression (e.g. <code>**&#47;com&47;*.g4</code>).
     *
     * @param globPattern A glob pattern
     * @param baseDir The base folder - paths that are tested will be
     * relativized against this
     * @return A predicate
     */
    default Predicate<Path> globFilter(String globPattern, Path baseDir) {
        return GlobFilter.create(baseDir, globPattern);
    }

    /**
     * Filter an iterable of paths based on them matching an Ant-style glob
     * expression (e.g. <code>**&#47;com&#47;*.g4</code>).
     *
     * @param globPattern A glob pattern
     * @param baseDir The base folder - paths that are tested will be
     * relativized against this
     * @return A predicate
     */
    default Iterable<Path> globFilter(Path baseDir, String globPattern, Iterable<Path> orig) {
        return filter(orig, globFilter(globPattern, baseDir));
    }

    /**
     * Filter an Iterable based on a predicate.
     *
     * @param <T> The type
     * @param orig The original filter
     * @param filter A predicate
     * @return An iterable
     */
    default <T> Iterable<T> filter(Iterable<T> orig, Predicate<T> filter) {
        return new FilterIterable<>(orig, filter);
    }

    default Iterable<Path> allFiles(Folders type, FileObject relativeTo) {
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
        try {
            for (Path p : find(type, new FolderQuery().relativeTo(toPath(relativeTo)))) {
                if (seen.contains(p)) {
                    continue;
                }
                try (Stream<Path> str = Files.walk(p)) {
                    str.filter(pth -> {
                        return !Files.isDirectory(pth);
                    }).filter(filter).forEach(all::add);
                } catch (NoSuchFileException ex) {
                    // ok
                } catch (IOException ex) {
                    Logger.getLogger(HeuristicFoldersHelperImplementation.class.getName())
                            .log(Level.INFO, "Failed walking " + p, ex);
                }
                seen.add(p);
            }
        } catch (IOException ex) {
            Logger.getLogger(HeuristicFoldersHelperImplementation.class.getName())
                    .log(Level.INFO, "Failed walking files for " + type, ex);
        }
        return all;
    }

    default Iterable<Path> allFiles(Folders type) {
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
        try {
            for (Path p : find(type, new FolderQuery())) {
                if (seen.contains(p)) {
                    continue;
                }
                try (Stream<Path> str = Files.walk(p)) {
                    str.filter(pth -> {
                        return !Files.isDirectory(pth);
                    }).filter(filter).forEach(all::add);
                } catch (NoSuchFileException ex) {
                    // ok
                } catch (IOException ex) {
                    Logger.getLogger(HeuristicFoldersHelperImplementation.class.getName())
                            .log(Level.INFO, "Failed walking " + p, ex);
                }
                seen.add(p);
            }
        } catch (IOException ex) {
            Logger.getLogger(HeuristicFoldersHelperImplementation.class.getName())
                    .log(Level.INFO, "Failed walking files for " + type, ex);
        }
        return all;
    }
}
