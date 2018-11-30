package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import org.nemesis.antlr.v4.netbeans.v8.util.MergeIterables;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileUtil;

/**
 * In-memory implementation of JavaFileManager, which creates virtual files
 * entirely stored in-memory. By default, stores content in byte arrays on the
 * Java heap. An alternative, experimental implementation of backing storage
 * using NIO direct buffers will be used if the system property 'jfs.off.heap'
 * is set to true.
 * <p>
 * This does all of the normal things StandardJavaFileManager does, and can be
 * used against Javac for cases where complication output should be written
 * entirely to memory (i.e. you want to compile something and then run it in an
 * isolating classlaoder).
 * </p>
 * <p>
 * Additionally, the 'masquerade' methods allow you to insert a file on disk or
 * an editable Document into the filesystem, and have it be read from as if it
 * were a file - so a JFS can be kept open over "live" files that are
 * always-up-to-date representations of data that is actually on disk or open in
 * an editor. Masqueraded documents are writable. Masqueraded files are not, as
 * blurring the distinction between virtual and non-virtual would make it easy
 * to have data-clobbering bugs that are catastrophic for the user.
 * </p>
 * <p>
 * The classpath is taken care of by delegating to StandardJavaFileManager for
 * the CLASS_PATH and PLATFORM_CLASS_PATH locations - to add JARs there, use the
 * setClasspath() methods, not the file-adding methods (unlike
 * StandardJavaFileManager, copying or masquerading a JAR does not cause its
 * <i>contents</i> to appear to be part of the file system).
 * </p>
 * <p>
 * A note on character sets: The best performing character set to use will
 * frequently be UTF-16, the native storage format for in-memory strings in the
 * JVM. However, this doubles the memory requirements for ASCII text, which Java
 * code frequently is. The right choice is going to depend on the size of the
 * source files being compiled, and the frequency with which they will be
 * rewritten, since that generated memory pressure.
 * </p>
 * <p>
 * Calling close() on a JFS will free all cached bytes <i>unless</i> an unclosed
 * classloader created over its storage exists, in which case resources will not
 * be freed until that classloader is closed (jfs URLs created by it need to
 * remain resolvable, and closing a JFS de-registers it from its URL stream
 * handler factory).
 * </p>
 *
 * @author Tim Boudreau
 */
public final class JFS implements JavaFileManager {

    private final Map<Location, JFSStorage> storageForLocation = new HashMap<>();
    private final String fsid;
    private final JFSStorageAllocator<?> allocator;
    private final BiConsumer<Location, FileObject> listener;
    private final StandardJavaFileManager delegate;
    static final Logger LOG = Logger.getLogger(JFS.class.getName());

    public JFS() {
        this(JFSStorageAllocator.defaultAllocator(), null);
    }

    public JFS(Charset charset) {
        this(JFSStorageAllocator.defaultAllocator().withEncoding(charset), null);
    }

    /**
     * Create a JFS, passing in a listener which will be notified of new file
     * creation.
     *
     * @param charset The character set to use for files
     * @param listener A listener
     */
    public JFS(Charset charset, BiConsumer<Location, FileObject> listener) {
        this(JFSStorageAllocator.defaultAllocator().withEncoding(charset), listener);
    }

    public JFS(BiConsumer<Location, FileObject> listener) {
        this(JFSStorageAllocator.defaultAllocator(), listener);
    }

    public static JFS offHeap(Charset charset) {
        return new JFS(NioBytesStorageAllocator.allocator().withEncoding(charset), null);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private JFS(JFSStorageAllocator<?> allocator, BiConsumer<Location, FileObject> listener) {
        this.fsid = Integer.toString(System.identityHashCode(this), 36)
                + "."
                + Long.toString(System.currentTimeMillis(), 36).toLowerCase();
        this.allocator = allocator;
        this.listener = listener;
        this.delegate = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null,
                Locale.getDefault(), allocator.encoding());
        JFSUrlStreamHandlerFactory.register(this);
        LOG.log(Level.FINE, "Created JFS {0}", fsid);
    }

    public Charset encoding() {
        return allocator.encoding();
    }

