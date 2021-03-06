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
package org.nemesis.data.graph.hetero;

import com.mastfrog.abstractions.list.IndexedResolvable;
import com.mastfrog.bits.Bits;
import com.mastfrog.bits.collections.BitSetSet;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.IntGraphVisitor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;

/**
 * A graph of container and containee, where the types of the two are
 * heterogenous, such a {} delimited blocks which contain variable references.
 * Basically, a graph where there are two types of node, A and B, and A -&gt; B
 * edges or B -&gt; A edges are possible, but not S -&gt; A or B -&gt; B. This
 * allows us to coalesce, for example, a SemanticRegions defining all of the {}
 * delimited blocks in a source file with, say, a NamedSemanticRegions
 * containing all defined variables to quickly and simply get a graph of which
 * variables are defined in which blocks, which can be queried in either
 * direction.
 *
 * @author Tim Boudreau
 */
public final class BitSetHeteroObjectGraph<TI extends IndexAddressable.IndexAddressableItem, RI extends IndexAddressable.IndexAddressableItem, T extends IndexAddressable<TI>, R extends IndexAddressable<RI>> {

    private final IntGraph tree;
    private final T first;
    private final R second;

    BitSetHeteroObjectGraph(IntGraph tree, T first, R second) {
        this.tree = tree;
        this.first = first;
        this.second = second;
    }

    public static <TI extends IndexAddressable.IndexAddressableItem, RI extends IndexAddressable.IndexAddressableItem, T extends IndexAddressable<TI>, R extends IndexAddressable<RI>>
            BitSetHeteroObjectGraph<TI, RI, T, R> create(IntGraph tree, T first, R second) {
        return new BitSetHeteroObjectGraph<>(tree, first, second);
    }

    /**
     * Get the left-side collection from which this graph is composed.
     *
     * @return The left side collection
     */
    public T first() {
        return first;
    }

    /**
     * Get the right-side collection from which this graph is composed.
     *
     * @return The left side collection
     */
    public R second() {
        return second;
    }

    private Set<TI> toSetFirst(Bits set) {
        return new BitSetSliceSet<>(first, set, 0);
    }

    private Set<RI> toSetSecond(Bits set) {
        return new BitSetSliceSet<>(second, set, first.size());
    }

    private Set<IndexAddressableItem> toSetAll(Bits set) {
        return new BitSetSet<>(new AllIndexed(), set);
    }

    private int bitSetIndex(RI item) {
        return item.index() + first.size();
    }

    /**
     * Get the set of items which have no antecedents in the graph.
     *
     * @return Items from either side which have no antecedents
     */
    public Set<IndexAddressableItem> topLevelOrOrphanItems() {
        return toSetAll(tree.topLevelOrOrphanNodes());
    }

    /**
     * Get the set of items which have no children in the graph.
     *
     * @return The set of items with no children
     */
    public Set<IndexAddressableItem> bottomLevelItems() {
        return toSetAll(tree.bottomLevelNodes());
    }

    /**
     * Get the minimum distance between one item and another in the graph.
     *
     * @param a One item in the graph
     * @param b Another item in the graph
     * @return The distance, or Integer.MAX_VALUE if a is not reachable from b
     * and b is not reachable from a.
     */
    public int distance(IndexAddressableItem a, IndexAddressableItem b) {
        int aix = first.isChildType(a) ? a.index() : first.size() + a.index();
        int bix = first.isChildType(b) ? b.index() : first.size() + b.index();
        return tree.distance(aix, bix);
    }

    /**
     * Get a typed graph slice for left-side edges.
     *
     * @return The slice
     */
    public Slice<TI, RI> leftSlice() {
        return new FirstSliceImpl();
    }

    /**
     * Get a typed graph for right-side edges.
     *
     * @return The right slice
     */
    public Slice<RI, TI> rightSlice() {
        return new SecondSliceImpl();
    }


    public void walk(HeteroGraphVisitor<TI, RI> v) {
        tree.walk(new IntGraphVisitor() {
            @Override
            public void enterNode(int ruleId, int depth) {
                if (ruleId < first.size()) {
                    v.enterFirst(first.forIndex(ruleId), depth);
                } else {
                    v.enterSecond(second.forIndex(ruleId - first.size()), depth);
                }
            }

            @Override
            public void exitNode(int ruleId, int depth) {
                if (ruleId < first.size()) {
                    v.exitFirst(first.forIndex(ruleId), depth);
                } else {
                    v.exitSecond(second.forIndex(ruleId - first.size()), depth);
                }
            }
        });

    }


    class SecondSliceImpl implements Slice<RI, TI> {

