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

import com.mastfrog.abstractions.list.IndexedResolvable;
import com.mastfrog.bits.collections.BitSetSet;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import static com.mastfrog.util.strings.LevenshteinDistance.sortByDistance;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.NamedIndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.impl.ArrayEndSupplier;
import org.nemesis.data.impl.ArrayUtil;
import org.nemesis.data.impl.ArrayUtil.Bias;
import static org.nemesis.data.impl.ArrayUtil.endSupplierHashCode;
import org.nemesis.data.impl.MutableEndSupplier;
import org.nemesis.data.impl.SizedArrayValueSupplier;

/**
 * Maps pairs of start/end offsets to a set of strings. Use with care: in
 * particular, this class does not support unsorted or duplicate names, or
 * overlapping ranges. This is the right data structure for an Antlr parse, but
 * not a general-purpose thing. It specifically uses shared arrays of names,
 * start and end positions to minimize memory and disk-cache consumption.
 * <p>
 * <b>Note:</b> The <code>NamedSemanticRegion</code> instances emitted are
 * <i>flyweight objects</i> - they are created on demand, and while they honor
 * the <code>equals()</code> contract, you will not get the same instance
 * returned for the same input twice.
 * </p>
 *
 * @author Tim Boudreau
 */
public class NamedSemanticRegions<K extends Enum<K>> implements Iterable<NamedSemanticRegion<K>>, Externalizable, NamedIndexAddressable<NamedSemanticRegion<K>> {

    private final int[] starts;
    private final SizedArrayValueSupplier ends;
    private final String[] names;
    private final K[] kinds;
    private int size;
    private transient IndexImpl index;
    private transient EnumSet<K> presentKinds;