    /**
     * Add JARs or folders to the compile classpath. Note: this delegates to the
     * StandardJavaFileManager and does not copy JARs or their contents into
     * memory.
     *
     * @param jars
     * @throws IOException
     */
    public void setClasspath(Path... jars) throws IOException {
        List<File> files = new ArrayList<>(jars.length);
        for (Path p : jars) {
            files.add(p.toFile());
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    public void setClasspath(URL... jars) throws IOException, URISyntaxException {
        List<File> files = new ArrayList<>(jars.length);
        for (URL up : jars) {
            files.add(new File(up.toURI()));
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    public void setClasspath(Collection<Path> jars) throws IOException {
        List<File> files = new ArrayList<>(jars.size());
        for (Path p : jars) {
            files.add(p.toFile());
        }
        delegate.setLocation(StandardLocation.CLASS_PATH, files);
    }

    JFSStorageAllocator<?> alloc() {
        return allocator;
    }

    public String id() {
        return fsid;
    }

    boolean is(String id) {
        return id().equals(id);
    }

    private JFSStorage forLocation(Location loc, boolean create) {
        JFSStorage result = storageForLocation.get(loc);
        if (result == null && create) {
            // Ensure we don't have a cached merged storage that does
            // not contain all locations but could be used to resolve
            // urls
            storageForLocation.remove(JFSStorage.MERGED_LOCATION);
            result = new JFSStorage(loc, this, allocator, listener);
            storageForLocation.put(loc, result);
        }
        return result;
    }

    /**
     * Retrieve the number of bytes represented by this object.
     *
     * @return The size
     */
    public long size() {
        long result = 0;
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            result += e.getValue().size();
        }
        return result;
    }

    /**
     * Discard the bytes held in a particular location.
     *
     * @param locations
     * @throws IOException
     */
    public void closeLocations(Location... locations) throws IOException {
        System.out.println("JFS CLOSE LOCATIONS " + Arrays.toString(locations));
        for (Location loc : locations) {
            JFSStorage store = storageForLocation.get(loc);
            if (store != null) {
                store.close();
                storageForLocation.remove(loc);
            }
        }
    }

    /**
     * Create a classloader over a particular location.
     *
     * @param location
     * @return A classloader
     */
    @Override
    public JFSClassLoader getClassLoader(Location location) {
        JFSStorage storage = forLocation(location, false);
        try {
            return storage.createClassLoader(delegate.getClassLoader(StandardLocation.CLASS_PATH));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Create a classloader specifying the parent classloader.
     *
     * @param location The location
     * @param parent The parent classloader
     * @return A classloader
     * @throws IOException
     */
    public JFSClassLoader getClassLoader(Location location, ClassLoader parent) throws IOException {
        JFSStorage storage = forLocation(location, false);
        return storage.createClassLoader(parent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        if (StandardLocation.PLATFORM_CLASS_PATH == location
                || StandardLocation.CLASS_PATH == location
                || StandardLocation.ANNOTATION_PROCESSOR_PATH == location) {
            Iterable<JavaFileObject> fos
                    = delegate.list(location, packageName, kinds, recurse);
            if (storageForLocation.containsKey(location)) {
                return new MergeIterables<>(fos, _list(location, packageName, kinds, recurse));
            }
            return fos;
        }
        return _list(location, packageName, kinds, recurse);
    }

    private Iterable<JavaFileObject> _list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        JFSStorage stor = forLocation(location, false);
        if (stor == null) {
            return Collections.emptyList();
        }
        Iterable<JavaFileObject> result = stor.list(packageName, kinds, recurse);
        LOG.log(Level.FINEST, "List {0} pkg {1} of {2} gets {3}",
                new Object[]{location, packageName, kinds, result});
        return result;
    }

    public Map<JFSFileObject, Location> listAll() {
        Map<JFSFileObject, Location> all = new HashMap<>();
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            Location loc = e.getKey();
            JFSStorage s = e.getValue();
            for (JFSFileObject fo : s.listAll(EnumSet.allOf(JavaFileObject.Kind.class), true)) {
                all.put(fo, loc);
            }
        }
        return all;
    }

    public void listAll(BiConsumer<Location, JFSFileObject> cons) {
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            Location loc = e.getKey();
            JFSStorage s = e.getValue();
            for (JFSFileObject fo : s.listAll(EnumSet.allOf(JavaFileObject.Kind.class), true)) {
                cons.accept(loc, fo);
            }
        }
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof JFSFileObjectImpl) {
            Name name = ((JFSFileObjectImpl) file).name();
            return name.asClassName();
        }
        return delegate.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a.equals(b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return true;
    }

    @Override
    public boolean hasLocation(Location location) {
        return storageForLocation.containsKey(location)
                || delegate.hasLocation(location);
    }

    public JFSStorage storageForLocation(String locationName) {
        JFSStorage result = null;
        for (Map.Entry<Location, JFSStorage> e : storageForLocation.entrySet()) {
            if (e.getKey().getName().equals(locationName)) {
                result = e.getValue();
                break;
            }
        }
        if (result == null && JFSStorage.MERGED_LOCATION.getName().equals(locationName)) {
            result = JFSStorage.createMerged(fsid, storageForLocation.values());
            storageForLocation.put(JFSStorage.MERGED_LOCATION, result);
        }
        return result;
    }

    @Override
    public JFSJavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        Name name = Name.forClassName(className, kind);
        JFSStorage stor = forLocation(location, false);
        if (stor == null) {
            throw new IOException("No files in location " + location.getName());
        }
        JFSJavaFileObject result = stor.findJavaFileObject(name, false);
        if (result == null) {
            throw new FileNotFoundException("Did not find in " + location + ": " + name);
        }
        LOG.log(Level.FINEST, "getJavaFileForInput {0} {1} {2} gets {3}",
                new Object[]{location, className, kind, result});
        return result;
    }

    @Override
    public JFSJavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        Name name = Name.forClassName(className, kind);
        JFSStorage stor = forLocation(location, true);
        JFSJavaFileObject result = stor.findJavaFileObject(name, true);
        LOG.log(Level.FINEST, "getJavaFileForOutput {0} {1} {2} gets {3}",
                new Object[]{location, className, kind, result});
        return result;
    }

    @Override
    public JFSFileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        Name name = Name.forFileName(packageName, relativeName);
        JFSStorage stor = forLocation(location, false);
        if (stor == null) {
            throw new IOException("Nothing stored in location: " + location + " (looking up " + name + ")");
        }
        JFSFileObjectImpl obj = stor.find(name, false);
        if (obj == null) {
            throw new FileNotFoundException("Did not find in " + location + ": " + name);
        }
        LOG.log(Level.FINEST, "getFileForInput {0} {1} gets {2}",
                new Object[]{location, packageName, obj});
        return obj;
    }

