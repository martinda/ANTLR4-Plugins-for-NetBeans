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
package org.nemesis.debug.ui.trackable;

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.strings.Strings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import org.nemesis.debug.api.Trackables;
import org.nemesis.debug.api.TrackingRoots;
import org.nemesis.debug.ui.AntlrPluginDebugTopComponent;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.HtmlRenderer;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.nemesis.debug.ui.trackable//Trackables//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "TrackablesTopComponent",
        iconBase = "org/nemesis/debug/ui/trackable/pig.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "output", openAtStartup = true)
@ActionID(category = "Window", id = "org.nemesis.debug.ui.trackable.TrackablesTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_TrackablesAction",
        preferredID = "TrackablesTopComponent"
)
@Messages({
    "CTL_TrackablesAction=Trackables",
    "CTL_TrackablesTopComponent=Trackables",
    "HINT_TrackablesTopComponent=Shows status of objects which should be cleaned up but might not be"
})
public final class TrackablesTopComponent extends TopComponent implements Consumer<Set<? extends Trackables.TrackingReference<?>>>, ActionListener {

    private final DefaultListModel<Trackables.TrackingReference<?>> mdl
            = new DefaultListModel<>();

    private final Timer timer = new Timer(3000, this);
    private final RequestProcessor p = new RequestProcessor("refs");
    private final Set<String> knownMimeTypes = ConcurrentHashMap.newKeySet();
    private int ticks;

