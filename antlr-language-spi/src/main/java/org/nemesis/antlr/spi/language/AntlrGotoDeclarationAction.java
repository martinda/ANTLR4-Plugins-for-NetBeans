package org.nemesis.antlr.spi.language;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.EditorActionNames;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

/**
 * XXX nothing else in this package is UI-related - may want to move this to its
 * own module or other package.
 *
 * @author Tim Boudreau
 */
final class AntlrGotoDeclarationAction extends AbstractEditorAction {

    private static final Logger LOGGER
            = Logger.getLogger("org.nemesis.antlr.file.file.G4GotoDeclarationAction");
    private final NameReferenceSetKey<?>[] keys;

    AntlrGotoDeclarationAction(NameReferenceSetKey<?>[] keys) {
        this.keys = notNull("keys", keys);
        putValue(ASYNCHRONOUS_KEY, true);
        putValue(NAME, EditorActionNames.gotoDeclaration);
        String trimmed
                = NbBundle.getBundle(BaseKit.class).getString("goto-declaration-trimmed");
        putValue(ExtKit.TRIMMED_TEXT, trimmed);
        putValue(BaseAction.POPUP_MENU_TEXT, trimmed);
    }

    @Override
    protected void actionPerformed(ActionEvent evt, JTextComponent component) {
        Caret caret = component.getCaret();
        if (caret == null) {
            return;
        }
        int position = caret.getDot();
        LOGGER.log(Level.FINER, "Invoke {0} at {1}", new Object[]{position,
            "G4GotoDeclarationAction"});

        try {
            ParserManager.parse(Collections.singleton(Source.create(component.getDocument())), new UserTask(){
                @Override
                public void run(ResultIterator resultIterator) throws Exception {
                    Parser.Result res = resultIterator.getParserResult();
                    if (res instanceof ExtractionParserResult) {
                        Extraction ext = ((ExtractionParserResult) res).extraction();
                        navigateTo(evt, component, ext, position);
                    }
                }
            });
            /*
            NbAntlrUtils.parseImmediately(component.getDocument(), (Extraction extraction, Exception thrown) -> {
            if (thrown != null) {
            LOGGER.log(Level.FINER, "Thrown in extracting");
            Exceptions.printStackTrace(thrown);
            return;
            }
            navigateTo(evt, component, extraction, position);
            });
            */
        } catch (ParseException ex) {
            LOGGER.log(Level.SEVERE, "Thrown in extracting " + component.getDocument(), ex);
        }
    }

    private void navigateTo(ActionEvent evt, JTextComponent component,
            Extraction extraction, int position) {
        LOGGER.log(Level.FINER, "Find ref at {0} in {1}", new Object[]{position,
            extraction.logString()});
        for (NameReferenceSetKey<?> key : keys) {
            NamedRegionReferenceSets<?> regions = extraction.references(key);
            NamedSemanticRegionReference<?> set = regions.at(position);
            if (set != null) {
                LOGGER.log(Level.FINER, "Found ref {0} navigating to {1} at {2}",
                        new Object[]{set, set.referencing(),
                            set.referencing().start()});
                navigateTo(component, set.referencing().start());
                return;
            }
        }
        try {
            navToUnknown(extraction, position);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void navToUnknown(Extraction extraction, int position) throws IOException {
        for (NameReferenceSetKey<?> k : keys) {
            if (checkOneUnknown(extraction, k, position)) {
                return;
            }
        }
    }

    private <T extends Enum<T>> void ensureOpenAndNavigate(
            SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attributed,
            StyledDocument doc,
            DataObject dob,
            EditorCookie.Observable ck,
            NameReferenceSetKey<T> key, int position) {
        JTextComponent comp = EditorRegistry.findComponent(doc);
        if (comp == null) {
            JEditorPane[] panes = ck.getOpenedPanes();
            if (panes != null && panes.length > 0) {
                comp = panes[0];
            }
        }
        if (comp != null) {
            TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
            if (tc != null) {
                boolean opening = !tc.isOpened();
                if (!opening) {
                    tc.open();
                }
                tc.requestActive();
                // If we are opening, the window system will asynchronously
                // move focus around, etc., so stay out of the way of that
                // with a timer
                if (opening) {
                    JTextComponent compFinal = comp;
                    Timer timer = new Timer(350, evt -> {
                        compFinal.requestFocus();
                        navigateTo(compFinal, attributed.key().element().start());
                    });
                    timer.setRepeats(false);
                    timer.start();
                } else {
                    comp.requestFocus();
                    navigateTo(comp, attributed.key().element().start());
                }
                return;
            }
        }
        ck.addPropertyChangeListener(new PCL(attributed.key().element().start(), ck));
        OpenCookie opener = dob.getLookup().lookup(OpenCookie.class);
        if (opener != null) {
            opener.open();
        }
    }

    class PCL implements PropertyChangeListener, ActionListener {

        private final int position;
        private final EditorCookie.Observable ck;
        private final Timer timer = new Timer(10000, this);

        public PCL(int position, EditorCookie.Observable ck) {
            this.position = position;
            this.ck = ck;
            timer.setRepeats(false);
            timer.start();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
                EditorCookie.Observable src = (EditorCookie.Observable) evt.getSource();
                JTextComponent[] comps = (JTextComponent[]) evt.getNewValue();
                if (comps.length > 0) {
                    timer.stop();
                    TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(
                            TopComponent.class, comps[0]);
                    if (tc != null) {
                        tc.requestActive();
                    }
                    src.removePropertyChangeListener(this);
                    navigateTo(comps[0], position);
                }
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ck.removePropertyChangeListener(this);
        }
    }

    private <T extends Enum<T>> boolean checkOneUnknown(Extraction extraction, NameReferenceSetKey<T> key, int position) throws DataObjectNotFoundException, IOException {
        SemanticRegions<UnknownNameReference<T>> unks = extraction.unknowns(key);
        SemanticRegion<UnknownNameReference<T>> reg = unks.at(position);
        if (reg != null) {
            Attributions<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T> attr = extraction.resolveAll(key);
            if (attr != null) {
                SemanticRegion<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<T>, NamedSemanticRegion<T>, T>> attributed = attr.attributed().at(position);
                if (attributed != null) {
                    GrammarSource<?> src = attributed.key().source();
                    Optional<FileObject> ofo = src.lookup(FileObject.class);
                    if (ofo.isPresent()) {
                        DataObject dob = DataObject.find(ofo.get());
                        EditorCookie.Observable ck = dob.getLookup().lookup(EditorCookie.Observable.class);
                        if (ck != null) {
                            StyledDocument doc = ck.openDocument();
                            Mutex.EVENT.readAccess(() -> {
                                ensureOpenAndNavigate(attributed, doc, dob, ck, key, position);
                            });
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void navigateTo(JTextComponent component, int position) {
        Mutex.EVENT.readAccess(() -> {
            Caret caret = component.getCaret();
            if (caret != null) {
                LOGGER.log(Level.FINER, "Setting caret to {0} in {1}", new Object[]{
                    position, component});
                resetCaretMagicPosition(component);
                caret.setDot(position);
            }
        });
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Arrays.deepHashCode(this.keys);
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
        final AntlrGotoDeclarationAction other = (AntlrGotoDeclarationAction) obj;
        return Arrays.deepEquals(this.keys, other.keys);
    }

}