    public JFSFileObject getFileForOutput(Location location, Path filePath) throws IOException {
        Name nm = Name.forPath(filePath);
        return getFileForOutput(location, nm.packageName(), nm.getName(), null);
    }

    public JFSFileObject getSourceFileForOutput(String filePath) throws IOException {
        Name nm = Name.forFileName(filePath);
        return getFileForOutput(StandardLocation.SOURCE_PATH, nm.packageName(), nm.getName(), null);
    }

    @Override
    public JFSFileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        Name name = Name.forFileName(packageName, relativeName);
        JFSStorage stor = forLocation(location, true);
        JFSFileObjectImpl result = stor.find(name, true);
        LOG.log(Level.FINEST, "getFileForOutput {0} {1} gets {2}",
                new Object[]{location, packageName, result});
        return result;
    }

    /**
     * Convenience method for getting an existing JFSFileObjectImpl by path.
     *
     * @param location The location
     * @param path The path
     * @return A file object or null
     */
    public JFSFileObject get(Location location, Path path) {
        JFSStorage stor = forLocation(location, false);
        return stor == null ? null : stor.find(Name.forPath(path));
    }

    @Override
    public void flush() throws IOException {
        // do nothing
    }

    /**
     * Returns true if close() was called - if a live classloader exists over
     * some portion of this filesystem, this JFS may not be fully closed yet
     * (URLs produced by that classloader need still to be resolvable).
     *
     * @return True if close was called and closing any remaining classloaders
     * will delete the storage
     */
    public boolean closeCalled() {
        return closeCalled;
    }

    /**
     * Returns true if close() has been called and no classloader is holding
     * this JFS open - the contents have been discarded.
     *
     * @return
     */
    public boolean isReallyClosed() {
        return closeCalled && storageForLocation.isEmpty();
    }

    private volatile boolean closeCalled;

    /**
     * Close this JFS. Resources may not be released if a classloader still
     * exists which can create URLs into this JFS.
     */
    @Override
    public void close() throws IOException {
        closeCalled = true;
        Set<Location> toRemove = new HashSet<>();
        for (Map.Entry<Location, JFSStorage> e : new ArrayList<>(storageForLocation.entrySet())) {
            if (e.getValue().close()) {
                toRemove.add(e.getKey());
            } else {
                LOG.log(Level.FINE, "JFS.close(): Will not close {0} in {1} - a live classloader over it exists",
                        new Object[]{e.getKey(), fsid});
            }
        }
        for (Location loc : toRemove) {
            LOG.log(Level.FINER, "JFS.close(): removing {0} from {1}", new Object[]{loc, fsid});
            storageForLocation.remove(loc);
        }
        if (storageForLocation.isEmpty()) {
            LOG.log(Level.FINER, "JFS.close(): empty and no live classloaders - unregistering {0}", fsid);
            JFSUrlStreamHandlerFactory.unregister(this);
            delegate.close();
            allocator.destroy();
        }
    }

    void lastClassloaderClosed(Location loc) throws IOException {
        if (closeCalled) {
            storageForLocation.remove(loc);
            close();
        }
    }

    @Override
    public int isSupportedOption(String option) {
        return -1;
    }

    public JFSFileObject masquerade(Path file, Location loc, Path asPath, Charset encoding) {
        LOG.log(Level.FINEST, "JFS.masquerade(Path): Add {0} as {1} bytes to {2} in {3} with {4}",
                new Object[]{file, asPath, loc, fsid, encoding.name()});
        return forLocation(loc, true).addRealFile(asPath, file, encoding);
    }

    /**
     * Alias a file into this filesystem without reading its bytes or testing
     * its existence unless an attempt is made to read it. The resulting
     * JFSFileObject cannot be written to.
     *
     * @param file The file's actual path on disk
     * @param loc The location to include it at
     * @param asPath The path that should be used locally
     * @return A file object
     */
    public JFSFileObject masquerade(Path file, Location loc, Path asPath) {
        LOG.log(Level.FINEST, "JFS.masquerade(Path): Add {0} as {1} bytes to {2} in {3}",
                new Object[]{file, asPath, loc, fsid});
        return forLocation(loc, true).addRealFile(asPath, file);
    }

    /**
     * Alias a file into this filesystem without reading its bytes or testing
     * its existence unless an attempt is made to read it. The resulting
     * JFSFileObject cannot be written to.
     *
     * @param file The file's actual path on disk
     * @param loc The location to include it at
     * @param asPath The path that should be used locally
     * @return A file object
     */
    public JFSFileObject masquerade(Document doc, Location loc, Path asPath) {
        LOG.log(Level.FINEST, "JFS.masquerade(Document): Add {0} as {1} bytes to {2} in {3}",
                new Object[]{doc, asPath, loc, fsid});
        return forLocation(loc, true).addDocument(asPath, doc);
    }

    /**
     * Mainly for testing, copy a folder full of files into storage.
     *
     * @param dir The folder
     * @throws IOException If something goes wrong
     */
    public void load(Path dir, boolean copy) throws IOException {
        LOG.log(Level.FINEST, "JFS.load(): Add children of {0} copying? {1} bytes to {2}",
                new Object[]{dir, copy, fsid});
        Files.walkFileTree(dir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (copy) {
                    copy(file, dir, StandardLocation.SOURCE_PATH);
                } else {
                    Path rel = dir.relativize(file);
                    masquerade(file, StandardLocation.SOURCE_PATH, rel);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copy a single file into a location, giving it a path relative to a parent
     * file. This immediately reads the file and copies its bytes.
     *
     * @param file The file to copy
     * @param relativeTo A parent folder (it must be) of the file to copy
     * @param location The location to copy to
     * @return A file object
     * @throws IOException If the path is not relative, the file is not
     * readable, or something else goes wrong
     */
    public JFSFileObject copy(Path file, Path relativeTo, Location location) throws IOException {
        if (!file.startsWith(relativeTo)) {
            throw new IOException(file + " is not a child of " + relativeTo);
        }
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Name name = Name.forPath(file, relativeTo);
        byte[] bytes = convertEncoding(Files.readAllBytes(file), file, name);

        LOG.log(Level.FINEST, "JFS.copy(): Copying file {0} as {1} to {2} in {3}",
                new Object[]{file, name, location, fsid});
        return copyBytes(name, bytes, location, lastModified);
    }

    /**
     * Copy a single file into a location, giving it a path relative to a parent
     * file. This immediately reads the file and copies its bytes.
     *
     * @param file The file to copy
     * @param relativeTo A parent folder (it must be) of the file to copy
     * @param location The location to copy to
     * @return A file object
     * @throws IOException If the path is not relative, the file is not
     * readable, or something else goes wrong
     */
    public JFSFileObject copy(Path file, Location location, Path as) throws IOException {
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Name name = Name.forPath(as);
        byte[] bytes = convertEncoding(Files.readAllBytes(file), file, name);
        LOG.log(Level.FINEST, "JFS.copy(): Copying file {0} as {1} to {2} in {3}",
                new Object[]{file, name, location, fsid});
        return copyBytes(name, bytes, location, lastModified);
    }

    /**
     * Shared implementation for copying an array of bytes into the filesystem at
     * a specific location.
     *
     * @param name A name
     * @param bytes Some btes
     * @param location The location
     * @param lastModified The last modified time
     * @return A file object
     * @throws IOException
     */
    private JFSFileObject copyBytes(Name name, byte[] bytes, Location location, long lastModified) throws IOException {
        JFSStorage storage = forLocation(location, true);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        JFSFileObjectImpl result = storage.allocate(name, java);
        result.setBytes(bytes, lastModified);
        return result;
    }

    /**
     * Converts the character set of bytes being copied to that of this JFS.
     *
     * @param bytes The bytes
     * @param forFile The original file path
     * @param name The name to be used
     * @return A byte array, which may have been converted from the original
     */
    private byte[] convertEncoding(byte[] bytes, Path forFile, Name name) {
        if (name.kind() == JavaFileObject.Kind.CLASS) {
            return bytes;
        }
        // If we are copying in a file from disk, convert it to the encoding
        // this JFS is using
        org.openide.filesystems.FileObject fileObject = FileUtil.toFileObject(FileUtil.normalizeFile(forFile.toFile()));
        if (fileObject != null) {
            Charset inputCharset = FileEncodingQuery.getEncoding(fileObject);
            if (inputCharset != null && !inputCharset.equals(encoding())) {
                String in = new String(bytes, inputCharset);
                return in.getBytes(encoding());
            }
        }
        return bytes;
    }

    /**
     * For testing, copy an array of bytes into a file in some location.
     *
     * @param path
     * @param location
     * @param bytes
     * @return
     * @throws IOException
     */
    public JFSFileObject create(Path path, Location location, byte[] bytes) throws IOException {
        long lastModified = System.currentTimeMillis();
        Name name = Name.forPath(path);
        LOG.log(Level.FINEST, "JFS.create(): Add {0} with {1} bytes to {2} in {3}",
                new Object[]{name, bytes.length, location, fsid});
        JFSStorage storage = forLocation(location, true);
        boolean java = name.kind() == CLASS || name.kind() == SOURCE;
        JFSFileObjectImpl result = storage.allocate(name, java);
        result.setBytes(bytes, lastModified);
        return result;
    }

    /**
     * Create a fileobject passing a string (will be stored as utf 8).
     *
     * @param path The path
     * @param location The location
     * @param string The string
     * @return A fileObject
     * @throws IOException if something goes wrong
     */
    public JFSFileObject create(Path path, Location location, String string) throws IOException {
        return create(path, location, string.getBytes(allocator.encoding()));
    }
}
