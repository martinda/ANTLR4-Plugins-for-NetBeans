package org.nemesis.data.graph;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.nemesis.data.Indexed;
import org.nemesis.data.graph.bits.Bits;
import org.nemesis.data.graph.bits.MutableBits;

/**
 * A Set implementation which can contain values which are one of a fixed set of
 * possible values. An exception is thrown if a value which is not one of those
 * is added. Backed typically by a BitSet and a sorted array of strings, which
 * may be shared with other objects.
 *
 * @author Tim Boudreau
 */
public final class BitSetSet<T> extends AbstractSet<T> implements Set<T> {

    private final Bits set;
    private final Indexed<? extends T> data;

    public BitSetSet(Indexed<? extends T> data) {
        this(data, MutableBits.create(data.size()));
    }

    public BitSetSet(Indexed<? extends T> data, BitSet set) {
        this(data, Bits.fromBitSet(set));
    }

    public BitSetSet(Indexed<? extends T> data, Bits set) {
        this.data = data;
        this.set = set;
    }

    public static BitSetSet forStringList(List<String> data) {
        return new BitSetSet<>(Indexed.forStringList(data));
    }

    public static BitSetSet forStringSet(Set<String> data) {
        return new BitSetSet<>(Indexed.forStringSet(data));
    }

    public static BitSetSet forSortedStringArray(String[] data) {
        return new BitSetSet<>(Indexed.forSortedStringArray(data));
    }

    private MutableBits mutableBits() {
        if (!(set instanceof MutableBits)) {
            throw new UnsupportedOperationException("Read-only bits "
                    + "implementation.");
        }
        return (MutableBits) set;
    }

    @Override
    public int size() {
        return set.cardinality();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        int ix = indexOf(o);
        return ix < 0 ? false : set.get(ix);
    }

    @Override
    public Iterator<T> iterator() {
        if (isEmpty()) {
            return Collections.emptyIterator();
        }
        return new Iter();
    }

    class Iter implements Iterator<T> {

        int ix = -1;

        @Override
        public boolean hasNext() {
            return set.nextSetBit(ix + 1) >= 0;
        }

        @Override
        public T next() {
            int offset = set.nextSetBit(ix + 1);
            if (offset < 0) {
                throw new IllegalStateException();
            }
            ix = offset;
            return get(ix);
        }
    }

    @Override
    public boolean add(T e) {
        int ix = indexOf(e);
        if (ix < 0) {
            throw new IllegalArgumentException("Not in set: " + e);
        }
        boolean wasSet = set.get(ix);
        mutableBits().set(ix);
        return !wasSet;
    }

    @Override
    public boolean remove(Object o) {
        int ix = indexOf(o);
        if (ix < 0) {
            return false;
        }
        boolean wasSet = set.get(ix);
        mutableBits().clear(ix);
        return wasSet;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        MutableBits nue = MutableBits.create(set.cardinality());
        for (T obj : c) {
            int ix = indexOf(obj);
            if (ix < 0) {
                throw new IllegalArgumentException(obj + "");
            }
            nue.set(ix);
        }
        int oldCardinality = set.cardinality();
        mutableBits().or(nue);
        return set.cardinality() != oldCardinality;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        MutableBits nue = MutableBits.create(set.cardinality());
        for (Object o : c) {
            int ix = indexOf(o);
            if (ix >= 0) {
                nue.set(ix);
            }
        }
        int oldCardinality = set.cardinality();
        mutableBits().and(nue);
        return oldCardinality != set.cardinality();
    }

    private int indexOf(Object o) {
        return data.indexOf(o);
    }

    private T get(int index) {
        try {
            return data.forIndex(index);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Indexed size mismatch in "
                    + "BitSetSet with cardinality " + set.cardinality()
                    + " firstSetBit " + firstSet() + " lastSetBit " + lastSet()
                    + " over indexed with size " + data.size(),
                    ex);
        }
    }

    private int lastSet() {
        return set.previousSetBit(Integer.MAX_VALUE);
    }

    private int firstSet() {
        return set.nextSetBit(0);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        MutableBits nue = MutableBits.create(set.cardinality());
        for (Object o : c) {
            int ix = indexOf(o);
            if (ix >= 0) {
                nue.set(ix);
            }
        }
        int oldCardinality = set.cardinality();
        mutableBits().andNot(nue);
        return oldCardinality != set.cardinality();
    }

    @Override
    public void clear() {
        mutableBits().clear();
    }
}
