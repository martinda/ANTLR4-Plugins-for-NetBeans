/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.navigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.DefaultListModel;
import javax.swing.JPopupMenu;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.awt.HtmlRenderer;

/**
 * Configuration object which aggregates the various bits of code that go into
 * creating the behavior of a navigator panel over an Extraction.
 *
 * @author Tim Boudreau
 */
public final class NavigatorPanelConfig<K extends Enum<K>> {

    private final NameReferenceSetKey<K> centralityKey;
    private final Appearance<? super NamedSemanticRegion<K>> appearance;
    private final ListModelPopulator<K> populator;
    private final Consumer<JPopupMenu> popupMenuPopulator;
    private final BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> elementFetcher;
    private final boolean sortable;
    private final String displayName;
    private final String hint;
    private final Function<Extraction, Set<String>> delimitersFinder;

    private NavigatorPanelConfig(NameReferenceSetKey<K> centralityKey, Appearance<NamedSemanticRegion<K>> appearance,
            ListModelPopulator<K> populator, Consumer<JPopupMenu> popupMenuPopulator,
            BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> elementFetcher,
            String displayName, boolean sortable, String hint, Function<Extraction, Set<String>> delimitersFinder) {
        this.centralityKey = centralityKey;
        this.appearance = appearance == null ? new DefaultAppearance() : appearance;
        this.populator = populator;
        this.popupMenuPopulator = popupMenuPopulator;
        this.hint = hint;
        this.displayName = displayName;
        this.sortable = sortable;
        this.elementFetcher = elementFetcher;
        this.delimitersFinder = delimitersFinder;
    }

    Set<String> delimiters(Extraction ext) {
        return delimitersFinder.apply(ext);
    }

    /**
     * Builder for navigator panel configurations - supply at least a display
     * name and a function to extract elements from a parse; return the
     * resulting NavigatorPanelConfig of this builder from a static method, and
     * register it with &064;AntlrNavigatorPanelRegistration to add a navigator
     * panel.
     *
     * @param <K>
     */
    public static final class Builder<K extends Enum<K>> {

        private boolean sortable;
        private Consumer<JPopupMenu> popupMenuPopulator;
        private BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> elementFetcher;
        private ListModelPopulator<K> populator;
        private String displayName;
        private String hint;
        private Appearance<NamedSemanticRegion<K>> appearance;
        private NameReferenceSetKey<K> centralityKey;
        private final Set<ExtractionKey<K>> keys = new HashSet<>();

        private Builder() {

        }

        public NavigatorPanelConfig<K> build() {
            if (elementFetcher == null) {
                throw new IllegalStateException("Element fetcher must be set");
            }
            if (displayName == null) {
                throw new IllegalStateException("Display name must be set");
            }
            return new NavigatorPanelConfig<>(centralityKey, appearance, populator == null ? new DefaultPopulator<>() : populator,
                    popupMenuPopulator, elementFetcher, displayName, sortable, hint,
                    delimiters(keys));
        }

        static <K> Function<Extraction, Set<String>> delimiters(Set<ExtractionKey<K>> keys) {
            return (ext -> {
                Set<String> result = null;
                for (ExtractionKey<K> k : keys) {
                    String s = ext.getScopingDelimiter(k);
                    if (s != null) {
                        if (result == null) {
                            result = new HashSet<>(3);
                        }
                        result.add(s);
                    }
                }
                return result == null ? Collections.emptySet() : result;
            });
        }

        static final class DefaultPopulator<K extends Enum<K>> implements ListModelPopulator<K> {

            @Override
            public int populateListModel(Extraction extraction, List<NamedSemanticRegion<K>> fetched, DefaultListModel<NamedSemanticRegion<K>> model, NamedSemanticRegion<K> oldSelection, SortTypes sort) {
                int sel = -1;
                for (NamedSemanticRegion<K> region : fetched) {
                    if (sel == -1 && oldSelection != null && region.name().equals(oldSelection.name())) {
                        sel = model.size();
                    }
                    model.addElement(region);
                }
                return sel;
            }
        }

        /**
         * Supply a key which will look up a reference graph of the regions you
         * wnat to display (you need to have set up your ExtractorBuilder to
         * create one), to enable the PAGE_RANK and EIGENVECTOR_CENTRALITY sort
         * modes, which sort most-important nodes to the top. Sets the sortable
         * flag.
         *
         * @param centralityKey A key which references the same nodes you want
         * to display
         * @return this
         */
        public Builder<K> withCentralityKey(NameReferenceSetKey<K> centralityKey) {
            if (this.centralityKey != null) {
                throw new IllegalStateException("Centrality key already set to " + this.centralityKey);
            }
            this.centralityKey = centralityKey;
            sortable = true;
            return this;
        }

        /**
         * Supply an object which will copy items from the fetched list of items
         * and put them into a new empty list model which is passed in. May be
         * called once per builder. Optional.
         *
         * @param populator The populator
         * @return this
         */
        public Builder<K> withListModelPopulator(ListModelPopulator<K> populator) {
            if (this.populator != null) {
                throw new IllegalStateException("Populator already set to " + this.populator);
            }
            this.populator = populator;
            return this;
        }

        /**
         * Set the display name of this navigator panel. Required.
         *
         * @param displayName The display name (should be localized)
         * @return this
         */
        public Builder<K> setDisplayName(String displayName) {
            if (this.displayName != null) {
                throw new IllegalStateException("Display name already set");
            }
            this.displayName = displayName;
            return this;
        }

