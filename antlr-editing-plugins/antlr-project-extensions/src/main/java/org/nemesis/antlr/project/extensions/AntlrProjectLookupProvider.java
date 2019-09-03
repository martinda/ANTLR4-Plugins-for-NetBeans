package org.nemesis.antlr.project.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.project.Folders;
import org.netbeans.api.project.Project;
import org.netbeans.api.queries.FileBuiltQuery;
import org.netbeans.spi.project.LookupProvider;
import org.netbeans.spi.project.LookupProvider.Registration.ProjectType;
import org.netbeans.spi.queries.FileBuiltQueryImplementation;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
@LookupProvider.Registration(projectTypes = @ProjectType(id = "org-netbeans-modules-maven")) //XXX what about ant?
public class AntlrProjectLookupProvider implements LookupProvider {

    private static final Logger LOG = Logger.getLogger(AntlrProjectLookupProvider.class.getName());

    public AntlrProjectLookupProvider() {
        LOG.log(Level.FINE, "CREATED AN {0}", AntlrProjectLookupProvider.class.getName());
    }

    @Override
    public Lookup createAdditionalLookup(Lookup baseContext) {
        Project project = baseContext.lookup(Project.class);
        LOG.log(Level.FINER, "CREATE ADDTL LOOKUP FOR {0}", project.getProjectDirectory().getName());
        InstanceContent content = new InstanceContent();
        content.add(new AntlrFileBuiltQuery());
        content.add(new AntlrSources(baseContext));
        return new AbstractLookup(content);
    }

    public static class AntlrFileBuiltQuery implements FileBuiltQueryImplementation {

        @Override
        public FileBuiltQuery.Status getStatus(FileObject file) {
            if (!"text/x-g4".equals(file.getMIMEType())) {
                return null;
            }
            LOG.log(Level.FINEST, "AntlrFileBuiltQuery.getStatus({0})", file.getPath());
            Folders owner = Folders.ownerOf(file);
            if (owner != Folders.ANTLR_GRAMMAR_SOURCES) {
                return null;
            }
            Path relativePath = Folders.ownerRelativePath(file);
            if (relativePath == null) {
                return null;
            }
            return statusFor(file, owner, relativePath);
        }

        private FileBuiltQuery.Status statusFor(FileObject file, Folders owner, Path relativePath) {
            Path classDir = relativePath.getParent();
            return new BuiltStatus(file, classDir, javaSourcePathFor(file, classDir));
        }

        static Path[] javaSourcePathFor(FileObject file, Path classDir) {
            String name = file.getName();
            List<Path> possibleSourcePaths = new ArrayList<>();
            for (Path path : Folders.CLASS_OUTPUT.find(file)) {
                Path test = path.resolve(classDir.resolve(name + "Lexer.java"));
                possibleSourcePaths.add(test.resolve(classDir).resolve(file.getName() + "Lexer.java"));
                possibleSourcePaths.add(test.resolve(classDir).resolve(file.getName() + "Parser.java"));
            }
            return possibleSourcePaths.toArray(new Path[possibleSourcePaths.size()]);
        }

        static final class BuiltStatus extends FileChangeAdapter implements FileBuiltQuery.Status {

            private final FileObject antlrFile;
            private Path[] javaSources;
            private final Path packagePath;
            private ChangeSupport supp;

            private BuiltStatus(FileObject antlrFile, Path packagePath, Path[] javaSources) {
                this.antlrFile = antlrFile;
                this.javaSources = javaSources;
                this.packagePath = packagePath;
                antlrFile.addFileChangeListener(FileUtil.weakFileChangeListener(this, antlrFile));
            }

            @Override
            public boolean isBuilt() {
                if (!antlrFile.isValid()) {
                    return false;
                }
                Path[] javaSources;
                synchronized (this) {
                    javaSources = this.javaSources;
                }
                if (javaSources.length == 0) {
                    Path[] nue = javaSourcePathFor(antlrFile, packagePath);
                    synchronized (this) {
                        javaSources = this.javaSources = nue;
                    }
                }
                boolean result = false;
                long lastModified = antlrFile.lastModified().getTime();
                for (Path p : javaSources) {
                    if (Files.exists(p)) {
                        try {
                            result = Files.getLastModifiedTime(p).toMillis() >= lastModified;
                            break;
                        } catch (IOException ex) {
                            LOG.log(Level.INFO, null, ex);
                        }
                    }
                }
                LOG.log(Level.FINEST, "Build status for {0} is {1}", new Object[]{antlrFile.getPath(), result});
                return result;
            }

            @Override
            public synchronized void fileRenamed(FileRenameEvent fe) {
                javaSources = new Path[0];
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                synchronized (this) {
                    if (supp != null) {
                        supp = null;
                    }
                }
            }

            @Override
            public void fileChanged(FileEvent fe) {
                fire();
            }

            void fire() {
                ChangeSupport supp;
                synchronized (this) {
                    supp = this.supp;
                }
                if (supp == null) {
                    return;
                }
                supp.fireChange();
            }

            @Override
            public void addChangeListener(ChangeListener l) {
                ChangeSupport supp;
                synchronized (this) {
                    supp = this.supp;
                    if (supp == null) {
                        supp = this.supp = new ChangeSupport(this);
                    }
                }
                supp.addChangeListener(l);
            }

            @Override
            public void removeChangeListener(ChangeListener l) {
                ChangeSupport supp;
                synchronized (this) {
                    supp = this.supp;
                }
                if (supp == null) {
                    supp.removeChangeListener(l);
                }
            }
        }
    }
}