    public TrackablesTopComponent() {
        initComponents();
        setName(Bundle.CTL_TrackablesTopComponent());
        setToolTipText(Bundle.HINT_TrackablesTopComponent());
        putClientProperty(TopComponent.PROP_KEEP_PREFERRED_SIZE_WHEN_SLIDED_IN, Boolean.TRUE);
        jList1.setModel(mdl);
        jList1.setCellRenderer(new Ren());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (ticks++ % 5 == 0) {
            refresh();
        }
        repaint();
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList<>();

        jList1.setModel(mdl);
        jList1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lisrClick(evt);
            }
        });
        jScrollPane1.setViewportView(jList1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    @SuppressWarnings("CallToThreadYield")
    private void lisrClick(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lisrClick
        Set<Object> omit = new HashSet<>();
        omit.add(this);
        omit.add(mdl);
        DefaultListModel<?> m = AntlrPluginDebugTopComponent.model();
        if (m != null) {
            omit.add(m);
        }
        if (evt.getClickCount() > 1) {
            int ix = jList1.locationToIndex(evt.getPoint());
            if (ix >= 0 && ix < mdl.size()) {
                Trackables.TrackingReference<?> item = mdl.elementAt(ix);
                if (item != null) {
                    Doc doc = new Doc();
                    RefsTopComponent tc = new RefsTopComponent(item, doc);
                    tc.open();
                    tc.requestActive();
                    Object o = item.get();
                    doc.append("Searching for references to " + item.stringValue());
                    if (o != null) {
                        StatusDisplayer.getDefault().setStatusText(
                                "Scanning for references: " + item);
                        Thread thread = Thread.currentThread();
                        p.post(() -> {
                            Predicate<? super Object> tester = TrackingRoots.ignoreTester();
                            // The current thread will have a reference to the item via
                            // RequestProcessor.Processor.todo
                            omit.add(Thread.currentThread());
                            try {
                                Map<String, Object> roots = new LinkedHashMap<>();
                                ClassLoader ldr = Lookup.getDefault().lookup(ClassLoader.class);
                                if (ldr != null) {
                                    roots.put("Module system classloader (" + ldr.getClass().getSimpleName() + ")", ldr);
                                }
                                roots.put("Default Lookup", Lookup.getDefault());
                                roots.put("Current Thread (" + thread.getName() + " " + thread.getClass().getSimpleName() + ")",
                                        thread);
                                roots.put("Current context classloader", Thread.currentThread().getContextClassLoader());
                                Set<Object> rootObjs = new HashSet<>(roots.values());
                                TrackingRoots.collectAll((name, obj) -> {
                                    if (!rootObjs.contains(obj)) {
                                        roots.put(name, obj);
                                    }
                                });
                                rootObjs.clear();

                                FileObject editors = FileUtil.getConfigFile("Editors/text");
                                if (editors != null) {
                                    for (FileObject fo : editors.getChildren()) {
                                        if (fo.isFolder()) {
                                            String mime = "text/" + fo.getName();
                                            try {
                                                roots.put("MimeLookup for " + mime, MimeLookup.getLookup(mime));
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    }
                                }
                                Window[] ww = Window.getWindows();
                                for (int i = 0; i < ww.length; i++) {
                                    Window w = ww[i];
                                    if (w == null) {
                                        continue;
                                    }
                                    String name;
                                    if (w instanceof JDialog) {
                                        name = "Window-" + i + ": " + ((JDialog) w).getTitle();
                                    } else if (w instanceof JFrame) {
                                        name = "Window-" + i + ": " + ((JFrame) w).getTitle();
                                    } else {
                                        name = "Window-" + i + " (" + w.getClass().getSimpleName() + ")";
                                    }
                                    roots.put(name, w);
                                }
                                doc.append("Will search " + roots.size() + " roots.  Running a preemptive GC.");
                                for (int i = 0; i < 50; i++) {
                                    System.gc();
                                    System.runFinalization();
                                    Thread.yield();
                                }
                                repaint();
                                try {
                                    int count = ReferencesFinder.detect(obj -> obj == o, roots, omit, doc, tester);
                                    if (count > 0) {
                                        doc.append("Done.");
                                    } else {
                                        doc.append("No references found in any of " + Strings.join(", ", roots.keySet()));
                                    }
                                } catch (Exception | Error ex) {
                                    doc.append("Failed.");
                                    doc.append(Strings.toString(ex));
                                }
                            } catch (Exception | Error e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }
        }
    }//GEN-LAST:event_lisrClick

    static final class RefsTopComponent extends TopComponent {

        private final String shortName;

        RefsTopComponent(Trackables.TrackingReference<?> t, Doc doc) {
            shortName = t.toString();
            setDisplayName("Refs-" + shortName);
            JTextArea area = new JTextArea(doc);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setFont(UIManager.getFont("controlFont"));
            setLayout(new BorderLayout());
            JScrollPane pane = new JScrollPane(area);
            add(pane, BorderLayout.CENTER);
            pane.setBorder(BorderFactory.createEmptyBorder());
            pane.setViewportBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        public void open() {
            Mode mode = WindowManager.getDefault().findMode("output");
            if (mode != null) {
                mode.dockInto(this);
            }
            super.open();
        }

        @Override
        public int getPersistenceType() {
            return PERSISTENCE_NEVER;
        }

        @Override
        public String getShortName() {
            return shortName;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<Trackables.TrackingReference<?>> jList1;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        Trackables.listen(this);
        timer.start();
    }

    @Override
    public void componentClosed() {
        timer.stop();
        Trackables.unlisten(this);
        mdl.clear();
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    @Override
    public void accept(Set<? extends Trackables.TrackingReference<?>> t) {
        EventQueue.invokeLater(() -> {
            if (isDisplayable()) {
                updateModel(t);
            }
        });
    }

    private void refresh() {
        int count = mdl.size();
        int selIx = jList1.getSelectedIndex();
        boolean selectionDeleted = false;
        for (int i = count - 1; i >= 0; i--) {
            Trackables.TrackingReference<?> item = mdl.elementAt(i);
            if (item.get() == null) {
                mdl.remove(i);
                if (selIx > 0 && i < selIx) {
                    selIx--;
                }
                if (i == selIx) {
                    selectionDeleted = true;
                }
            }
        }
        if (selectionDeleted) {
            if (mdl.size() > 0) {
                jList1.setSelectedIndex(0);
            } else {
                jList1.clearSelection();
            }
        } else if (selIx >= 0) {
            jList1.setSelectedIndex(selIx);
        }
    }

    private void updateModel(Set<? extends Trackables.TrackingReference<?>> t) {
        Trackables.TrackingReference<?> oldSel = jList1.getSelectedValue();
        List<Trackables.TrackingReference<?>> l = new ArrayList<>(t);
        Set<Trackables.TrackingReference<?>> discard = new HashSet<>();
        Set<Trackables.TrackingReference<?>> keep = new HashSet<>();
        for (int i = 0; i < mdl.size(); i++) {
            Trackables.TrackingReference<?> item = mdl.elementAt(i);
            if (item.isAlive()) {
                keep.add(item);
            } else {
                discard.add(item);
            }
        }
        l.addAll(keep);
        mdl.clear();
        Collections.sort(l, (a, b) -> {
            int result = a.type().getName().compareTo(b.type().getName());
            if (result == 0) {
                result = a.compareTo(b);
            }
            return result;
        });
        int selIx = -1;
        for (int i = 0; i < l.size(); i++) {
            Trackables.TrackingReference<?> item = l.get(i);
            if (oldSel == item) {
                selIx = i;
            }
            mdl.addElement(item);
        }
        if (selIx >= 0) {
            jList1.setSelectedIndex(selIx);
        }
    }

    static final class Ren implements ListCellRenderer<Trackables.TrackingReference<?>> {

        private final HtmlRenderer.Renderer ren = HtmlRenderer.createRenderer();

        @Override
        public Component getListCellRendererComponent(JList<? extends Trackables.TrackingReference<?>> list, Trackables.TrackingReference<?> value, int index, boolean isSelected, boolean cellHasFocus) {
            String valueText = value.htmlValue();
            ren.setHtml(true);
            Component result = ren.getListCellRendererComponent(list, valueText, index, isSelected, cellHasFocus);
            ren.setHtml(true);
            ren.setText(valueText);
            ((JComponent) result).setToolTipText(value.htmlStringValue());
            return result;
        }
    }

    static class Doc extends DefaultStyledDocument implements Runnable, Consumer<String> {

        private final AtomicLinkedQueue<String> pending = new AtomicLinkedQueue<>();
        private final AtomicBoolean enqueued = new AtomicBoolean();

        public void append(String txt) {
            pending.add(txt);
            if (enqueued.compareAndSet(false, true)) {
                EventQueue.invokeLater(this);
            }
        }

        @Override
        public void accept(String t) {
            append(t);
        }

        @Override
        public void run() {
            try {
                while (!pending.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    pending.drain(line -> {
                        sb.insert(0, line + "\n");
                    });
                    writeLock();
                    try {
                        int len = getLength();
                        insertString(len, sb.toString(), null);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        writeUnlock();
                    }
                }
            } finally {
                enqueued.set(false);
            }
        }
    }
}