    @SuppressWarnings("unchecked")
    private <X extends Enum<X>> void finishReadExternal(ObjectInput in, Class<X> kindType, int sz) throws IOException, ClassNotFoundException {
        X[] allKinds = kindType.getEnumConstants();
        X[] kinds = ArrayUtil.ofType(allKinds, sz);
        int[] starts = new int[sz];
        String[] names = new String[sz];
        SerializationContext ctx = SerializationContext.SER.get();
        for (int i = 0; i < sz; i++) {
            starts[i] = in.readInt();
            if (ctx != null) {
                names[i] = ctx.stringForIndex(in.readShort());
            } else {
                names[i] = in.readUTF();
            }
            kinds[i] = allKinds[in.readByte()];
        }
        SizedArrayValueSupplier ends = (SizedArrayValueSupplier) in.readObject();
        try {
            Field f = NamedSemanticRegions.class.getDeclaredField("starts");
            f.setAccessible(true);
            f.set(this, starts);
            f = NamedSemanticRegions.class.getDeclaredField("names");
            f.setAccessible(true);
            f.set(this, names);
            f = NamedSemanticRegions.class.getDeclaredField("kinds");
            f.setAccessible(true);
            f.set(this, kinds);
            f = NamedSemanticRegions.class.getDeclaredField("size");
            f.setAccessible(true);
            f.set(this, sz);
            f = NamedSemanticRegions.class.getDeclaredField("ends");
            f.setAccessible(true);
            f.set(this, ends);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Get the type of the kind enum.
     *
     * @return An enum type
     */
    @SuppressWarnings("unchecked")
    public Class<K> kindType() {
        return (Class<K>) kinds.getClass().getComponentType();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(1); // version
        out.writeInt(size);
        out.writeUTF(kinds.getClass().getComponentType().getName());
        SerializationContext ctx = SerializationContext.currentSerializationContext();
        short[] stringTable = ctx == null ? null : ctx.toArray(names, size);
        for (int i = 0; i < size; i++) {
            out.writeInt(starts[i]);
            if (stringTable != null) {
                out.writeShort(stringTable[i]);
            } else {
                out.writeUTF(names[i]);
            }
            out.writeByte(kinds[i].ordinal());
        }
        out.writeObject(ends);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int v = in.readInt();
        if (v != 1) {
            throw new IOException("Unsupported version " + v);
        }
        int sz = in.readInt();
        assert sz >= 0;
        String type = in.readUTF();
        Class<?> enumType = Class.forName(type);
        assert enumType.isEnum();
        finishReadExternal(in, (Class) enumType, sz);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final NamedSemanticRegions<?> EMPTY = new NamedSemanticRegions(new String[0], new Enum[0], 0);

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> NamedSemanticRegions<T> empty() {
        return (NamedSemanticRegions<T>) EMPTY;
    }

    NamedSemanticRegions(String[] names, int[] starts, int[] ends, K[] kinds, int size) {
        assert starts != null && ends != null && starts.length == ends.length;
        assert names != null && names.length == starts.length;
        assert kinds != null && kinds.length == names.length;
        assert kinds.getClass().getComponentType().isEnum();
        this.names = names;
        this.starts = starts;
        this.ends = ArrayUtil.createMutableArrayEndSupplier(ends);
        this.kinds = kinds;
        this.size = size;
    }

    NamedSemanticRegions(String[] names, int[] starts, K[] kinds, int size) {
        assert names.length >= size;
        assert starts.length >= size;
        assert kinds.length >= size;
        this.starts = starts;
        this.kinds = kinds;
        this.size = size;
        this.ends = new StringEndSupplier();
        this.names = names;
    }

    NamedSemanticRegions(String[] names, K[] kinds, int size) {
        assert kinds.length == names.length;
        assert setOf(names).size() == names.length : "Names array contains duplicates: " + Arrays.toString(names);
        this.starts = new int[names.length];
        this.kinds = kinds;
        this.names = names;
        Arrays.sort(this.names);
        Arrays.fill(starts, -1);
        this.ends = new StringEndSupplier();
        this.size = size;
    }

    public NamedSemanticRegions() { // for deserialization only
        this.starts = null;
        this.kinds = null;
        this.ends = null;
        this.size = -1;
        this.names = null;
    }

    /**
     * Create a copy of the NamedSemanticRegions where any items
     * whose start or end is after the passed at-point will be
     * adjusted by the passed amount.
     *
     * @param amount The amount to shift by
     * @param at The start point after which start and/or end points
     * should be adjusted
     * @return A new NamedSemanticRegions, or this if the shift amount is
     * zero, the size of this instance is zero, or the at-point is past
     * the end of the last item
     */
    public NamedSemanticRegions<K> shiftedBy(int amount, int at) {
        if (amount == 0 || size == 0 || at > ends.applyAsInt(size-1)) {
            return this;
        }
        int[] newStarts = new int[size];
        int[] newEnds = null;
        if (!areRegionSizesNameLengths()) {
            newEnds = new int[size];
        }
        K[] newKinds = CollectionUtils.genericArray(kindType(), size);
        // XXX we could be creating a collision here - should
        // test if each new region overlaps the old one, and
        // skip it if so.  Only needed if amount < 0
        int lastEnd = -1;
        int skippedItems = 0;
        for (int i = 0; i < size; i++) {
            int end = ends.applyAsInt(i);
            if (lastEnd != -1 && amount < 0 && starts[i] + amount < lastEnd) {
                skippedItems++;
                continue;
            }
            if (end < at) {
                newStarts[i-skippedItems] = starts[i];
                newKinds[i-skippedItems] = kinds[i];
                if (newEnds != null) {
                    newEnds[i-skippedItems] = end;
                }
            } else if (starts[i] < at && end >= at) {
                newStarts[i-skippedItems] = starts[i] + at;
                newKinds[i-skippedItems] = kinds[i];
                if (newEnds != null) {
                    newEnds[i-skippedItems] = end + at;
                }
            } else if (starts[i] >= at) {
                newStarts[i-skippedItems] = starts[i] + at;
                newKinds[i-skippedItems] = kinds[i];
                if (newEnds != null) {
                    newEnds[i-skippedItems] = newEnds[i] + at;
                }
            }
        }
        if (newEnds != null) {
            return new NamedSemanticRegions<>(names, newStarts, newEnds, newKinds, size-skippedItems);
        } else {
            return new NamedSemanticRegions<>(names, newStarts, newKinds, size-skippedItems);
        }
    }

    /**
     * Get the subset of all possible values of <code>K</code>, the <i>kind</i>
     * enum, which are actually present in this instance.
     *
     * @return A set of kinds
     */
    @SuppressWarnings("unchecked")
    public Set<? extends K> presentKinds() {
        if (presentKinds == null) {
            int total = kindType().getEnumConstants().length;
            presentKinds = EnumSet.noneOf((Class<K>) kinds.getClass().getComponentType());
            for (int i = 0; i < size; i++) {
                presentKinds.add(kinds[i]);
                if (presentKinds.size() == total) {
                    break;
                }
            }
        }
        return presentKinds;
    }

    /**
     * Call the passed consumer for each element whose name starts with the
     * passed prefix.
     *
     * @param prefix A prefix
     * @param c A consumer
     * @return The number of times the passed consumer was invoked
     */
    public int matchingPrefix(String prefix, Consumer<? super NamedSemanticRegion<K>> c) {
        if (prefix.isEmpty()) {
            for (int i = 0; i < size; i++) {
                c.accept(new IndexNamedSemanticRegionImpl(i));
            }
            return size;
        }
        return ArrayUtil.prefixBinarySearch(nameArray(), prefix, ix -> {
            c.accept(new IndexNamedSemanticRegionImpl(ix));
        });
    }

    /**
     * Call the passed consumer for each element whose name ends with the passed
     * suffix.
     *
     * @param suffix A suffix
     * @param c A consumer
     * @return The number of times the passed consumer was invoked
     */
    public int matchingSuffix(String suffix, Consumer<? super NamedSemanticRegion<K>> c) {
        int result = 0;
        for (int i = 0; i < size; i++) {
            String nm = names[i];
            if (nm.length() > suffix.length() && nm.endsWith(suffix)) {
                result++;
                c.accept(new IndexNamedSemanticRegionImpl(i));
            }
        }
        return result;
    }

    /**
     * Determine whether this set of regions maintains an end-position array, or
     * if region sizes are the lengths of the names.
     *
     * @return True if the caller can assume they know the length of the region
     * if they have the text of an item's name
     */
    public boolean areRegionSizesNameLengths() {
        return ends.getClass() == StringEndSupplier.class;
    }

    /**
     * Fetch the raw, uncopied name array that is the sorted backing store of
     * names. Do not modify!
     *
     * @return
     */
    public String[] nameArray() {
        if (names.length != size) {
            return Arrays.copyOf(names, size);
        }
        return names;
    }

    /**
     * Get a list of all names in this collection.
     *
     * @return A list of strings
     */
    public List<String> allNames() {
        return Arrays.asList(nameArray());
    }

    /**
     * Determine if this has the same contents as another NamedSemanticRegions;
     * this is not done by <code>equals()</code> because it is not unusual to
     * add NamedSemanticRegions instances to sets, where equals() will be called
     * a lot, and it is not cheap.
     *
     * @param other Another regions
     * @return True if they are effectively equal
     */
    public boolean equalTo(NamedSemanticRegions<?> other) {
        if (other == this) {
            return true;
        }
        if (other != null && other.size == size && other.kinds.getClass().getComponentType() == kinds.getClass().getComponentType()) {
            return Arrays.equals(names, other.names)
                    && Arrays.equals(kinds, other.kinds)
                    && Arrays.equals(starts, other.starts)
                    && other.ends.equals(ends);
        }
        return false;
    }

    /**
     * Return a set populated with all names that match the passed predicate.
     *
     * @param pred A predicate
     * @return A set of strings
     */
    public Set<String> collectNames(Predicate<NamedSemanticRegion<K>> pred) {
        BitSet set = new BitSet(size());
        for (NamedSemanticRegion<K> r : this) {
            if (pred.test(r)) {
                set.set(r.index());
            }
        }
        return new BitSetSet<>(new IndexedStringAdapter(), set);
    }

    class IndexedStringAdapter implements IndexedResolvable<String> {

        @Override
        public int indexOf(Object o) {
            return Arrays.binarySearch(names, 0, size, o);
        }

        @Override
        public String forIndex(int index) {
            return names[index];
        }

        @Override
        public int size() {
            return NamedSemanticRegions.this.size();
        }
    }

    /**
     * Create a builder for a NamedSemanticRegions with the passed key type.
     *
     * @param <K> The key type
     * @param type The key type
     * @return A builder
     */
    public static <K extends Enum<K>> NamedSemanticRegionsBuilder<K> builder(Class<K> type) {
        return new NamedSemanticRegionsBuilder<>(type);
    }

    /**
     * Get the index of a NamedSemanticRegion (the type is Object for collection
     * compatibility, but results are only returned for NamedSemanticRegion).
     *
     * @param o An object
     * @return An index or -1
     */
    @Override
    public int indexOf(Object o) {
        if (o instanceof NamedSemanticRegion<?>) {
            NamedSemanticRegion<?> n = (NamedSemanticRegion<?>) o;
            if (contains(n.name())) { // XXX check ownership instead?
                return n.index();
            }
        }
        return -1;
    }

    /**
     * Determine if an item could possibly be a child of this collection.
     *
     * @param item An item
     * @return true if it is the right type
     */
    @Override
    public boolean isChildType(IndexAddressable.IndexAddressableItem item) {
        return item instanceof NamedSemanticRegion<?>;
    }

    class StringEndSupplier implements SizedArrayValueSupplier {

        @Override
        public int get(int index) {
            return starts[index] == -1 ? -1 : starts[index] + names[index].length();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof SizedArrayValueSupplier) {
                SizedArrayValueSupplier other = (SizedArrayValueSupplier) o;
                if (other.size() == size()) {
                    int sz = size();
                    for (int i = 0; i < sz; i++) {
                        int a = get(i);
                        int b = other.get(i);
                        if (a != b) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return endSupplierHashCode(this);
        }

        @Override
        public int size() {
            return size;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<K> kType() {
        return (Class<K>) kinds.getClass().getComponentType();
    }

    /**
     * Get the set of regions whose offsets have not been set.
     *
     * @return A set of names
     */
    public Set<String> regionsWithUnsetOffsets() {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < size(); i++) {
            if (starts[i] < 0 || ends.get(i) < 0) {
                result.add(names[i]);
            }
        }
        return result;
    }

    /**
     * Get the number of items in this collection.
     *
     * @return A count of items
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Get the <code>index</code>'th element of this collection.
     *
     * @param index An index
     * @return A region
     * @throws IllegalArgumentException if the index is out of bounds.
     */
    @Override
    public NamedSemanticRegion<K> forIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Illegal index " + index);
        }
        return new IndexNamedSemanticRegionImpl(index);
    }

    Set<String> removeRegionsWithNoOffsets() {
        Set<String> result = regionsWithUnsetOffsets();
        for (String s : result) {
            remove(s);
        }
        return result;
    }

    int removeAll(Collection<String> c) {
        int result = 0;
        for (String s : c) {
            if (remove(s)) {
                result++;
            }
        }
        return result;
    }

    boolean remove(String name) {
        // XXX forIndex mutation methods out of API
        if (size == 0) {
            return false;
        }
        int ix = indexOf(name);
        if (ix < 0) {
            return false;
        }
        int last = last();
        if (last == 0) {
            size = 0;
            return true;
        }
        System.arraycopy(names, ix + 1, names, ix, size - (ix + 1));
        System.arraycopy(starts, ix + 1, starts, ix, size - (ix + 1));
        if (ends instanceof MutableEndSupplier) {
            ((MutableEndSupplier) ends).remove(ix);
        }
        System.arraycopy(kinds, ix + 1, kinds, ix, size - (ix + 1));
        size--;
        index = null;
        return true;
    }

    /**
     * Create a minimal-footprint set (backed by a BitSet mapping indices in
     * this collection's name array) which can contain elements that are present
     * in this collection (you cannot add foreign strings to it).
     *
     * @return
     */
    public Set<String> newSet() {
        return new BitSetSet<>(IndexedResolvable.forSortedStringArray(names));
    }

    /**
     * Iterate items by position.
     *
     * @return An iterator
     */
    @Override
    public Iterator<NamedSemanticRegion<K>> byPositionIterator() {
        return index().iterator();
    }

    /**
     * Iterate items by name.
     *
     * @return An iterator
     */
    @Override
    public Iterator<NamedSemanticRegion<K>> byNameIterator() {
        return iterator();
    }

    /**
     * Create another NamedSemanticRegions instance, sharing this ones keys
     * array, but whose offsets are independent - useful when collecting both
     * the offsets of the names of sections of a source and also the bounds of
     * that named section.
     *
     * @return A NamedSemanticRegions with its starts and ends uninitialized.
     */
    public NamedSemanticRegions<K> secondary() {
        // XXX should return a builder
        String[] n = Arrays.copyOf(names, size);
        K[] k = Arrays.copyOf(kinds, size);
        int[] starts = new int[size];
        int[] ends = new int[size];
        Arrays.fill(starts, -1);
        Arrays.fill(ends, -1);
        return new NamedSemanticRegions<>(n, starts, ends, k, size);
    }

    /**
     * Returns a new NamedSemanticRegions which omits entries for the passed set
     * of names.
     *
     * @param toRemove The names to remove
     * @return A new region set
     */
    public NamedSemanticRegions<K> sans(String... toRemove) {
        return sans(Arrays.asList(toRemove));
    }

    int orderingOf(int ix) {
        IndexImpl index = this.index;
        if (index == null) {
            index();
            index = this.index;
        }
        return Arrays.binarySearch(index.starts, 0, size, starts[ix]);
    }

    /**
     * Get the index of a name in position-order.
     *
     * @param name A name
     * @return An index
     */
    public int orderingOf(String name) {
        return orderingOf(internalIndexOf(name));
    }

    /**
     * Returns a new NamedSemanticRegions which omits entries for the passed set
     * of names.
     *
     * @param toRemove The names to remove
     * @return A new region set
     */
    @SuppressWarnings("unchecked")
    public NamedSemanticRegions<K> sans(Iterable<String> toRemove) {
        BitSet set = new BitSet(size());
        for (String s : toRemove) {
            int ix = indexOf(s);
            if (ix >= 0) {
                set.set(ix);
            }
        }
        int newCount = size() - set.cardinality();
        if (newCount == 0) {
            return this;
        }
        int[] newStarts = new int[newCount];
        int[] newEnds = new int[newCount];
        K[] newKinds = ArrayUtil.genericArray(kType(), newCount);
        String[] newNames = new String[newCount];
        int cursor = 0;
        for (int i = 0; i < size(); i++) {
            if (!set.get(i)) {
                newStarts[cursor] = starts[i];
                newEnds[cursor] = ends.get(i);
                newNames[cursor] = names[i];
                newKinds[cursor] = kinds[i];
                cursor++;
            }
        }
        return new NamedSemanticRegions<>(newNames, newStarts, newEnds, newKinds, newCount);
    }

    /**
     * Get the internal index of the name in question.
     *
     * @param name The name
     * @return the index, or -1 if not present
     */
    public int indexOf(String name) {
        if (name == null) {
            return -1;
        }
        int result = Arrays.binarySearch(names, 0, size, name);
        return result < 0 ? -1 : result;
    }

    private int internalIndexOf(String name) {
        int result = Arrays.binarySearch(names, 0, size, name);
        if (result < 0) {
            throw new IllegalArgumentException("Not a known name '"
                    + name + "' in " + Arrays.toString(names));
        }
        return result;
    }

    int start(String name) {
        return starts[internalIndexOf(name)];
    }

    int end(String name) {
        return ends.get(internalIndexOf(name));
    }

    void setOffsets(String name, int start, int end) {
        assert end > start;
        assert name != null;
        if (!(ends instanceof MutableEndSupplier) && end != start + name.length()) {
            throw new IllegalStateException("Ends in this Offsets are based on string length,"
                    + " but attempting to set the end for " + name + " to " + end
                    + " which is not start + name.length() = " + (start + name.length())
                    + ". Use the constructor that takes an array of starts and ends if you want that.");
        }
        int ix = internalIndexOf(name);
        starts[ix] = start;
        if (ends instanceof MutableEndSupplier) {
            ((MutableEndSupplier) ends).setEnd(ix, end);
        }
    }

    /**
     * Get the region with the passed name; throws an IllegalArgumentException
     * if no such name is present.
     *
     * @param name The name
     * @return A region
     * @throws IllegalArgumentException if the name is not present
     */
    public NamedSemanticRegion<K> regionFor(String name) {
        int ix = indexOf(name);
        return ix < 0 ? null : new IndexNamedSemanticRegionImpl(ix);
    }

    /**
     * Get the kind of the region with this index.
     *
     * @param index The index of the region
     * @throws ArrayIndexOutOfBoundsException if out of range
     * @return
     */
    public K kind(int index) {
        return kinds[index];
    }

    /**
     * Get the kind of the region with the passed name; throws an
     * IllegalArgumentException if no such name is present.
     *
     * @param name The name
     * @return A region
     * @throws IllegalArgumentException if the name is not present
     */
    public K kind(String name) {
        return kind(internalIndexOf(name));
    }

    /**
     * Get the name at a given index.
     *
     * @param index
     * @throws ArrayIndexOutOfBoundsException if the index is &lt; 0 or &gt;
     * size().
     * @return A name
     */
    public String get(int index) {
        return names[index];
    }

    void setKind(String name, K kind) {
        kinds[internalIndexOf(name)] = kind;
    }

    @Override
    public Iterator<NamedSemanticRegion<K>> iterator() {
        return new Iter();
    }

    /**
     * Determine if this NamedSemanticRegions instance contains a particular
     * name.
     *
     * @param name The name
     * @return true if it is present
     */
    public boolean contains(String name) {
        return indexOf(name) >= 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(names[i]).append('@').append(starts[i]).append(':').append(ends.get(i))
                    .append('(').append(i).append(')');
            if (i != size() - 1) {
                sb.append(',');
            }
        }
        sb.append(" EndSupplier: ").append(this.ends);
        return sb.toString();
    }

    /**
     * Get an iterable of just those regions with the kind requested.
     *
     * @param The requested kind
     * @return kind An iterable of regions
     */
    public Iterable<NamedSemanticRegion<K>> ofKind(K kind) {
        return new ByKindIter(kind);
    }

    /**
     * Add all items in this collection to the passed list, in position order.
     *
     * @param into
     */
    public void collectItems(List<? super NamedSemanticRegion<K>> into) {
        for (NamedSemanticRegion<K> i : this) {
            into.add(i);
        }
    }

    private class ByKindIter implements Iterator<NamedSemanticRegion<K>>, Iterable<NamedSemanticRegion<K>> {

        private int ix = 0;
        private NamedSemanticRegion<K> nextItem;
        private final K kind;

        ByKindIter(K kind) {
            this.kind = kind;
        }

        @Override
        public Iterator<NamedSemanticRegion<K>> iterator() {
            if (ix == 0) {
                return this;
            }
            return new ByKindIter(kind);
        }

        @Override
        public boolean hasNext() {
            if (nextItem == null && ix < size()) {
                for (int i = ix; i < size(); i++) {
                    if (kinds[i] != kind) {
                        nextItem = new IndexNamedSemanticRegionImpl(i);
                        break;
                    }
                    ix++;
                }
            }
            return nextItem != null;
        }

        @Override
        public NamedSemanticRegion<K> next() {
            NamedSemanticRegion<K> result = nextItem;
            if (result == null) {
                throw new NoSuchElementException("next() called after end");
            }
            nextItem = null;
            ix++;
            return result;
        }
    }

    final class EmptyReferenceSet implements NamedRegionReferenceSet<K> {

        private final int index;

        EmptyReferenceSet(int index) {
            this.index = index;
        }

        @Override
        public boolean isChildType(IndexAddressableItem foo) {
            return false;
        }

        @Override
        public void collectItems(List<? super NamedSemanticRegionReference<K>> items) {
            // do nothing
        }

        public Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred) {
            return Collections.emptySet();
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            throw new IllegalArgumentException("Empty reference set: " + index);
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public NamedSemanticRegionReference<K> at(int pos) {
            return null;
        }

        @Override
        public String name() {
            return names[index];
        }

        @Override
        public boolean contains(int pos) {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public NamedSemanticRegion<K> original() {
            return new IndexNamedSemanticRegionImpl(index);
        }

        @Override
        public int indexOf(Object o) {
            return -1;
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> byNameIterator() {
            return Collections.emptyIterator();
        }
    }

    /**
     * Create a builder for a reference set that stores offsets of "references"
     * to elements in this collection.
     *
     * @return A reference set builder
     */
    public NamedRegionReferenceSetsBuilder<K> newReferenceSetsBuilder() {
        return new ReferenceSetsBuilderImpl();
    }

    private final class ReferenceSetsBuilderImpl extends NamedRegionReferenceSetsBuilder<K> {

        SemanticRegions.SemanticRegionsBuilder<Integer> bldr = SemanticRegions.builder(Integer.class);

        @Override
        public void addReference(String name, int start, int end) {
            int ix = internalIndexOf(name);
            bldr.add(ix, start, end);
        }

        @Override
        public NamedRegionReferenceSets<K> build() {
            return new ReferenceSetsImpl(bldr.build());
        }
    }

    private final class ReferenceSetsImpl implements NamedRegionReferenceSets<K> {

        private final SemanticRegions<Integer> regions;
        private transient EmptyNamedRegionReferenceSets.EmptyRS<K> emptyRefs;

        ReferenceSetsImpl(SemanticRegions<Integer> regions) {
            this.regions = regions;
        }

        @Override
        public Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred) {
            Set<String> set = new HashSet<>(size());
            for (NamedSemanticRegionReference<K> reg : asIterable()) {
                if (pred.test(reg)) {
                    set.add(reg.name());
                }
            }
            return set;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (SemanticRegion<Integer> reg : regions) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                RefItem i = new RefItem(reg);
                sb.append(i);
            }
            return sb.toString();
        }

        @Override
        public NamedSemanticRegionReference<K> itemAt(int pos) {
            SemanticRegion<Integer> reg = regions.at(pos);
            return reg == null ? null : new RefItem(reg);
        }

        @Override
        public int size() {
            return regions.size();
        }

        @Override
        public NamedSemanticRegionReference<K> at(int position) {
            return itemAt(position);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public boolean isChildType(IndexAddressableItem item) {
            boolean result = item != null && item.getClass() == RefItem.class
                    && ((RefItem) item).owner() == this;
            if (!result && item != null && item.getClass() == NamedRegionReferenceSetImpl.ReferenceSetWrapper.class) {
                NamedRegionReferenceSetImpl.ReferenceSetWrapper w = (NamedRegionReferenceSetImpl.ReferenceSetWrapper) item;
                RefItem ri = w.ri;
                return isChildType(ri);
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int indexOf(Object o) {
            if (o instanceof IndexAddressableItem && isChildType((IndexAddressableItem) o)) {
                if (o.getClass() == NamedRegionReferenceSetImpl.ReferenceSetWrapper.class) {
                    o = ((NamedRegionReferenceSetImpl.ReferenceSetWrapper) o).ri;
                }
                RefItem ri = (RefItem) o;
                return ri.index();
            }
            return -1;
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            return regionFor(regions.forIndex(index));
        }

        private RefItem regionFor(SemanticRegion<Integer> reg) {
            return reg == null ? null : new RefItem(reg);
        }

        @Override
        public NamedRegionReferenceSet<K> references(String name) {
            int ix = NamedSemanticRegions.this.indexOf(name);
            if (ix < 0) {
                return null;
            }
            int[][] ices = this.keyIndex();
            if (ices[ix] == null) {
                return emptyRefs == null
                        ? emptyRefs = new EmptyNamedRegionReferenceSets.EmptyRS<>()
                        : emptyRefs;
            }
            return new NamedRegionReferenceSetImpl(ix, ices[ix]);
        }

        @Override
        public Iterator<NamedRegionReferenceSet<K>> iterator() {
            return new RefI();
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
            List<NamedSemanticRegionReference<K>> refs = new ArrayList<>();
            collectItems(refs);
            Collections.sort(refs);
            return refs.iterator();
        }

        @Override
        public NamedSemanticRegions<K> originals() {
            return NamedSemanticRegions.this;
        }

        private class RefI implements Iterator<NamedRegionReferenceSet<K>> {

            private final int[][] index = keyIndex();
            private int ix = -1;

            @Override
            public boolean hasNext() {
                return ix + 1 < index.length;
            }

            @Override
            public NamedRegionReferenceSet<K> next() {
                int[] nxt = index[++ix];
                if (nxt != null) {
                    return new NamedRegionReferenceSetImpl(ix, nxt);
                } else {
                    return new EmptyReferenceSet(ix);
                }
            }
        }

        private class NamedRegionReferenceSetImpl implements NamedRegionReferenceSet<K>, NamedIndexAddressable<NamedSemanticRegionReference<K>> {

            private final int[] keys;
            private final int origIndex;

            NamedRegionReferenceSetImpl(int origIndex, int[] keys) {
                this.keys = keys;
                this.origIndex = origIndex;
            }

            @Override
            public int size() {
                return keys.length;
            }

            @Override
            public ReferenceSetWrapper forIndex(int ix) {
                if (ix < 0 || ix > keys.length) {
                    throw new IllegalArgumentException("Out of range: " + ix);
                }
                return new ReferenceSetWrapper(new RefItem(regions.forIndex(keys[ix])), ix);
            }

            @Override
            public boolean contains(int pos) {
                SemanticRegion<Integer> reg = regions.at(pos);
                if (reg != null) {
                    return reg.key() == origIndex;
                }
                return false;
            }

            @Override
            public String name() {
                return NamedSemanticRegions.this.names[origIndex];
            }

            @Override
            public NamedSemanticRegionReference<K> at(int pos) {
                SemanticRegion<Integer> i = regions.at(pos);
                if (i != null && origIndex == i.key()) {
                    int localIndex = Arrays.binarySearch(keys, i.key());
                    return localIndex < 0
                            ? null
                            : new ReferenceSetWrapper(regionFor(i), origIndex);
                }
                return null;
            }

            @Override
            public NamedSemanticRegion<K> original() {
                return NamedSemanticRegions.this.forIndex(origIndex);
            }

            @Override
            public void collectItems(List<? super NamedSemanticRegionReference<K>> items) {
                for (NamedSemanticRegionReference<K> r : this) {
                    items.add(r);
                }
            }

            @Override
            public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
                return iterator();
            }

            @Override
            public Iterator<NamedSemanticRegionReference<K>> iterator() {
                return new SI();
            }

            @Override
            public String toString() {
                return name();
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean isChildType(IndexAddressableItem item) {
                if (item == null) {
                    return false;
                }
                if (item instanceof NamedSemanticRegionReference<?>) {
                    if (ReferenceSetWrapper.class == item.getClass()) {
                        ReferenceSetWrapper w = (ReferenceSetWrapper) item;
                        return w.owner() == this
                                && ReferenceSetsImpl.this.isChildType(w.ri);
                    }
                }
                return false;
            }

            @Override
            public int indexOf(Object o) {
                if (o instanceof IndexAddressableItem && isChildType((IndexAddressableItem) o)) {
                    return ((IndexAddressableItem) o).index();
                }
                return -1;
            }

            class SI implements Iterator<NamedSemanticRegionReference<K>> {

                int ix = -1;

                @Override
                public boolean hasNext() {
                    return ix + 1 < keys.length;
                }

                @Override
                public NamedSemanticRegionReference<K> next() {
                    if (ix >= keys.length) {
                        throw new NoSuchElementException();
                    }
                    SemanticRegion<Integer> reg = regions.forIndex(keys[++ix]);
                    return new ReferenceSetWrapper(regionFor(reg), ix);
                }
            }

            class ReferenceSetWrapper implements NamedSemanticRegionReference<K> {

                private final RefItem ri;
                private final int setIndex;

                ReferenceSetWrapper(RefItem ri, int setIndex) {
                    this.ri = ri;
                    this.setIndex = setIndex;
                }

                NamedRegionReferenceSetImpl owner() {
                    return NamedRegionReferenceSetImpl.this;
                }

                @Override
                public NamedSemanticRegion<K> referencing() {
                    return ri.referencing();
                }

                @Override
                public int referencedIndex() {
                    return ri.referencedIndex();
                }

                @Override
                public K kind() {
                    return ri.kind();
                }

                @Override
                public int ordering() {
                    return ri.ordering();
                }

                @Override
                public boolean isReference() {
                    return true;
                }

                @Override
                public String name() {
                    return ri.name();
                }

                @Override
                public int start() {
                    return ri.start();
                }

                @Override
                public int end() {
                    return ri.end();
                }

                @Override
                public int index() {
                    return setIndex;
                }

                @Override
                public int hashCode() {
                    return ri.hashCode() + (7 * setIndex);
                }

                @Override
                public boolean equals(Object o) {
                    if (o == null) {
                        return false;
                    } else if (o == this) {
                        return true;
                    } else if (o instanceof NamedSemanticRegionReference<?>) {
                        NamedSemanticRegionReference<?> other = (NamedSemanticRegionReference<?>) o;
                        return start() == other.start() && end() == other.end() && name().equals(other.name())
                                && Objects.equals(kind(), other.kind());
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return "set:" + setIndex + ":" + ri;
                }

                @Override
                public NamedSemanticRegions<K> ownedBy() {
                    return NamedSemanticRegions.this;
                }
            }
        }

        private transient int[][] keyIndex;

        private int[][] keyIndex() {
            // XXX could use an int[][][] and omit blank arrays for
            // not-present indices
            if (keyIndex != null) {
                return keyIndex;
            }
            Map<Integer, List<SemanticRegion<Integer>>> m = new TreeMap<>();
            for (SemanticRegion<Integer> r : regions) {
                List<SemanticRegion<Integer>> forRegion = m.get(r.key());
                if (forRegion == null) {
                    forRegion = new ArrayList<>(15);
                    m.put(r.key(), forRegion);
                }
                forRegion.add(r);
            }
            int[][] result = new int[NamedSemanticRegions.this.size()][];
            for (int i = 0; i < NamedSemanticRegions.this.size(); i++) {
                List<SemanticRegion<Integer>> l = m.get(i);
                if (l != null) {
                    int[] items = new int[l.size()];
                    result[i] = items;
                    for (int j = 0; j < items.length; j++) {
                        items[j] = l.get(j).index();
                    }
                }
            }
            return keyIndex = result;
        }

        class RefItem implements NamedSemanticRegionReference<K> {

            private final SemanticRegion<Integer> reg;
            private final int ix;

            RefItem(SemanticRegion<Integer> reg) {
                this.reg = reg;
                ix = reg.key();
            }

            @Override
            public int hashCode() {
                return ix + (7 * reg.hashCode());
            }

            @Override
            public boolean equals(Object o) {
                if (o == null) {
                    return false;
                } else if (o == this) {
                    return true;
                } else if (o instanceof NamedSemanticRegionReference<?>) {
                    NamedSemanticRegionReference<?> other = (NamedSemanticRegionReference<?>) o;
                    return start() == other.start() && end() == other.end() && name().equals(other.name())
                            && Objects.equals(kind(), other.kind());
                }
                return false;
            }

            ReferenceSetsImpl owner() {
                return ReferenceSetsImpl.this;
            }

            @Override
            public K kind() {
                return kinds[ix];
            }

            @Override
            public int ordering() {
                return reg.index();
            }

            @Override
            public boolean isReference() {
                return true;
            }

            @Override
            public String name() {
                return names[ix];
            }

            @Override
            public int start() {
                return reg.start();
            }

            @Override
            public int end() {
                return reg.end();
            }

            @Override
            public int index() {
                return reg.index();
            }

            @Override
            public NamedSemanticRegion<K> referencing() {
                return NamedSemanticRegions.this.forIndex(ix);
            }

            @Override
            public int referencedIndex() {
                return ix;
            }

            @Override
            public String toString() {
                return "ref:" + index() + ":" + name() + "@" + start() + ":" + end() + "->" + referencedIndex();
            }

            @Override
            public NamedSemanticRegions<K> ownedBy() {
                return NamedSemanticRegions.this;
            }
        }
    }

    private final class Iter implements Iterator<NamedSemanticRegion<K>> {

        private int ix = -1;

        @Override
        public boolean hasNext() {
            return ix + 1 < size();
        }

        @Override
        public NamedSemanticRegion<K> next() {
            return new IndexNamedSemanticRegionImpl(++ix);
        }
    }

    /**
     * Get an index which can be used to quickly and efficiently look up items
     * by position.
     *
     * @return An index
     */
    public NamedSemanticRegionPositionIndex<K> index() {
        if (index != null) {
            return index;
        }
        List<IndexNamedSemanticRegionImpl> all = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            all.add(new IndexNamedSemanticRegionImpl(i));
        }
        Collections.sort(all);
        int[] indices = new int[size()];
        int[] sortedStarts = new int[size()];
        int[] sortedEnds = new int[size()];
        for (int i = 0; i < size(); i++) {
            IndexNamedSemanticRegionImpl item = all.get(i);
            indices[i] = item.index();
            if (item.start() == item.end()) {
                throw new IllegalStateException("Some indices not set: "
                        + item.name() + "@" + item.start() + ":" + item.end()
                        + "-" + item.kind());
            }
            sortedStarts[i] = item.start();
            sortedEnds[i] = item.end();
        }
        return index = new IndexImpl(sortedStarts, sortedEnds, indices);
    }

    int last() {
        return size() - 1;
    }

    /**
     * Convenience method that generates Java code for constructing an instance
     * identical to this one, for use in creating tests.
     *
     * @return A java code string
     */
    public String toCode() {
        String tn = this.kType().getName().replace('$', '.');
        StringBuilder sb = new StringBuilder("NamedSemanticRegionsBuilder<").append(tn).append("> bldr=NamedSemanticRegions.builder(")
                .append(tn).append(".class").append(");\n");
        for (int i = 0; i < size; i++) {
            // String name, K kind, int start, int end
            sb.append("bldr.add(\"").append(names[i]).append('"').append(", ")
                    .append(kinds == null || kinds[i] == null ? "null"
                            : tn + "." + kinds[i].name()).append(", ")
                    .append(starts[i]).append(", ").append(ends.get(i)).append(");\n");
        }
        return sb.toString();
    }

    /**
     * Get the region whose bounds contains the passed position, if any.
     *
     * @param position A character position
     * @return A region
     */
    @Override
    public NamedSemanticRegion<K> at(int position) {
        return index().regionAt(position);
    }

    private final class IndexImpl implements NamedSemanticRegionPositionIndex<K> {

        private final int[] starts;
        private final int[] ends;
        private final int[] indices;

        IndexImpl(int[] starts, int[] ends, int[] indices) {
            this.starts = starts;
            this.ends = ends;
            this.indices = indices;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < starts.length; i++) {
                sb.append(names[indices[i]]).append("@").append(starts[i]).append(":").append(ends[i]);
                if (i != starts.length - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        @Override
        public NamedSemanticRegion<K> first() {
            return indices.length == 0 ? null : new IndexNamedSemanticRegionImpl(indices[0]);
        }

        @Override
        public NamedSemanticRegion<K> withStart(int start) {
            int offset = Arrays.binarySearch(starts, 0, size, start);
            return offset < 0 ? null : new IndexNamedSemanticRegionImpl(indices[offset]);
        }

        @Override
        public NamedSemanticRegion<K> withEnd(int end) {
            int offset = Arrays.binarySearch(ends, 0, size, end);
            return offset < 0 ? null : new IndexNamedSemanticRegionImpl(indices[offset]);
        }

        @Override
        public NamedSemanticRegion<K> nearestPreceding(int position) {
            if (position < 0) {
                return null;
            }
            int offset = ArrayUtil.lastOffsetLessThanOrEqualTo(position, starts, size, Bias.FORWARD);
            return offset < 0 ? null : new IndexNamedSemanticRegionImpl(indices[offset]);
        }

        private int indexFor(int pos) {
            // XXX could use the original end supplier wrapped in one which
            // looks up by index, and forgo having an ends array here
            int result = ArrayUtil.rangeBinarySearch(pos, starts, new ArrayEndSupplier(ends), size);
            assert result < 0 || (pos >= starts[result] && pos < ends[result]) : "rangeBinarySearch bogus result for regionAt(" + pos + ")"
                    + new IndexNamedSemanticRegionImpl(indices[result]);
            return result;
        }

        @Override
        public NamedSemanticRegion<K> regionAt(int pos) {
            int ix = indexFor(pos);
            return ix < 0 ? null : new IndexNamedSemanticRegionImpl(indices[ix]);
        }

        @Override
        public Iterator<NamedSemanticRegion<K>> iterator() {
            return new IndexIter();
        }

        class IndexIter implements Iterator<NamedSemanticRegion<K>> {

            private int ix = -1;

            @Override
            public boolean hasNext() {
                return ix + 1 < size();
            }

            @Override
            public NamedSemanticRegion<K> next() {
                return new IndexNamedSemanticRegionImpl(indices[++ix]);
            }
        }
    }

    private class IndexNamedSemanticRegionImpl implements NamedSemanticRegion<K> {

        private final int index;

        IndexNamedSemanticRegionImpl(int index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return ordering() + ":" + name() + "@" + start() + ":" + end() + "(" + index() + ")"
                    + ":" + kind();
        }

        @Override
        public boolean isReference() {
            return false;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public K kind() {
            return kinds[index];
        }

        @Override
        public int ordering() {
            return orderingOf(index);
        }

        @Override
        public int start() {
            return starts[index];
        }

        @Override
        public int end() {
            return ends.get(index);
        }

        @Override
        public String name() {
            return names[index];
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof NamedSemanticRegion<?>) {
                NamedSemanticRegion<?> other = (NamedSemanticRegion<?>) o;
                return other.name().equals(name());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return name().hashCode();
        }
    }

    public Iterable<NamedSemanticRegion<K>> combinedIterable(NamedIndexAddressable<? extends NamedSemanticRegion<K>> other, boolean positionSort) {
        List<NamedIndexAddressable<? extends NamedSemanticRegion<K>>> l = new ArrayList<>(2);
        l.add(this);
        l.add(other);
        return new CombineIterable<>(positionSort, l);
    }

    public static <K extends Enum<K>> Iterable<NamedSemanticRegion<K>> combine(Collection<? extends NamedIndexAddressable<? extends NamedSemanticRegion<K>>> all, boolean positionSort) {
        return new CombineIterable<>(positionSort, all);
    }

    private static class CombineIterable<T extends Enum<T>> implements Iterable<NamedSemanticRegion<T>> {

        private final boolean positionSort;
        private final Collection<? extends NamedIndexAddressable<? extends NamedSemanticRegion<T>>> coll;

        CombineIterable(boolean positionSort, Collection<? extends NamedIndexAddressable<? extends NamedSemanticRegion<T>>> coll) {
            this.positionSort = positionSort;
            this.coll = coll;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<NamedSemanticRegion<T>> iterator() {
            return new CIter(positionSort, coll);
        }

        private static final class CIter<T extends Enum<T>> implements Iterator<NamedSemanticRegion<T>> {

            private final List<IteratorHolder<T>> holders;

            @SuppressWarnings("unchecked")
            CIter(boolean positionSort, Collection<? extends NamedIndexAddressable<NamedSemanticRegion<T>>> coll) {
                holders = new ArrayList(coll.size());
                for (NamedIndexAddressable<NamedSemanticRegion<T>> r : coll) {
                    holders.add(new IteratorHolder<>(positionSort ? r.byPositionIterator() : r.byNameIterator(), positionSort));
                }
            }

            @Override
            public boolean hasNext() {
                if (holders.isEmpty()) {
                    return false;
                }
                Collections.sort(holders);
                for (IteratorHolder<T> i : holders) {
                    if (i.hasNext()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public NamedSemanticRegion<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return holders.get(0).next();
            }
        }

        private static final class IteratorHolder<T extends Enum<T>> implements Iterator<NamedSemanticRegion<T>>, Comparable<IteratorHolder<T>> {

            private final Iterator<NamedSemanticRegion<T>> iter;
            private NamedSemanticRegion<T> next;
            private final boolean positionSort;

            IteratorHolder(Iterator<NamedSemanticRegion<T>> iter, boolean positionSort) {
                this.iter = iter;
                next = iter.hasNext() ? iter.next() : null;
                this.positionSort = positionSort;
            }

            NamedSemanticRegion<T> checkNext() {
                hasNext();
                return next;
            }

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                if (iter.hasNext()) {
                    next = iter.next();
                }
                return next != null;
            }

            @Override
            public NamedSemanticRegion<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                NamedSemanticRegion<T> obj = next;
                next = null;
                return obj;
            }

            @Override
            public int compareTo(IteratorHolder<T> o) {
                NamedSemanticRegion<T> myNext = checkNext();
                NamedSemanticRegion<T> oNext = o.checkNext();
                if (myNext == null && oNext != null) {
                    return 1;
                } else if (oNext == null && myNext != null) {
                    return -1;
                } else if (oNext == null && myNext == null) {
                    return 0;
                } else {
                    if (positionSort) {
//                        return myNext.compareTo(oNext);
                        int ms = myNext.start();
                        int os = oNext.start();
                        int result = ms > os ? 1 : ms == os ? 0 : -1;
                        if (result == 0) {
                            // Sort containing in front of contained, same as
                            // SemanticRegions sort order
                            int me = myNext.end();
                            int oe = oNext.end();
                            result = me < oe ? 1 : me == oe ? 0 : 1;
                        }
                        return result;
                    } else {
                        return myNext.name().compareTo(oNext.name());
                    }
                }
            }
        }
    }

    /**
     * Get the top <i>n</i> most similar names in the collection using the
     * levenshtein distance algorithm; useful when offering hints to replace an
     * unknown reference to a source element.
     *
     * @param name The name
     * @param count The maximum number of results to return
     * @return A list of strings
     */
    public List<String> topSimilarNames(String name, int count) {
        return sortByDistanceTop(count, name, new ArrayList<>(this.allNames()));
    }

    /**
     * Get the top <code>count</code> names sorted by their string distance to
     * the passed string.
     *
     * @param count The maximum number of results to return
     * @param to The word to compare them to
     * @param l A list of strings
     * @return A sorted sublist of the passed list, which may be empty
     */
    private static List<String> sortByDistanceTop(int count, String to, List<String> l) {
        sortByDistance(to, l);
        return l.subList(0, Math.min(count, l.size()));
    }
}
