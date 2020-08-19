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
package org.nemesis.antlr.spi.language.highlighting;

import com.mastfrog.function.state.Bool;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nemesis.editor.ops.DocumentOperator;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsChangeListener;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A generic highlighter or error annotator, which correctly implements several
 * things that can be tricky:
 * <ul>
 * <li>When to start and stop listening on the document</li>
 * <li>Updating highlights without causing flashing</li>
 * </ul>
 * This class makes no particular assumptions about how updating of highlights
 * is (re-)triggered - it simply provides the hooks to detect when to start and
 * stop listening to the highlighting context (editor), and a way to update
 * highlights that will avoid flashing and other bad behavior that are common
 * problems.
 * <p>
 * Implementation: override <code>activated(FileObject, Document)</code>
 * and <code>deactivated(FileObject, Document)</code> to attach and detach
 * listeners; when you want to update highlights due to an event you detected,
 * call <code>updateHighlights()</code> passing it a closure which returns
 * <code>true</code> if any highlights were added to the <code>OffsetsBag</code>
 * it was passed, false if the highlights should be cleared and the bag's
 * contents should be ignored.
 * </p><p>
 * Registration: on your subclass, add a public static factory method that
 * delegates to the method <code>factory(id, zorder, function)</code> to
 * construct an instance, and annotate it <code>&#064;MimeRegistration</code>.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class AbstractHighlighter {

    private static final Map<Class<?>, RequestProcessor> rpByType = new HashMap<>();
    private final LazyHighlightsContainer lazy = new LazyHighlightsContainer( this );
    protected final HighlightsLayerFactory.Context ctx;
    private final CompL compl = new CompL();
    private OffsetsBag theBag;
    private final boolean mergeHighlights;
    protected final Logger LOG;

    protected AbstractHighlighter( HighlightsLayerFactory.Context ctx ) {
        this( ctx, true );
    }

    @SuppressWarnings( "LeakingThisInConstructor" )
    protected AbstractHighlighter( HighlightsLayerFactory.Context ctx, boolean mergeHighlights ) {
        this.LOG = Logger.getLogger( getClass().getName() );
        this.ctx = ctx;
        this.mergeHighlights = mergeHighlights;
        Document doc = ctx.getDocument();
        // XXX listen for changes, etc
        JTextComponent theEditor = ctx.getComponent();
        // Listen for component events
        theEditor.addComponentListener( WeakListeners.create(
                ComponentListener.class, compl, theEditor ) );
        // Also listen for ancestor events, because component events can be
        // deceptive, but the combination of both ensures we catch all adds/removes
        theEditor.addPropertyChangeListener( "ancestor",
                                             WeakListeners.propertyChange( compl, "ancestor", theEditor ) );
//        bag = new OffsetsBag(ctx.getDocument(), mergeHighlights);
        LOG.log( Level.FINE, "Create {0} for {1}", new Object[]{ getClass().getName(), doc } );
        // Ensure we are initialized, and don't assume we are constructed in the
        // event thread; calling isShowing() in any other thread is unsafe
        EventQueue.invokeLater( () -> {
            if ( ctx.getComponent().isShowing() ) {
                LOG.log( Level.FINER, "Component is showing, set active" );
                compl.setActive( true );
            }
        } );
    }

    /**
     * Called when the editor this instance is highlighting is made visible.
     * Perform whatever logic you need to begin listening to a file or document
     * for changes that should trigger rerunning highlighting, and enqueue an
     * initial highlighting run, here.
     *
     * @param file The file
     * @param doc  The document
     */
    protected abstract void activated( FileObject file, Document doc );

    /**
     * <i>Fully</i> detach listeners here, cancel any pending tasks, etc.
     *
     * @param file The file
     * @param doc  The document
     */
    protected abstract void deactivated( FileObject file, Document doc );

    private void onAfterDeactivated() {
        // Clean up the bag so it doesn't hang around forever as a listener
        // in the document's listener list
        OffsetsBag bag = bag( false );
        if ( bag != null ) {
            bag.discard();
            if ( theBag == bag ) {
                theBag = null;
            }
        }
    }

    private OffsetsBag bag( boolean create ) {
        OffsetsBag result = null;
        if ( !create ) {
            synchronized ( this ) {
                result = theBag;
            }
        } else if ( isActive() ) {
            synchronized ( this ) {
                if ( theBag == null && create ) {
                    Document doc = ctx.getDocument();
                    if ( doc != null ) {
                        result = theBag = new OffsetsBag( doc, mergeHighlights );
                    }
                } else {
                    result = theBag;
                }
            }
        }
        return result;
    }

    /**
     * To update highlighting of the document, call this method with a closure
     * that accepts an OffsetsBag, and returns <code>true</code> if there were
     * any highlights added to the bag, and <code>false</code> if the bag was
     * left empty (any existing highlights created by previous calls will be
     * cleared0.
     *
     * @param highlightsUpdater A predicate which modifies the empty OffsetsBag
     *                          it is passed, adding highlights where needed, and returns true if it
     *                          added any highlights to it, false if not.
     */
    protected final void updateHighlights( Predicate<OffsetsBag> highlightsUpdater ) {
        OffsetsBag bag = bag( true );
        if ( bag == null ) {
            return;
        }
        OffsetsBag brandNewBag = new OffsetsBag( ctx.getDocument(), mergeHighlights );
        Bool doUpdate = Bool.create();
        try {
            doUpdate.set( highlightsUpdater.test( brandNewBag ) );
        } finally {
            Mutex.EVENT.readAccess( () -> {
                // We may have been closed / deactivated before
                // Mutex.EVENT's EventQueue.invokeLater() call completes,
                // and that can result in tripping an AssertionError.
                // Since active updates are based on a ComponentListener that
                // should only be called from the event thread, we are safe
                // enough that if isActive() is true and the bag is still
                // the instance referenced from the field, then it is still valid
                if ( isActive() && theBag == bag ) {
                    if ( doUpdate.getAsBoolean() ) {
                        bag.setHighlights( brandNewBag );
                    } else {
                        bag.clear();
                    }
                }
                brandNewBag.discard();
            } );
        }
    }

    /**
     * Returns true if the document is visible to the user and highlighting
     * should be performed.
     *
     * @return
     */
    protected final boolean isActive() {
        return compl.active;
    }

    /**
     * Returns a single-thread request processor created for all instances of
     * this class, which can be used for asynchronous tasks while guaranteeing
     * more than one of such tasks cannot be run concurrently.
     *
     * @return A request processor
     */
    protected final RequestProcessor threadPool() {
        return threadPool( getClass() );
    }

    @SuppressWarnings( "DoubleCheckedLocking" )
    private static final RequestProcessor threadPool( Class<?> type ) {
        RequestProcessor result = rpByType.get( type );
        if ( result == null ) {
            synchronized ( rpByType ) {
                result = rpByType.get( type );
                if ( result == null ) {
                    result = new RequestProcessor( type.getName() + "-subscribe", 1, false );
                    rpByType.put( type, result );
                }
            }
        }
        return result;
    }

    /**
     * Create a factory for some type of AbstractHighlighter - typical usage is to create
     * a no-argument factory method that calls this and annotate it with
     * mime lookup registration.
     *
     * @param layerTypeId        The layer type id
     * @param zOrder             The z-order
     * @param highlighterCreator A function that returns an instance of AbstractHighlighter
     *
     * @return A generic highlighter factory
     */
    public static final HighlightsLayerFactory factory( String layerTypeId, ZOrder zOrder,
            Function<? super HighlightsLayerFactory.Context, ? extends AbstractHighlighter> highlighterCreator ) {
        return new Factory( layerTypeId, zOrder, highlighterCreator, false );
    }

    /**
     * Create a factory for some type of AbstractHighlighter - typical usage is to create
     * a no-argument factory method that calls this and annotate it with
     * mime lookup registration.
     *
     * @param layerTypeId        The layer type id
     * @param zOrder             The z-order
     * @param highlighterCreator A function that returns an instance of AbstractHighlighter
     *
     * @return A generic highlighter factory
     */
    public static final HighlightsLayerFactory factory( String layerTypeId, ZOrder zOrder,
            Function<? super HighlightsLayerFactory.Context, ? extends AbstractHighlighter> highlighterCreator,
            boolean mustHaveFile ) {
        return new Factory( layerTypeId, zOrder, highlighterCreator, mustHaveFile );
    }

    public final HighlightsContainer getHighlightsBag() {
        return lazy;
    }

    /**
     * A basic single-highlighter highlights layer factory that is easy to use
     * from a no-argument overload the <code>factory()</code> method to register
     * a highlight in the module's layer.
     */
    private static class Factory implements HighlightsLayerFactory {

        private static final HighlightsLayer[] EMPTY = new HighlightsLayer[ 0 ];
        private final ZOrder zOrder;
        private final Function<? super Context, ? extends AbstractHighlighter> highlighterCreator;
        private final String layerTypeId;
        private final boolean mustHaveFile;

        Factory( String layerTypeId, ZOrder zorder,
                Function<? super Context, ? extends AbstractHighlighter> highlighterCreator,
                boolean mustHaveFile ) {
            this.zOrder = notNull( "zorder", zorder );
            this.highlighterCreator = notNull( "highlighterCreator", highlighterCreator );
            this.layerTypeId = notNull( "layerTypeId", layerTypeId );
            this.mustHaveFile = mustHaveFile;
        }

        @Override
        public HighlightsLayer[] createLayers( HighlightsLayerFactory.Context ctx ) {
            Document doc = ctx.getDocument();
            if ( mustHaveFile ) {
                FileObject fo = NbEditorUtilities.getFileObject( doc );
                if ( fo == null ) { // preview pane, etc.
                    return EMPTY;
                }
            }
            AbstractHighlighter highlighter = highlighterCreator.apply( ctx );
            return new HighlightsLayer[]{
                HighlightsLayer.create( layerTypeId, zOrder,
                                        true, highlighter.getHighlightsBag() )
            };
        }
    }

    /**
     * Listens on the editor component, and informs the owning highlighter when
     * the component becomes visible or is hidden, so it can ignore changes
     * when the component is not onscreen.
     */
    private final class CompL extends ComponentAdapter implements Runnable, PropertyChangeListener {

        // Volatile because while highlighters are only attached and detached from the
        // event thread, it can be read from any thread that checks state
        private volatile boolean active;
        private final RequestProcessor.Task task = threadPool().create( this );

        @Override
        public void componentShown( ComponentEvent e ) {
            LOG.log( Level.FINEST, "Component shown {0}", ctx.getDocument() );
            setActive( true );
        }

        @Override
        public void componentHidden( ComponentEvent e ) {
            LOG.log( Level.FINEST, "Component hidden {0}", ctx.getDocument() );
            setActive( false );
        }

        @Override
        public void propertyChange( PropertyChangeEvent evt ) {
            if ( "ancestor".equals( evt.getPropertyName() ) ) {
                setActive( evt.getNewValue() != null );
            }
        }

        void setActive( boolean active ) {
            boolean act = this.active;
            if ( active != act ) {
                this.active = act = active;
                LOG.log( Level.FINE, "Set active to {0} for {1}",
                         new Object[]{ act, ctx.getDocument() } );
                if ( act ) {
                    task.schedule( 350 );
                } else {
                    task.cancel();
                    deactivate();
                }
            }
        }

        void deactivate() {
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject( doc );
            try {
                synchronized ( this ) {
                    LOG.log( Level.FINE, "Activating against {0}", fo );
                    try {
                        deactivated( fo, doc );
                    } finally {
                        onAfterDeactivated();
                    }
                }
            } catch ( Exception ex ) {
                LOG.log( Level.SEVERE, "Exception deactivating against "
                                       + fo + " / " + doc, ex );
            }
        }

        void activate() {
            Document doc = ctx.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject( doc );
            if ( active ) {
                try {
                    synchronized ( this ) {
                        LOG.log( Level.FINE, "Activating against {0}", fo );
                        activated( fo, doc );
                    }
                } catch ( Exception ex ) {
                    LOG.log( Level.SEVERE, "Exception activating against "
                                           + fo + " / " + doc, ex );
                }
                // Make sure the highlights container the editor is listening on
                // tells the painting infrastructure that there is something to paint
                // otherwise it will never paint with our highlight data until something
                // external triggers it
                lazy.tickle();
            } else {
                LOG.log( Level.FINE, "Not active, don't subscribe to rebuilds of {0}",
                         ctx.getDocument() );
            }
        }

        @Override
        public void run() {
            if ( active ) {
                activate();
            }
        }
    }

    /**
     * Allows our OffsetsBag to be fully detached and stop listening on the
     * document when the editor is not displayed. If that comes with too much
     * of a performance penalty, reconsider.
     */
    private static final class LazyHighlightsContainer implements HighlightsContainer, HighlightsChangeListener {
        private final AbstractHighlighter hl;
        private int lastIdHash;
        private final List<HighlightsChangeListener> listeners = new CopyOnWriteArrayList<>();

        public LazyHighlightsContainer( AbstractHighlighter hl ) {
            this.hl = hl;
        }

        void tickle() {
            Document doc = hl.ctx.getDocument();
            if ( doc != null && !listeners.isEmpty() ) {
                HighlightsChangeEvent evt;
                try {
                    evt = DocumentOperator.render( doc,
                                                   () -> {
                                                       int len = doc.getLength();
                                                       if ( len > 0 ) {
                                                           return new HighlightsChangeEvent( this, 0, len - 1 );
                                                       }
                                                       return null;
                                                   } );
                    if ( evt != null ) {
                        fire( evt );
                    }
                } catch ( BadLocationException | RuntimeException | Error ex ) {
                    Exceptions.printStackTrace( ex );
                }
            }
        }

        private synchronized OffsetsBag realBag( boolean create ) {
            OffsetsBag result = hl.bag( create );
            if ( result != null ) {
                int hash = System.identityHashCode( result );
                if ( hash != lastIdHash ) {
                    lastIdHash = hash;
                    result.addHighlightsChangeListener( this );
                    tickle();
                }
            }
            return result;
        }

        @Override
        public HighlightsSequence getHighlights( int startOffset, int endOffset ) {
            OffsetsBag bag = realBag( false );
            if ( bag == null ) {
                return HighlightsSequence.EMPTY;
            }
            return bag.getHighlights( startOffset, endOffset );
        }

        @Override
        public void addHighlightsChangeListener( HighlightsChangeListener listener ) {
            listeners.add( listener );
        }

        @Override
        public void removeHighlightsChangeListener( HighlightsChangeListener listener ) {
            listeners.remove( listener );
        }

        private void fire( HighlightsChangeEvent e ) {
            listeners.forEach( l -> {
                l.highlightChanged( e );
            } );
        }

        @Override
        public void highlightChanged( HighlightsChangeEvent event ) {
            // don't call the listeners while holding the lock
            if ( !listeners.isEmpty() ) {
                HighlightsChangeEvent e
                        = new HighlightsChangeEvent( this, event.getStartOffset(),
                                                     event.getEndOffset() );
                fire( e );
            }
        }
    }
}
