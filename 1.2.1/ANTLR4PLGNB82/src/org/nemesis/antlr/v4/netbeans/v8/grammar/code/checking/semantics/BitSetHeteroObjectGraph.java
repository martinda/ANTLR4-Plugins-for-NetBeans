package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.BitSet;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.BitSetTree.IntRuleVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.IndexAddressable.IndexAddressableItem;

/**
 *
 * @author Tim Boudreau
 */
public final class BitSetHeteroObjectGraph<TI extends IndexAddressable.IndexAddressableItem, RI extends IndexAddressable.IndexAddressableItem, T extends IndexAddressable<TI>, R extends IndexAddressable<RI>> {

    private final BitSetTree tree;
    private final T first;
    private final R second;

    BitSetHeteroObjectGraph(BitSetTree tree, T first, R second) {
        this.tree = tree;
        this.first = first;
        this.second = second;
    }

    public T first() {
        return first;
    }

    public R second() {
        return second;
    }

    private Set<TI> toSetFirst(BitSet set) {
        return new BitSetSet(first, set);
    }

    private Set<RI> toSetSecond(BitSet set) {
        return new BitSetSet(second, set);
    }

    private Set<Object> toSetAll(BitSet set) {
        return new BitSetSet(new AllIndexed(), set);
    }

    private int bitSetIndex(RI item) {
        return item.index() + first.size();
    }

    public Set<Object> topLevelOrOrphanRules() {
        return toSetAll(tree.topLevelOrOrphanRules());
    }

    public Set<Object> bottomLevelRules() {
        return toSetAll(tree.bottomLevelRules());
    }

    public int distance(IndexAddressableItem a, IndexAddressableItem b) {
        int aix = first.isChildType(a) ? a.index() : first.size() + a.index();
        int bix = first.isChildType(b) ? b.index() : first.size() + b.index();
        return tree.distance(aix, bix);
    }

    public Slice<TI, RI> firstSlice() {
        return new FirstSliceImpl();
    }

    public Slice<RI, TI> secondSlice() {
        return new SecondSliceImpl();
    }

    public interface Slice<T, R> {

        Set<Object> closureOf(T obj);

        Set<Object> reverseClosureOf(T obj);

        Set<R> parents(T obj);

        Set<R> children(T obj);

        boolean hasOutboundEdge(T obj, R k);

        boolean hasInboundEdge(T obj, R k);

        int distance(T obj, R k);

        int inboundReferenceCount(T obj);

        int outboundReferenceCount(T obj);

        int closureSize(T obj);

        int reverseClosureSize(T obj);
    }

    public void walk(HeteroGraphVisitor<TI, RI> v) {
        tree.walk(new IntRuleVisitor() {
            @Override
            public void enterRule(int ruleId, int depth) {
                if (ruleId < first.size()) {
                    v.enterFirst(first.forIndex(ruleId), depth);
                } else {
                    v.enterSecond(second.forIndex(ruleId - first.size()), depth);
                }
            }

            @Override
            public void exitRule(int ruleId, int depth) {
                if (ruleId < first.size()) {
                    v.exitFirst(first.forIndex(ruleId), depth);
                } else {
                    v.exitSecond(second.forIndex(ruleId - first.size()), depth);
                }
            }
        });

    }

    interface HeteroGraphVisitor<T, R> {

        void enterFirst(T ruleId, int depth);

        void enterSecond(R ruleId, int depth);

        default void exitFirst(T ruleId, int depth) {

        }

        default void exitSecond(R ruleId, int depth) {

        }
    }

    class SecondSliceImpl implements Slice<RI, TI> {

        @Override
        public Set<Object> closureOf(RI obj) {
            return toSetAll(tree.closureOf(bitSetIndex(obj)));
        }

        @Override
        public Set<Object> reverseClosureOf(RI obj) {
            return toSetAll(tree.reverseClosureOf(bitSetIndex(obj)));
        }

        @Override
        public Set<TI> parents(RI obj) {
            return toSetFirst(tree.parents(bitSetIndex(obj)));
        }

        @Override
        public Set<TI> children(RI obj) {
            return toSetFirst(tree.children(bitSetIndex(obj)));
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
    }

    class FirstSliceImpl implements Slice<TI, RI> {

        @Override
        public Set<Object> closureOf(TI obj) {
            return toSetAll(tree.closureOf(obj.index()));
        }

        @Override
        public Set<Object> reverseClosureOf(TI obj) {
            return toSetAll(tree.reverseClosureOf(obj.index()));
        }

        public Set<RI> parents(TI obj) {
            return toSetSecond(tree.parents(obj.index()));
        }

        public Set<RI> children(TI obj) {
            return toSetSecond(tree.children(obj.index()));
        }

        public boolean hasOutboundEdge(TI obj, RI k) {
            return tree.hasOutboundEdge(obj.index(), bitSetIndex(k));
        }

        public boolean hasInboundEdge(TI obj, RI k) {
            return tree.hasInboundEdge(obj.index(), bitSetIndex(k));
        }

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
    }

    private class AllIndexed implements Indexed<Object> {

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
        public Object forIndex(int index) {
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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        walk(new HeteroGraphVisitor<TI, RI>() {
            @Override
            public void enterFirst(TI ruleId, int depth) {
                char[] c = new char[depth * 2];
                sb.append(c).append(ruleId).append('\n');
            }

            @Override
            public void enterSecond(RI ruleId, int depth) {
                char[] c = new char[depth * 2];
                sb.append(c).append(ruleId).append('\n');
            }
        });
        return sb.toString();
    }
}