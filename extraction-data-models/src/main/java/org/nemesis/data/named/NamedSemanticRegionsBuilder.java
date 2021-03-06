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
package org.nemesis.data.named;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.nemesis.data.impl.ArrayUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class NamedSemanticRegionsBuilder<K extends Enum<K>> {

    private final Class<K> type;
    private final Map<String, K> typeForName;
    private final Map<String, int[]> offsetsForName;
    private boolean needArrayBased;
    private final Map<String, Duplicates<K>> duplicates = new HashMap<>();

    NamedSemanticRegionsBuilder(Class<K> type) {
        this.type = type;
        typeForName = new TreeMap<>();
        offsetsForName = new TreeMap<>();
    }

    /**
     * Mark this instance as needing array-based end position storage. If a
     * NamedSemanticRegions represents reference to the string coordinates of
     * the names it provides, then none is needed, since the length of the
     * region will always be the character count of the name. If the named
     * region represents a larger or smaller region named by the name, then it
     * will need an array for end positions. Adding an element with an end
     * offset different from the name will trigger this automatically.
     *
     * @return this
     */
    public NamedSemanticRegionsBuilder<K> arrayBased() {
        needArrayBased = true;
        return this;
    }

    /**
     * A NamedSemanticRegions can contain only one occurrence of any name; if
     * more than one occurrence is added to the builder, (typically because the
     * parsed source is broken) the duplicates can be retrieved here.
     *
     * @param bc A consumer for name and duplicate regions added
     */
    public void retrieveDuplicates(BiConsumer<String, Iterable<? extends NamedSemanticRegion<K>>> bc) {
        for (Map.Entry<String, Duplicates<K>> e : duplicates.entrySet()) {
            bc.accept(e.getKey(), e.getValue());
        }
    }

    void addDuplicate(String name, K kind, int start, int end) {
        Duplicates<K> dups = duplicates.get(name);
        if (dups == null) {
            dups = new Duplicates<>(name, start, end, kind);
            duplicates.put(name, dups);
            return;
        }
        dups.add(start, end, kind);
    }

    /**
     * Add an element - may not overlap with others.
     *
     * @param name The name
     * @param kind The kind to apply to it
     * @param start The start offset, inclusive
     * @param end The end offset, exclusive
     * @return true if the passed element was not a duplicate
     */
    public boolean add(String name, K kind, int start, int end) {
        assert name != null;
        assert kind != null;
        K existing = typeForName.get(name);
        if (existing != null) {
            int[] oldOffsets = offsetsForName.get(name);
            if (oldOffsets == null) {
                oldOffsets = new int[]{0, 0};
            }
            addDuplicate(name, existing, oldOffsets[0], oldOffsets[1]);
            addDuplicate(name, kind, start, end);
        }
        if (existing != null && existing.ordinal() < kind.ordinal()) {
            return false;
        }
        typeForName.put(name, kind);
        offsetsForName.put(name, new int[]{start, end});
        if (end != name.length() + start) {
            needArrayBased = true;
        }
        return existing == null;
    }

    NamedSemanticRegionsBuilder<K> add(String name, K kind) {
        // Removed mutability from API but this is still used by tests
        assert name != null;
        assert kind != null;
        K existing = typeForName.get(name);
        if (existing != null && existing.ordinal() < kind.ordinal()) {
            return this;
        }
        typeForName.put(name, kind);
        return this;
    }

    /**
     * Complete creating the regions.
     *
     * @return A NamedSemanticRegions
     */
    public NamedSemanticRegions<K> build() {
        String[] names = typeForName.keySet().toArray(new String[typeForName.size()]);
        K[] kinds = ArrayUtil.genericArray(type, names.length);
        String last = null;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (last != null) {
                assert name.compareTo(last) >= 1 : "TreeMap keySet is unsorted";
            }
            kinds[i] = typeForName.get(name);
            assert kinds[i] != null;
            last = name;
        }
        NamedSemanticRegions<K> result;
        if (!needArrayBased) {
            result = new NamedSemanticRegions<>(names, kinds, names.length);
        } else {
            int[] starts = new int[names.length];
            int[] ends = new int[names.length];
            Arrays.fill(starts, -1);
            Arrays.fill(ends, -1);
            result = new NamedSemanticRegions<>(names, starts, ends, kinds, names.length);
        }
        for (Map.Entry<String, int[]> e : offsetsForName.entrySet()) {
            result.setOffsets(e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        return result;
    }

    static final class Duplicates<K extends Enum<K>> implements Iterable<Duplicates<K>.Dup> {

        private final String name;
        private final List<Dup> duplicates;

        Duplicates(String name, int start, int end, K kind) {
            this.name = name;
            duplicates = new ArrayList<>(3);
            duplicates.add(new Dup(start, end, kind, 0));
        }

        void add(int start, int end, K kind) {
            duplicates.add(new Dup(start, end, kind, duplicates.size() + 1));
        }

        @Override
        public Iterator<Duplicates<K>.Dup> iterator() {
            return duplicates.iterator();
        }

        class Dup implements NamedSemanticRegion<K> {

            private final int start;
            private final int end;
            private final K kind;
            private final int ordering;

            Dup(int start, int end, K kind, int ordering) {
                this.start = start;
                this.end = end;
                this.kind = kind;
                this.ordering = ordering;
            }

            @Override
            public String toString() {
                return "Dup@" + start + ":" + end + " " + kind + " o-"
                        + ordering;
            }

            @Override
            public int hashCode() {
                int hash = 3;
                hash = 17 * hash + this.start;
                hash = 17 * hash + this.end;
                hash = 17 * hash + Objects.hashCode(this.kind);
                hash = 17 * hash + this.ordering;
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
                final Dup other = (Dup) obj;
                if (this.start != other.start) {
                    return false;
                }
                if (this.end != other.end) {
                    return false;
                }
                if (this.ordering != other.ordering) {
                    return false;
                }
                return Objects.equals(this.kind, other.kind);
            }

            @Override
            public K kind() {
                return kind;
            }

            @Override
            public int ordering() {
                return ordering;
            }

            @Override
            public boolean isReference() {
                return false;
            }

            @Override
            public int start() {
                return start;
            }

            @Override
            public int end() {
                return end;
            }

            @Override
            public int index() {
                return ordering;
            }

            @Override
            public String name() {
                return Duplicates.this.name;
            }
        }
    }
}