        /**
         * Set the tooltip for this navigator panel.
         *
         * @param hint The hint
         * @return this
         */
        public Builder<K> setHint(String hint) {
            if (this.hint != null) {
                throw new IllegalStateException("Hint already set");
            }
            this.hint = hint;
            return this;
        }

        Builder<K> fetchingWith(NamedRegionKey<K> key) {
            keys.add(key);
            return fetchingWith(new FetchByKey<>(key));
        }

        public Builder<K> setSingleIcon(String icon) {
            return withAppearance(new IconAppearance<NamedSemanticRegion<K>>(icon));
        }

        /**
         * In addition to the fetcher or key supplied when creating this object,
         * also use the passed key to look up additional regions.
         *
         * @param key The key
         * @return this
         */
        public Builder<K> alsoFetchingWith(NamedRegionKey<K> key) {
            keys.add(key);
            return fetchingWith(new FetchByKey<>(key));
        }

        private static final class FetchByKey<K extends Enum<K>> implements BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> {

            private final NamedRegionKey<K> key;

            public FetchByKey(NamedRegionKey<K> key) {
                this.key = key;
            }

            @Override
            public void accept(Extraction t, List<? super NamedSemanticRegion<K>> u) {
                for (NamedSemanticRegion<K> region : t.namedRegions(key)) {
                    u.add(region);
                }
            }
        }

        /**
         * In addition to the fetcher or key supplied when creating this object,
         * also use the passed key to look up additional regions.
         *
         * @param key The key
         * @return this
         */
        public Builder<K> alsoFetchingWith(BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> fetcher) {
            return fetchingWith(fetcher);
        }

        Builder<K> fetchingWith(BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> fetcher) {
            if (this.elementFetcher != null) {
                this.elementFetcher = this.elementFetcher.andThen(fetcher);
            } else {
                this.elementFetcher = fetcher;
            }
            return this;
        }

        /**
         * Use the passed Appearance to configure the rendering of each row with
         * color, html text, icon, indentaton and more.
         *
         * @param appearance An appearance configurer
         * @return
         */
        public Builder<K> withAppearance(Appearance<NamedSemanticRegion<K>> appearance) {
            if (this.appearance != null) {
                this.appearance = this.appearance.and(appearance);
            } else {
                this.appearance = appearance;
            }
            return this;
        }

        /**
         * If called, the popup menu for this item will include menu items for
         * setting how the elements are sorted in the view.
         *
         * @return this
         */
        public Builder<K> sortable() {
            sortable = true;
            return this;
        }

        /**
         * If you want to add items to the navigator panel's popup menu, add
         * them here.
         *
         * @param populator
         * @return
         */
        public Builder<K> popupMenuPopulator(Consumer<JPopupMenu> populator) {
            if (popupMenuPopulator != null) {
                this.popupMenuPopulator = popupMenuPopulator.andThen(populator);
            } else {
                this.popupMenuPopulator = populator;
            }
            return this;
        }
    }

    String hint() {
        return hint;
    }

    String displayName() {
        return displayName;
    }

    boolean sortable() {
        return sortable;
    }

    public static <K extends Enum<K>> Builder<K> builder(NamedRegionKey<K> key) {
        return new Builder<K>().fetchingWith(key);
    }

    public static <K extends Enum<K>> Builder<K> builder(BiConsumer<Extraction, List<? super NamedSemanticRegion<K>>> elementFetcher) {
        return new Builder<K>().fetchingWith(elementFetcher);
    }

    void configureAppearance(HtmlRenderer.Renderer on, NamedSemanticRegion<K> region, boolean componentActive, Set<String> delimiter, SortTypes sort) {
        appearance.configureAppearance(on, region, componentActive, delimiter, sort);
    }

    void onPopulatePopupMenu(JPopupMenu menu) {
        if (popupMenuPopulator != null) {
            popupMenuPopulator.accept(menu);
        }
    }

    boolean isSortTypeEnabled(SortTypes type) {
        if (!sortable) {
            return false;
        }
        if (centralityKey == null) {
            return !type.isCentralitySort();
        }
        return true;
    }

    /**
     * Updates a list model and sorts it.
     *
     * @param <K> The enum type
     */
    public interface ListModelPopulator<K extends Enum<K>> {

        /**
         * Populate the list model with whatever objects this panel should find
         * in the extraction.
         *
         * @param extraction The extraction
         * @param model A new, empty model
         * @param oldSelection The selection in the panel at this time
         * @param requestedSort The sort order that should be used
         * @return The index of the old selection (if not null) in the new set
         * of model elements, or -1 if not found
         */
        int populateListModel(Extraction extraction, List<NamedSemanticRegion<K>> fetched, DefaultListModel<NamedSemanticRegion<K>> model, NamedSemanticRegion<K> oldSelection, SortTypes sort);
    }

    int populateListModel(Extraction extraction, DefaultListModel<NamedSemanticRegion<K>> newListModel, NamedSemanticRegion<K> oldSelection, SortTypes requestedSort) {
        List<NamedSemanticRegion<K>> items = new ArrayList<>(100);
        elementFetcher.accept(extraction, items);
        if (sortable && isSortTypeEnabled(requestedSort)) {
            requestedSort.sort(items, extraction, centralityKey);
        }
        return populator.populateListModel(extraction, items, newListModel, oldSelection, requestedSort);
    }

    public static <T> Appearance<T> simpleAppearance(String icon) {
        return new IconAppearance<>(icon);
    }

    public NavigatorPanel toNavigatorPanel(String mimeType) {
        return new GenericAntlrNavigatorPanel<>(mimeType, this, appearance);
    }
}
