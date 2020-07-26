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
package org.nemesis.antlr.live;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.tools.StandardLocation;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.live.ParsingUtils.toPath;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.AntlrGeneratorBuilder;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.project.Folders;
import static org.nemesis.antlr.project.Folders.ANTLR_GRAMMAR_SOURCES;
import static org.nemesis.antlr.project.Folders.ANTLR_IMPORTS;
import org.nemesis.antlr.spi.language.AntlrParseResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.ParseResultHook;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.debug.api.Debug;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.misc.utils.CachingSupplier;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.util.WeakSet;

/**
 * Allows callers to pass a callback which will be triggered whenever a new
 * parser result for an Antlr file is being constructed, and process it (for
 * example, compiling it in-memory and running something against the compiled
 * classes in an isolating classloader, or adding hints, or whatever).
 *
 * @author Tim Boudreau
 */
public final class RebuildSubscriptions {

    private static final UnixPath IMPORTS = UnixPath.get("imports");
    private static final Logger LOG = Logger.getLogger(RebuildSubscriptions.class.getName());
    private final JFSMapping mapping = new JFSMapping();
    private static final RequestProcessor RP = new RequestProcessor("rebuild-antlr-subscriptions", 4, true);
    private final Map<Project, Generator> generatorForMapping
            = new WeakHashMap<>();
    private final BrokenSourceThrottle throttle = new BrokenSourceThrottle();

    public static boolean isThrottled(Path filePath, String tokensHash) {
        return instance().isThrottled(filePath, tokensHash);
    }

    public static boolean maybeThrottled(Path filePath, String tokensHash) {
        return instance().throttle.maybeThrottle(filePath, tokensHash);
    }


    static Supplier<RebuildSubscriptions> INSTANCE_SUPPLIER
            = CachingSupplier.of(RebuildSubscriptions::new);

    JFS jfsFor(Project project) { // for tests
        return mapping.getIfPresent(project);
    }

    public static int liveSubscriptions() {
        return INSTANCE_SUPPLIER.get().generatorForMapping.size();
    }

    static RebuildSubscriptions instance() {
        return INSTANCE_SUPPLIER.get();
    }

    public static String info() { // debug stuff, deleteme
        StringBuilder sb = new StringBuilder();
        instance().generatorForMapping.entrySet().forEach((e) -> {
            sb.append(e.getKey().getProjectDirectory().getName()).append('\n')
                    .append(e.getValue());
        });
        return sb.toString();
    }

    /**
     * Subscribe the passed subscriber to re-parses of the passed file object.
     * If this is the first call to subscribe for this file, a parse will be
     * triggered.
     *
     * @param fo A fileobject for an Antlr grammar file
     * @param sub A subscriber that will be invoked on reparse
     * @return A runnable which will unsubscribe the subscriber; the surrounding
     * plumbing that enables the subscription is strongly referenced <i>by</i>
     * the unsubscriber - allow that to be garbage collected and you may not get
     * notified of changes.
     */
    public static Runnable subscribe(FileObject fo, Subscriber sub) {
        return instance()._subscribe(fo, sub);
    }

    /**
     * Get the most recent last modified time for any grammar file in the
     * grammar source folders of the project that owns the passed file,
     * preferring the most recent (saved or not) <i>Document</i> edit times
     * where those are available, since we JFS-masquerade documents when
     * present, and listen and update a timestamp for them. If no subscribers
     * are registered for the project, returns the file timestamps.
     *
     * @param fo A file object
     * @return The most recent last modified time, or -1 if it cannot be
     * determined (grammar file deleted, etc.)
     * @throws IOException If something goes wrong
     */
    public static long mostRecentGrammarLastModifiedInProjectOf(FileObject fo) throws IOException {
        String mime = fo.getMIMEType();
        Project p = FileOwnerQuery.getOwner(fo);
        if (p == null) {
            return -1;
        }
        Generator g = instance().generatorForMapping.get(p);
        if (g == null) {
            return mostRecentGrammarLastModifiedInProjectTheHardWay(mime, fo, p);
        }
        return g.newestGrammarLastModified();
    }

    public static long mostRecentGrammarLastModifiedInProject(Project proj) throws IOException {
        Generator g = instance().generatorForMapping.get(proj);
        return g == null ? Long.MIN_VALUE : g.newestGrammarLastModified();
    }