        @Override
        public Set<IndexAddressableItem> closureOf(RI obj) {
            return toSetAll(tree.closureOf(bitSetIndex(obj)));
        }

        @Override
        public Set<IndexAddressableItem> reverseClosureOf(RI obj) {
            return toSetAll(tree.reverseClosureOf(bitSetIndex(obj)));
        }

        @Override
        public Set<TI> parents(RI obj) {
            return toSetFirst(tree.parents(bitSetIndex(obj)));
        }

        @Override
        public Set<TI> children(RI obj) {
            int bis = bitSetIndex(obj);
            if (bis >= tree.size()) {
                return Collections.emptySet();
            }
            return toSetFirst(tree.children(bis));
        }

        @Override
        public boolean hasOutboundEdge(RI obj, TI k) {
            return tree.hasOutboundEdge(bitSetIndex(obj), k.index());
        }

        @Override
        public boolean hasInboundEdge(RI obj, TI k) {
            return tree.hasInboundEdge(bitSetIndex(obj), k.index());
        }

        @Override
        public int distance(RI obj, TI k) {
            return tree.distance(bitSetIndex(obj), k.index());
        }

        @Override
        public int inboundReferenceCount(RI obj) {
            return tree.inboundReferenceCount(bitSetIndex(obj));
        }

        @Override
        public int outboundReferenceCount(RI obj) {
            return tree.outboundReferenceCount(bitSetIndex(obj));
        }

        @Override
        public int closureSize(RI obj) {
            return tree.closureSize(bitSetIndex(obj));
        }

        @Override
        public int reverseClosureSize(RI obj) {
            return tree.reverseClosureSize(bitSetIndex(obj));
        }

        @Override
        public int childCount(RI obj) {
            int bis = bitSetIndex(obj);
            if (bis >= tree.size()) {
                return 0;
            }
            return tree.children(bis).cardinality();
        }
    }

    class FirstSliceImpl implements Slice<TI, RI> {

        @Override
        public Set<IndexAddressableItem> closureOf(TI obj) {
            return toSetAll(tree.closureOf(obj.index()));
        }

        @Override
        public Set<IndexAddressableItem> reverseClosureOf(TI obj) {
            return toSetAll(tree.reverseClosureOf(obj.index()));
        }

        @Override
        public Set<RI> parents(TI obj) {
            return toSetSecond(tree.parents(obj.index()));
        }

        @Override
        public Set<RI> children(TI obj) {
            int bis = obj.index();
            if (bis >= tree.size()) {
                return Collections.emptySet();
            }
            return toSetSecond(tree.children(bis));
        }

        @Override
        public boolean hasOutboundEdge(TI obj, RI k) {
            return tree.hasOutboundEdge(obj.index(), bitSetIndex(k));
        }

        @Override
        public boolean hasInboundEdge(TI obj, RI k) {
            return tree.hasInboundEdge(obj.index(), bitSetIndex(k));
        }

        @Override
        public int distance(TI obj, RI k) {
            return tree.distance(obj.index(), bitSetIndex(k));
        }

        @Override
        public int inboundReferenceCount(TI obj) {
            return tree.inboundReferenceCount(obj.index());
        }

        @Override
        public int outboundReferenceCount(TI obj) {
            return tree.outboundReferenceCount(obj.index());
        }

        @Override
        public int closureSize(TI obj) {
            return tree.closureSize(obj.index());
        }

        @Override
        public int reverseClosureSize(TI obj) {
            return tree.reverseClosureSize(obj.index());
        }

        @Override
        public int childCount(TI obj) {
            if (tree.size() <= obj.index()) {
                return 0;
            }
            return tree.children(obj.index()).cardinality();
        }
    }

    private class AllIndexed implements IndexedResolvable<IndexAddressableItem> {

        @Override
        public int indexOf(Object o) {
            int result = first.indexOf(o);
            if (result >= 0) {
                return result;
            }
            result = second.indexOf(o);
            if (result >= 0) {
                result += first.size();
            }
            return result;
        }

        @Override
        public IndexAddressableItem forIndex(int index) {
            if (index < first.size()) {
                return first.forIndex(index);
            }
            return second.forIndex(index + first.size());
        }

        @Override
        public int size() {
            return first.size() + second.size();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        walk(new HeteroGraphVisitor<TI, RI>() {
            @Override
            public void enterFirst(TI ruleId, int depth) {
                char[] c = new char[depth * 2];
                Arrays.fill(c, ' ');
                sb.append(c).append(ruleId).append('\n');
            }

            @Override
            public void enterSecond(RI ruleId, int depth) {
                char[] c = new char[depth * 2];
                Arrays.fill(c, ' ');
                sb.append(c).append(ruleId).append('\n');
            }
        });
        return sb.toString();
    }
}
