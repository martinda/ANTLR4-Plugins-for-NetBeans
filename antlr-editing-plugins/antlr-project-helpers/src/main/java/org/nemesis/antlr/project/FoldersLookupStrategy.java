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

import com.mastfrog.util.path.UnixPath;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import org.nemesis.antlr.project.impl.FoldersHelperTrampoline;
import org.netbeans.api.project.Project;
import org.nemesis.antlr.project.spi.FolderLookupStrategyImplementation;
import org.nemesis.antlr.project.spi.FolderQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * XXX this class should not be API, but need to get it out of the tests for
 * the maven impl.
 *
 * @author Tim Boudreau
 */
public final class FoldersLookupStrategy {

    private final FolderLookupStrategyImplementation spi;
    private static final Map<Project, FoldersLookupStrategy> CACHE
            = Collections.synchronizedMap(new WeakHashMap<>());

    public static FoldersLookupStrategy get(Project project) {
        FoldersLookupStrategy result = CACHE.get(project);
        if (result == null) {
            FolderQuery q = FoldersHelperTrampoline.getDefault().newQuery()
                    .project(project);
            result = new FoldersLookupStrategy(FoldersHelperTrampoline.getDefault()
                    .implementationFor(project, q));
            CACHE.put(project, result);
        }
        return result;
    }

    static void pathEvicted(Path path) {
        synchronized (CACHE) {
            Project toRemove = null;
            for (Project p : CACHE.keySet()) {
                File f = FileUtil.toFile(p.getProjectDirectory());
                if (f != null) {
                    Path projectPath = f.toPath();
                    if (UnixPath.get(projectPath).equals(UnixPath.get(path))) {
                        toRemove = p;
                        break;
                    }
                }
            }
            if (toRemove != null) {
                CACHE.remove(toRemove);
            }
        }
    }

    public static FoldersLookupStrategy get(Project project, Path file) {
        return new FoldersLookupStrategy(FoldersHelperTrampoline.getDefault().implementationFor(project,
                FoldersHelperTrampoline.getDefault().newQuery().project(project).relativeTo(file)));
    }

    public static FoldersLookupStrategy get(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return get(fo);
    }

    public static FoldersLookupStrategy get(Project project, FileObject fo) {
        return new FoldersLookupStrategy(FoldersHelperTrampoline.getDefault().implementationFor(project,
                FoldersHelperTrampoline.getDefault().newQuery().project(project).relativeTo(FileUtil.toFile(fo).toPath())));
    }

    public static FoldersLookupStrategy get(FileObject fo) {
        Project project = null;
        if (fo != null) {
            project = FileOwnerQuery.getOwner(fo);
        }
        return new FoldersLookupStrategy(FoldersHelperTrampoline.getDefault().implementationFor(project,
                FoldersHelperTrampoline.getDefault().newQuery().project(project).relativeTo(FileUtil.toFile(fo).toPath())));
    }

    FoldersLookupStrategy(FolderLookupStrategyImplementation impl) {
        this.spi = impl;
    }

    public Iterable<Path> allFiles(Folders type) {
        return FoldersHelperTrampoline.getDefault().allFiles(spi, type);
    }

    public Iterable<Path> allFiles(Folders type, FileObject relativeTo) {
        return FoldersHelperTrampoline.getDefault().allFiles(spi, type, relativeTo);
    }

    public Iterable<FileObject> allFileObjects(Folders type) {
        return FileObjectIterable.create(allFiles(type));
    }

    public String name() {
        return FoldersHelperTrampoline.getDefault().nameOf(spi);
    }

    public Iterable<Path> find(Folders folder) {
        FolderQuery q = FoldersHelperTrampoline.getDefault().newQuery();
        return FoldersHelperTrampoline.getDefault().find(spi, folder, q);
    }

    public Iterable<Path> find(Folders folder, Path file) {
        FolderQuery q = FoldersHelperTrampoline.getDefault().newQuery().relativeTo(file);
        return FoldersHelperTrampoline.getDefault().find(spi, folder, q);
    }

    public Path findFirst(Folders folder, Path file) {
        Iterator<Path> it = find(folder, file).iterator();
        return it.hasNext() ? it.next() : null;
    }

    public Path findFirst(Folders folder) {
        Iterator<Path> it = find(folder).iterator();
        return it.hasNext() ? it.next() : null;
    }

    AntlrConfiguration antlrConfig() {
        return FoldersHelperTrampoline.getDefault().antlrConfiguration(spi);
    }

    <T> T get(Class<T> what) {
        return FoldersHelperTrampoline.getDefault().lookupObject(spi, what);
    }

    public String toString() {
        return getClass().getSimpleName() + '(' + spi + ')';
    }

    Iterable<FileObject> allFileObjects(Folders type, FileObject relativeTo) {
        if (relativeTo == null) {
            return allFileObjects(type);
        }
        return FileObjectIterable.create(allFiles(type, relativeTo));
    }

    static final class FoldersTrampolineImpl extends FoldersHelperTrampoline {

        public FoldersLookupStrategy forImplementation(FolderLookupStrategyImplementation impl) {
            return new FoldersLookupStrategy(impl);
        }
    }

    static {
        FoldersHelperTrampoline.DEFAULT = new FoldersTrampolineImpl();
    }
}