    private static long mostRecentGrammarLastModifiedInProjectTheHardWay(String mime, FileObject fo, Project project) throws IOException {
        Iterable<FileObject> files = CollectionUtils.concatenate(
                Folders.ANTLR_GRAMMAR_SOURCES.findFileObject(project, fo),
                Folders.ANTLR_IMPORTS.findFileObject(project, fo));
        long result = -1;
        for (FileObject curr : files) {
            if ("text/x-g4".equals(curr.getMIMEType())) {
                result = Math.max(result, curr.lastModified().getTime());
            }
        }
        return result == -1 ? fo.lastModified().getTime() : result;
    }

    public static BrokenSourceThrottle throttle() {
        return instance().throttle;
    }

    public static boolean isThrottled(AntlrGenerationResult res) {
        if (!res.isUsable()) {
            return throttle().maybeThrottle(res.originalFilePath, res.tokensHash);
        }
        return false;
    }

    // XXX need general subscribe to mime type / all
    private Runnable _subscribe(FileObject fo, Subscriber sub) {
        LOG.log(Level.FINE, "Subscribe {0} to rebuilds of {1}", new Object[]{sub, fo});
        if (!"text/x-g4".equals(fo.getMIMEType())) {
            throw new IllegalArgumentException(fo.getNameExt() + " is not "
                    + "an antlr grammar file - its mime type is " + fo.getMIMEType());
        }
        File foFile = FileUtil.toFile(fo);
        if (foFile == null) {
            LOG.log(Level.WARNING, "Not a disk file, cannot subscribe to {0}", fo.getPath());
            return null;
        }
        Project project = FileOwnerQuery.getOwner(fo);
        if (project == null) {
            LOG.log(Level.WARNING, "Not owned by a project; cannot subscribe to {0}", fo.getPath());
            return null;
        }
        File file = FileUtil.toFile(project.getProjectDirectory());
        if (file == null) {
            LOG.log(Level.WARNING, "Project dir for {0} not a disk file"
                    + ", cannot subscribe to {1}", new Object[]{project, fo.getPath()});
            return null;
        }
        // XXX use JFS MAPPING to enable cleanup
        Generator generator = generatorForMapping.get(project);
        try {
            if (generator == null) {
                generator = new Generator(fo, project, mapping);
                LOG.log(Level.FINEST, "Create new generator for {0} in {1} for {2}",
                        new Object[]{fo.getPath(), project.getProjectDirectory().getPath(), sub});
                generatorForMapping.put(project, generator);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        generator.hook.subscribe(sub, fo);
        Generator finalGen = generator;
        return () -> {
            boolean allUnsubscribed = finalGen.hook.unsubscribe(sub);
            if (allUnsubscribed) {
                generatorForMapping.remove(project);
            }
        };
    }

    boolean kill(Project p, JFS jfs) {
        Generator gen = generatorForMapping.get(p);
        if (gen == null) {
            return true;
        }
        return gen.kill(jfs);
    }

    static volatile int ids;

    static class Generator extends FileChangeAdapter {

        private final Set<Mapping> mappings = new HashSet<>();
        private final Map<FileObject, Mapping> mappingForFile = new ConcurrentHashMap<>();
        private final RebuildHook hook;
        private final JFSMapping mapping;
        private final Charset encoding;
        private final FileObject initialFile;
        private final int id = ids++;
        private volatile boolean killed;

        boolean kill(JFS jfs) {
            killed = true;
            for (FileObject fo : mappingForFile.keySet()) {
                fo.removeFileChangeListener(this);
            }
            mappings.clear();
            mappingForFile.clear();
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Generator-")
                    .append(id)
                    .append("(").append(initialFile.getPath())
                    .append(' ').append(encoding).append(' ');
            for (Mapping m : mappings) {
                sb.append("\n  ").append(m);
            }
            return sb.append(')').toString();
        }

        private void addMapping(FileObject file, Mapping mapping) {
            mappings.add(notNull("mapping", mapping));
            mappingForFile.put(file, mapping);
        }

        @SuppressWarnings("LeakingThisInConstructor")
        Generator(FileObject subscribeTo, Project in, JFSMapping mapping) throws IOException {
            // Create the callback that will be invoked
            hook = new RebuildHook();
            this.mapping = mapping;
            this.initialFile = subscribeTo;
            encoding = FileEncodingQuery.getEncoding(subscribeTo);
            Set<FileObject> subscribed = new HashSet<>();
            LOG.log(Level.FINEST, "Create Generator {0} for {1}", new Object[]{id, subscribeTo.getPath()});
            // Find every file under the sources and imports dirs, and map them
            // into the JFS - masquerading documents if a document exists for
            // the file (it's open in the editor), otherwise masquerade the file,
            // and listen for document creation to replace the masquerade as
            // needed
            Folders.ANTLR_GRAMMAR_SOURCES.allFileObjects(in, subscribeTo)
                    .forEach(fo -> {
                        // Antlr imports may be underneath antlr sources,
                        // so don't map twice
                        Folders owner = Folders.ownerOf(fo);
                        if (owner == null) {
                            owner = Folders.ANTLR_GRAMMAR_SOURCES;
                        }
                        Mapping m = map(fo, owner);
                        if (m != null) {
                            addMapping(fo, m);
                            subscribed.add(fo);
                        } else {
                            LOG.log(Level.FINE, "No path to {0} for {1}",
                                    new Object[]{owner, fo.getNameExt(),
                                        Strings.lazyCharSequence(AntlrConfiguration.forFile(fo)::toString)});
                        }
                    });
            Folders.ANTLR_IMPORTS.allFileObjects(in, subscribeTo)
                    .forEach(fo -> {
                        if (!subscribed.contains(fo) && !mappingForFile.containsKey(fo)) {
                            Mapping imapping = map(fo, Folders.ANTLR_IMPORTS);
                            if (imapping != null) {
                                addMapping(fo, imapping);
                                subscribed.add(fo);
                            } else {
                                LOG.log(Level.FINE, "No mapping for {0}", fo.getPath());
                            }
                        }
                    });
            if (!subscribed.contains(subscribeTo)) {
                Folders owner = Folders.ownerOf(subscribeTo);
                // Corner case - there is nothing that makes sense as the file owner
                // This can sometimes happen in the case of Antlr sources mixed in with
                // Java sources of a random Ant-based project there is not specific support
                // for
                if (owner == null) {
                    LOG.log(Level.FINER, "Null owner for {0}", subscribeTo.getNameExt());
                    AntlrConfiguration config = AntlrConfiguration.forFile(subscribeTo);
                    Path pth = toPath(subscribeTo);
                    if (pth != null) {
                        if (config.javaSources() != null && pth.startsWith(config.javaSources())) {
                            owner = Folders.JAVA_SOURCES;
                        }
                        if (config.antlrImportDir() != null && pth.startsWith(config.antlrImportDir())) {
                            owner = Folders.ANTLR_IMPORTS;
                        }
                        if (config.antlrSourceDir() != null && pth.startsWith(config.antlrSourceDir())) {
                            owner = Folders.ANTLR_GRAMMAR_SOURCES;
                        }
                        if (config.testSources() != null && pth.startsWith(config.testSources())) {
                            owner = Folders.JAVA_TEST_SOURCES;
                        }
                    }
                }
                if (owner != null) {
                    Mapping newMapping = map(subscribeTo, owner);
                    if (newMapping != null) {
                        addMapping(subscribeTo, newMapping);
                        subscribed.add(subscribeTo);
                    } else {
                        LOG.log(Level.WARNING, "Could not create a mapping for "
                                + "{0} in {1} - either it resides in an "
                                + "unusual location relative to the project, "
                                + "or this is a bug.", new Object[]{subscribeTo,
                                    owner});
                    }
                }
            }
            // Listen on folders to ensure we detect new file creation
            // and set up mapping for newly created files
            Set<FileObject> listeningToDirs = new HashSet<>();
            for (Folders f : new Folders[]{ANTLR_GRAMMAR_SOURCES, ANTLR_IMPORTS}) {
                for (FileObject dir : f.findFileObject(subscribeTo)) {
                    if (dir != null && !listeningToDirs.contains(dir)) {
                        LOG.log(Level.FINEST, "Generator {0} recursive listen to {1}", new Object[]{id, dir.getPath()});
                        FileUtil.addRecursiveListener(this, FileUtil.toFile(dir));
                        listeningToDirs.add(dir);
                    } else {
                        LOG.log(Level.FINEST, "Already listening to {0}", dir.getNameExt());
                    }
                }
            }
            Folders owner = Folders.ownerOf(subscribeTo);
            if (owner != ANTLR_IMPORTS && owner != ANTLR_GRAMMAR_SOURCES && owner != null) {
                for (FileObject dir : owner.findFileObject(subscribeTo)) {
                    if (dir != null && !listeningToDirs.contains(dir)) {
                        FileUtil.addRecursiveListener(this, FileUtil.toFile(dir));
                        listeningToDirs.add(dir);
                    } else {
                        LOG.log(Level.FINEST, "Already listening to import {0}", dir.getNameExt());
                    }
                }
            }
        }

        public long newestGrammarLastModified(Project project) throws IOException {
            long result = Long.MIN_VALUE;
            JFS jfs = mapping.forProject(project);
            for (Mapping mapping : mappings) {
                if (mapping.isGrammarFile()) {
                    long val = mapping.lastModified(jfs);
                    if (val > 0) {
                        result = Math.max(result, val);
                    }
                }
            }
            return result;
        }

        public long newestGrammarLastModified() throws IOException {
            long result = Long.MIN_VALUE;
            JFS jfs = mapping.forProject(FileOwnerQuery.getOwner(initialFile));
            for (Mapping mapping : mappings) {
                if (mapping.isGrammarFile()) {
                    long val = mapping.lastModified(jfs);
                    if (val > 0) {
                        result = Math.max(result, val);
                    }
                }
            }
            return result;
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            if (killed) {
                return;
            }
            FileObject deleted = fe.getFile();
            new HashSet<>(mappings).stream().filter((m) -> (m.fo.equals(deleted))).forEach((m) -> {
                try {
                    JFS jfs = mapping.forProject(FileOwnerQuery.getOwner(initialFile));
                    JFSFileObject jfsFo = jfs.get(StandardLocation.SOURCE_PATH, m.targetPath);
                    if (jfsFo != null) {
                        jfsFo.delete();
                    }
                    mappings.remove(m);
                } catch (IOException ex) {
                    LOG.log(Level.INFO, null, ex);
                }
            });
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            if (killed) {
                return;
            }
            FileObject renamed = fe.getFile();
            new HashSet<>(mappings).stream().filter((m) -> (m.fo.equals(renamed))).forEach((m) -> {
                try {
                    JFS jfs = mapping.forProject(FileOwnerQuery.getOwner(initialFile));
                    JFSFileObject jfsFo = jfs.get(StandardLocation.SOURCE_PATH, m.targetPath);
                    if (jfsFo != null) {
                        jfsFo.delete();
                    }
                    Folders owner = Folders.ownerOf(renamed);
                    UnixPath newPath = pathFor(renamed, owner);
                    if (!newPath.equals(m.targetPath)) {
                        mappings.remove(m);
                        mappingForFile.remove(renamed);
                        Mapping nue = map(renamed, owner, newPath);
                        if (nue != null) {
                            addMapping(renamed, nue);
                        } else {
                            LOG.log(Level.FINER, "No mapping for {0}", newPath);
                        }
                    }
                } catch (IOException ex) {
                    LOG.log(Level.INFO, null, ex);
                }
            });
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            if (killed) {
                return;
            }
            FileObject fo = fe.getFile();
            if (fo.isData() && "text/x-g4".equals(fo.getMIMEType())) {
                Folders owner = Folders.ownerOf(fo);
                Path relativePath = Folders.ownerRelativePath(fo);
                if (owner != null && relativePath != null) {
                    addMapping(fo, map(fo, owner));
                }
            }
        }

        private UnixPath pathFor(FileObject fo, Folders owner) {
            Path relativePath = owner == Folders.ANTLR_IMPORTS
                    ? UnixPath.get("imports/" + fo.getNameExt()) : Folders.ownerRelativePath(fo);
            return relativePath == null ? null : UnixPath.get(relativePath);
        }

        Mapping map(FileObject fo, Folders owner) {
            UnixPath mappingPath = pathFor(fo, owner);
            if (mappingPath == null) {
                LOG.log(Level.INFO, "Got null owner mapping path for {0} with {1}",
                        new Object[]{owner, fo});
                // File was refactord, or lives in the wrong place,
                // such as the golden-files dir for tests
                return null;
            }
            return map(fo, owner, mappingPath);
        }

        Mapping map(FileObject fo, Folders owner, UnixPath mappingPath) {
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie.Observable obs = dob.getLookup().lookup(EditorCookie.Observable.class);
                return new Mapping(fo, obs, mappingPath);
            } catch (DataObjectNotFoundException ex) {
                LOG.log(Level.SEVERE, "No data object for " + fo.getPath(), ex);
                return new Mapping(fo, null, mappingPath);
            } finally {
                ParseResultHook.register(fo, hook);
            }
        }

        /**
         * Maintains the mapping from a single FileObject on disk, and a
         * masqueraded version of it inside the JFS for the project that owns
         * it; and takes care of switching betweenn mapping the FileObject and
         * mapping the Document when the file acquires an open document.
         */
        final class Mapping extends FileChangeAdapter implements PropertyChangeListener {

            private final UnixPath targetPath;
            private EditorCookie.Observable obs;
            private final FileObject fo;
            private MappingMode mode;
            private volatile boolean defunct;

            @SuppressWarnings("LeakingThisInConstructor")
            Mapping(FileObject fo, EditorCookie.Observable obs, UnixPath targetPath) {
                this.fo = fo;
                this.obs = obs;
                this.targetPath = targetPath;
                if (obs != null && obs.getDocument() != null) {
                    setMappingMode(MappingMode.MAP_DOCUMENT);
                } else {
                    setMappingMode(MappingMode.MAP_FILE);
                }
                LOG.log(Level.FINER, "Generator-{0} map {1} as {2}", new Object[]{id, fo.getPath(), mode});
                if (obs != null) {
                    obs.addPropertyChangeListener(WeakListeners.propertyChange(this, obs));
                } else {
                    LOG.log(Level.WARNING, "No EditorCookie.Observable for {0}", fo.getPath());
                }
                fo.addFileChangeListener(FileUtil.weakFileChangeListener(this, fo));
            }

            @Override
            public void fileRenamed(FileRenameEvent fe) {
                defunct = true;
                Generator.this.fileRenamed(fe);
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                defunct = true;
                Generator.this.fileDeleted(fe);
            }

            public boolean isGrammarFile() {
                return fo.isValid() && "text/x-g4".equals(fo.getMIMEType());
            }

            @Override
            public void fileChanged(FileEvent fe) {
                recheckMappingMode();
            }

            void recheckMappingMode() {
                File file = FileUtil.toFile(fo);
                if (file != null) {
                    // A change in another file could have made this file
                    // parsable, so reset throttles on it
                    instance().throttle.reset(file.toPath());
                }
                switch (mode) {
                    case MAP_FILE:
                        if (obs != null && obs.getDocument() != null) {
                            setMappingMode(MappingMode.MAP_DOCUMENT);
                        }
                }
            }

            public long lastModified(JFS jfs) {
                if (defunct) {
                    for (Mapping m : Generator.this.mappings) {
                        if (fo.equals(m.fo)) {
                            JFSFileObject jfo = jfs.get(StandardLocation.SOURCE_PATH, targetPath);
                            if (jfo == null) {
                                return fo.lastModified().getTime();
                            }
                        }
                    }
                    // just ensure a false result for the up to date check -
                    // we are no longer actually mapped
                    return System.currentTimeMillis();
                }
                // It seems we sometimes don't get notifications about the document
                // being opened - may be a race with adding the listener on startup
                recheckMappingMode();
                JFSFileObject jfo = jfs.get(StandardLocation.SOURCE_PATH, targetPath);
                if (jfo == null) {
                    return fo.lastModified().getTime();
                }
                return jfo.getLastModified();
            }

            @Override
            public String toString() {
                return fo.getPath() + " -> " + targetPath + " as " + mode;
            }

            synchronized void initMapping(JFS jfs) {
                switch (mode) {
                    case MAP_DOCUMENT: {
                        if (obs == null) {
                            try {
                                obs = DataObject.find(fo).getLookup().lookup(EditorCookie.Observable.class);
                                if (obs != null) {
                                    obs.addPropertyChangeListener(WeakListeners.propertyChange(this, obs));
                                }
                            } catch (DataObjectNotFoundException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        try {
                            if (obs != null) {
                                Document doc = obs.openDocument();
                                JFSFileObject nue = jfs.masquerade(doc, StandardLocation.SOURCE_PATH, targetPath);
                                break;
                            }
                        } catch (IOException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                    // fallthrough in case of exception
                    case MAP_FILE:
                        this.mode = MappingMode.MAP_FILE;
                        File file = FileUtil.toFile(fo);
                        if (file != null) {
                            jfs.masquerade(file.toPath(), StandardLocation.SOURCE_PATH, targetPath);
                        } else {
                            LOG.log(Level.WARNING, "Cannot map {0} into JFS - non-disk", fo);
                        }
                        break;
                    default:
                        throw new AssertionError(mode);
                }
            }

            synchronized void setMappingMode(MappingMode mode) {
                if (mode != this.mode) {
                    LOG.log(Level.FINER, "Mapping mode to {1} for {2} in Generator {0}", new Object[]{id,
                        mode,
                        fo.getName()});
                    this.mode = mode;
                    Project project = FileOwnerQuery.getOwner(fo);
                    if (project != null) {
                        LOG.log(Level.FINER, "Init JFS mappings for Generator {0} path {2} in {1}", new Object[]{id,
                            project.getProjectDirectory().getName(), targetPath});
                        JFS jfs = mapping.getIfPresent(project);
                        if (jfs != null) {
                            initMapping(jfs);
                        }
                    } else {
                        LOG.log(Level.FINER, "No project for Generator {0} path {1}", new Object[]{id, targetPath});
                    }
                }
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LOG.log(Level.FINEST, "Generator-{0} got cookie change {1} from {2}", new Object[]{id,
                    evt.getNewValue(), fo.getName()});
                if (EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
                    JTextComponent[] comp = (JTextComponent[]) evt.getNewValue();
                    if (comp == null || comp.length == 0) {
                        setMappingMode(MappingMode.MAP_FILE);
                    } else {
                        setMappingMode(MappingMode.MAP_DOCUMENT);
                    }
                } else if (EditorCookie.Observable.PROP_DOCUMENT.equals(evt.getPropertyName())) {
                    if (evt.getNewValue() != null) {
                        setMappingMode(MappingMode.MAP_DOCUMENT);
                    } else {
                        setMappingMode(MappingMode.MAP_FILE);
                    }
                }
            }
        }

        static enum MappingMode {
            MAP_FILE,
            MAP_DOCUMENT;
        }

        static {
            LOG.setLevel(Level.ALL);
        }

        class RebuildHook extends ParseResultHook<GrammarFileContext> {

            AntlrGenerator gen;
            private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

            public RebuildHook() {
                super(GrammarFileContext.class);
            }

            void subscribe(Subscriber subscribe, FileObject to) {
                subscribers.add(subscribe);
                LOG.log(Level.FINEST, "RebuildHook subscribe {0} to {1} count {2}",
                        new Object[]{subscribe, to.getPath(), subscribers.size()});
                // Trigger a parse immediately
                if (true || subscribers.size() == 1) {
                    LOG.log(Level.FINE, "Force parse of {0} for initial subscriber in "
                            + "Generator {1} for {2}",
                            new Object[]{to.getName(), id, subscribe});
                    Runnable doParse = () -> {
                        try {
                            ParsingUtils.parse(to, res -> {
                                if (res instanceof AntlrParseResult) {
                                    AntlrParseResult r = (AntlrParseResult) res;
                                    LOG.log(Level.FINER, "Parse of {0} completed",
                                            new Object[]{to.getName()});
                                    if (r.wasInvalidated()) {
                                        LOG.log(Level.FINEST, "Got old parser "
                                                + "result, passing to onReparse: {0}", r);
                                        Debug.message("Used-parse-result", r::toString);
                                        onReparse(null, r.getSnapshot().getMimeType(),
                                                r.extraction(), ParseResultContents.empty(), Fixes.empty());
                                    }
                                } else {
                                    LOG.log(Level.WARNING, "Not an AntlrParseResult: {0}", res);
                                }
                                return null;
                            });
                        } catch (Exception ex) {
                            LOG.log(Level.INFO, "Exception parsing " + to.getPath(), ex);
                        } catch (Error e) {
                            LOG.log(Level.SEVERE, "Error parsing " + to, e);
                            throw e;
                        }
                    };
                    // Try to keep this stuff off the event dispatch thread
                    if (EventQueue.isDispatchThread()) {
                        LOG.log(Level.FINER, "Is dispatch thread, enqueue "
                                + "reparse of {0} in background for {1}",
                                new Object[]{to.getName(), subscribe});
                        Debug.message("ON EQ - POSTPONE");
                        RP.post(doParse);
                    } else {
                        LOG.log(Level.FINER, "Run parse of {0} synchronously (not)", to.getName());
//                        doParse.run();
                        RP.post(doParse);
                    }
                } else {
                    LOG.log(Level.FINE, "Add subscriber {0} to {1} - not first "
                            + "subscriber, not forcing reparse", new Object[]{subscribe, to.getPath()});
                }
            }

            boolean unsubscribe(Subscriber subscribe) {
                LOG.log(Level.FINEST, "Unsubscribe {0} from {1} for {2}",
                        new Object[]{subscribe, id, initialFile.getPath()});
                subscribers.remove(subscribe);
                return subscribers.isEmpty();
            }

            private boolean mappingsInitialized;

            private void initMappings(JFS jfs) {
                mappingsInitialized = true;
                mappings.forEach((m) -> {
                    m.initMapping(jfs);
                });
            }

            private synchronized AntlrGenerator gen(Extraction ext) throws IOException {
                FileObject fo = ext.source().lookup(FileObject.class).get();
                Project project = FileOwnerQuery.getOwner(fo);
                JFS jfs = mapping.forProject(project);
                if (gen != null) {
                    if (jfs != gen.jfs()) {
                        gen = null;
                        LOG.log(Level.FINE, "Got a different JFS, reinit "
                                + "mappings for generator {0} for {1} in {2}",
                                new Object[]{id, ext.source(), project.getProjectDirectory()});
                        initMappings(jfs);
                    } else {
                        Optional<Path> pathOpt = ext.source().lookup(Path.class);
                        Path pth = pathOpt.isPresent() ? pathOpt.get()
                                : UnixPath.get(ext.source().id());
                        Path path = ext.source().lookupOrDefault(Path.class, () -> UnixPath.get(ext.source().id()));
                        return gen.withFileInfo(pth, ext.tokensHash());
                    }
                } else {
                    if (killed || !mappingsInitialized) {
                        initMappings(jfs);
                    }
                }

                Folders owner = Folders.ownerOf(fo);
                if (owner == null) {
                    owner = Folders.ANTLR_GRAMMAR_SOURCES;
                    LOG.log(Level.FINER, "No owner for {0} - defaulting to ANTLR_GRAMMAR_SOURCES", fo.getPath());
                }
                LOG.log(Level.FINER, "Create JFS for {0} owned by {1} in generator ", new Object[]{fo.getName(), owner, id});
                UnixPath relPath;
                switch (owner) {
                    case ANTLR_IMPORTS:
                        relPath = UnixPath.get("imports", fo.getNameExt());
                        LOG.log(Level.FINEST, "Using {0} for {1}", new Object[]{relPath, fo.getNameExt()});
                        break;
                    default:
                        Mapping mapping = mappingForFile.get(fo);
                        if (mapping != null) {
                            relPath = mapping.targetPath;
                        } else {
                            Path ownerRel = Folders.ownerRelativePath(fo);
                            if (ownerRel == null) {
                                LOG.log(Level.INFO, "NO REASONABLE OWNER FOR " + fo.getNameExt()
                                        + " - using its filename and assuming it is in the root of {0}", owner);
                                relPath = UnixPath.get(fo.getNameExt());
                            } else {
                                relPath = UnixPath.get(ownerRel);
                            }
                        }
                        break;
                }
                AntlrGeneratorBuilder<AntlrGenerator> agb = AntlrGenerator.builder(jfs);
                agb.withTokensHash(ext.tokensHash());
                Path originalFile = ext.source().lookupOrDefault(Path.class, () -> Paths.get(fo.getPath()));
                agb.withOriginalFile(originalFile);
                if (relPath.getParent() != null) {
                    String pkg = relPath.getParent().toString().replace('/', '.');
                    LOG.log(Level.FINEST, "Using package {0} for {1}",
                            new Object[]{pkg, fo.getName()});
                    agb.generateIntoJavaPackage(pkg);
                }
                UnixPath building = relPath.getParent() != null
                        ? relPath.getParent() : UnixPath.empty();
                return gen = agb
                        .building(building, IMPORTS);
            }

            private PrintStream outputFor(Extraction ext) {
                Path p = ext.source().lookupOrDefault(Path.class, () -> Paths.get(initialFile.getPath()));
                return AntlrLoggers.getDefault().forPath(p);
            }

            private String lastHash;
            private final Object lastLock = new Object();
            private Set<Subscriber> subscribersAtLastParse;

            @Override
            protected void onReparse(GrammarFileContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
                if (RebuildSubscriptions.instance().throttle.isThrottled(extraction)) {
                    LOG.log(Level.FINE, "Extraction {0} {1} throttled", new Object[]{extraction.source(), extraction.tokensHash()});
                    return;
                }
                Debug.runThrowing(this, "RebuildSubscriptions.onReparse-" + extraction.tokensHash() + "-" + extraction.source(), extraction::toString, () -> {
                    LOG.log(Level.FINER, "onReparse {0}", extraction.source());
                    List<Subscriber> targets = subscribers;
                    synchronized (lastLock) {
                        // avoid notifying for "casual" reparses where the
                        // content has not changed
                        // XXX this will block updates when an imported grammar
                        // changes
                        String hash = extraction.tokensHash();
                        Debug.message("tokens-hash: " + hash);
                        if (Objects.equals(lastHash, hash)) {
                            targets = new ArrayList<>(targets);
//                            targets.removeAll(subscribersAtLastParse);
                            if (targets.isEmpty()) {
                                Debug.failure("No remaining subscribers", this::toString);
                                return;
                            }
                        } else {
                            lastHash = hash;
                            subscribersAtLastParse = new WeakSet<>(subscribers);
                        }
                    }
                    AntlrGenerator gen = gen(extraction);
                    try {
                        // The parsing plumbing interrupts the parse thread in the event
                        // of cancellation (e.g. user typed a character).  This can wreak
                        // unholy havoc with I/O since it may cause a failure at any
                        // point that manifests as an IOException or some other failure,
                        // so try to detect the situation and bail out
                        if (Thread.interrupted()) {
                            LOG.log(Level.FINER, "Rebuild of {0} cancelled by interrupt",
                                    extraction.source());
                            Debug.failure("Interrupted", this::toString);
                            return;
                        }
                        runGeneration(extraction, mimeType, gen, tree, populate, fixes);
                    } catch (Error ex) {
                        // This will otherwise be swallowed
                        handleEiiE(ex, gen.jfs());
                        throw ex;
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Exception thrown running generator "
                                + "for " + extraction.source(), ex);
                    }
                });
            }

            public boolean runGeneration(Extraction extraction, String mimeType, AntlrGenerator generator, GrammarFileContext tree, ParseResultContents populate, Fixes fixes) throws Exception {
                PrintStream output = outputFor(extraction);
                // Build the message here so the UI doesn't hold a reference to the extraction
                // indefinitiely
                String msg = "Regenerate Antlr Grammar " + mimeType + " for " + extraction.source();
                AntlrGenerationResult result = Debug.runObjectThrowing(this, "generate " + extraction.source(), () -> {
                    return msg;
                }, () -> {
                    return mapping.whileWriteLocked(generator.jfs(), () -> {
                        try {
                            return generator.run(extraction.source().name(), output, true);
                        } catch (Error eiie) {
                            handleEiiE(eiie, generator.jfs());
                            throw eiie;
                        }
                    });
                });
                System.out.println("NEW FILES:\n");
                for (String fl : result.newlyGeneratedFiles) {
                    System.out.println(" * " + fl);
                }
                System.out.println("MODIFIED FILES:\n");
                for (String fl : result.modifiedFiles) {
                    System.out.println(" - " + fl);
                }

                if (!result.isSuccess()) {
                    System.out.println("NON SUCCESS " + result.errors);
                    System.out.println("INFO " + result.infoMessages);
                    System.out.println("" + result.thrown);
                    System.out.println("" + result.grammarName);
                }

                // FIXME - this shuold be included somehow in the extraction result -
                // EmbeddedAntlrParserImpl and perhaps others need to find it to
                // determine if something is throttled
                boolean throttled = instance().throttle.incrementThrottleIfBad(extraction, result);
                if (throttled) {
                    LOG.log(Level.FINEST, "Throttled {0}", extraction.source());
                    // XXX maybe let the parse result through only to new subsribers
                    // since the last run?
                    return false;
                }
                if (Debug.isActive()) {
                    if (result.isUsable()) {
                        Debug.success("Successful gen " + extraction.source(), result::toString);
                    } else {
                        Debug.failure("Failed gen " + extraction.source(), result::toString);
                    }
                }
                if (Thread.interrupted()) {
                    if (Debug.isActive()) {
                        Debug.failure("Thread interrupted, not passing to " + subscribers.size()
                                + " subscribers", result::toString);
                    }
                    LOG.log(Level.FINE, "Thread interrupted, not passing to {0} subscribers{1}",
                            new Object[]{subscribers.size(), result});
                    return true;
                }
                LOG.log(Level.FINER, "Reparse received by generator {0} for "
                        + "{1} placeholder {2} result usable {3} of "
                        + "{4} send to {5} subscribers",
                        new Object[]{id, extraction.source().name(),
                            extraction.isPlaceholder(), result.isUsable(),
                            result.grammarName, subscribers.size()});
                subscribers.forEach((s) -> {
                    try {
                        LOG.log(Level.FINEST, "Call onRebuild() on {0} for {1}",
                                new Object[]{s, extraction.source()});
                        s.onRebuilt(tree, mimeType, extraction, result, populate, fixes);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE,
                                "Exception processing parse result of "
                                + extraction.source(), e);
                    } catch (Error e) {
                        handleEiiE(e, generator.jfs());
                        throw e;
                    }
                });
                return false;
            }
        }
    }

    private static void handleEiiE(Error ex, JFS jfs) {
        // Sigh - RequestProcessor silently swallows Error throws
        try {
            LOG.log(Level.SEVERE, "Classpath: " + jfs.currentClasspath(), ex);
        } catch (Throwable t) {
            ex.addSuppressed(t);
            LOG.log(Level.SEVERE, t.toString(), ex);
            LOG.log(Level.SEVERE, null, t);
            throw ex;
        }
    }
}
