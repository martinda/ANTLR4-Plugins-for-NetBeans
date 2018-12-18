/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 *
 * @author Tim Boudreau
 */
interface Indexed<T> {

    int indexOf(Object o);

    T forIndex(int index);

    int size();

    static Indexed<String> forSortedStringArray(String... strings) {
        return new StringArrayIndexed(strings);
    }

    static Indexed<String> forStringSet(Set<String> set) {
        String[] sortedArray = new String[set.size()];
        sortedArray = set.toArray(sortedArray);
        if (!(set instanceof SortedSet<?>)) {
            Arrays.sort(sortedArray);
        }
        return new StringArrayIndexed(sortedArray);
    }

    static Indexed<String> forStringList(List<String> set) {
        String[] sortedArray = new String[set.size()];
        sortedArray = set.toArray(sortedArray);
        Arrays.sort(sortedArray);
        return new StringArrayIndexed(sortedArray);
    }

    static class StringArrayIndexed implements Indexed<String> {

        private final String[] sortedStrings;

        public StringArrayIndexed(String[] sortedStrings) {
            this.sortedStrings = sortedStrings;
        }

        @Override
        public int indexOf(Object o) {
            return Arrays.binarySearch(sortedStrings, Objects.toString(o, ""));
        }

        @Override
        public String forIndex(int index) {
            return sortedStrings[index];
        }

        @Override
        public int size() {
            return sortedStrings.length;
        }

    }

    static <T> Indexed<T> forList(List<T> list) {
        assert new HashSet<>(list).size() == list.size();
        return new Indexed<T>() {
            @Override
            public int indexOf(Object o) {
                return list.indexOf(o);
            }

            @Override
            public T forIndex(int index) {
                return list.get(index);
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }
}
