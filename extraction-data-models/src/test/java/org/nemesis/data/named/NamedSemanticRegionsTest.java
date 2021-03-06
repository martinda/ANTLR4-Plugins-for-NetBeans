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

import static com.mastfrog.util.collections.CollectionUtils.setOf;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class NamedSemanticRegionsTest {

    static char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    static char[] chars2 = "ABCDEFGHIJKLMNOPQRSTUVWXY".toCharArray();
    String[] names;
    String[] names2;
    private Foo[] foos;
    private Foo[] foos2;

    enum Foo {
        FOO,
        BAR,
        BAZ
    }

    @Test
    public void testIndex() {
        NamedSemanticRegionsBuilder<TestRuleTypes> bldr = NamedSemanticRegions.builder(TestRuleTypes.class);
        bldr.add("CloseBrace", TestRuleTypes.LEXER, 464, 481);
        bldr.add("Colon", TestRuleTypes.LEXER, 495, 507);
        bldr.add("Comma", TestRuleTypes.LEXER, 451, 463);
        bldr.add("DIGIT", TestRuleTypes.FRAGMENT, 680, 703);
        bldr.add("Digits", TestRuleTypes.LEXER, 344, 360);
        bldr.add("ESC", TestRuleTypes.FRAGMENT, 813, 844);
        bldr.add("ESC2", TestRuleTypes.FRAGMENT, 845, 878);
        bldr.add("FALSE", TestRuleTypes.FRAGMENT, 579, 604);
        bldr.add("False", TestRuleTypes.LEXER, 521, 535);
        bldr.add("ID", TestRuleTypes.FRAGMENT, 738, 812);
        bldr.add("Identifier", TestRuleTypes.LEXER, 537, 553);
        bldr.add("Minus", TestRuleTypes.LEXER, 482, 494);
        bldr.add("Number", TestRuleTypes.LEXER, 320, 342);
        bldr.add("OpenBrace", TestRuleTypes.LEXER, 434, 450);
        bldr.add("STRING", TestRuleTypes.FRAGMENT, 605, 640);
        bldr.add("STRING2", TestRuleTypes.FRAGMENT, 641, 679);
        bldr.add("String", TestRuleTypes.LEXER, 363, 389);
        bldr.add("TRUE", TestRuleTypes.FRAGMENT, 555, 578);
        bldr.add("True", TestRuleTypes.LEXER, 508, 520);
        bldr.add("WHITESPACE", TestRuleTypes.FRAGMENT, 704, 737);
        bldr.add("Whitespace", TestRuleTypes.LEXER, 391, 432);
        bldr.add("booleanValue", TestRuleTypes.PARSER, 231, 265);
        bldr.add("items", TestRuleTypes.PARSER, 27, 58);
        bldr.add("map", TestRuleTypes.PARSER, 60, 115);
        bldr.add("mapItem", TestRuleTypes.PARSER, 117, 153);
        bldr.add("numberValue", TestRuleTypes.PARSER, 292, 317);
        bldr.add("stringValue", TestRuleTypes.PARSER, 266, 291);
        bldr.add("value", TestRuleTypes.PARSER, 155, 229);

        NamedSemanticRegions<TestRuleTypes> n = bldr.build();
        System.out.println("GOT " + n);

        NamedSemanticRegionPositionIndex<TestRuleTypes> index = n.index();
        assertNotNull(index.regionAt(36));
        assertEquals("items", index.regionAt(36).name());

        Set<String> filteredNames = n.collectNames(reg -> {
            return reg.name().startsWith("ST");
        });
        assertEquals(setOf("STRING", "STRING2"), filteredNames);
    }

    @Test
    public void testOddAndEvenLengths() {
        // Test both odd and even lengths, since off-by-one errors in things
        // like binary search tend to result in code that looks like it works
        // but has some corner case around odd or even counts or intervals
        testOffsets(names2, foos2, 5);
        testOffsets(names, foos, 5);
        testOffsets(names2, foos2, 6);
        testOffsets(names, foos, 6);
    }

    @Test
    public void testSingle() {
        NamedSemanticRegions<Foo> offsets = new NamedSemanticRegions<>(new String[]{"foo"}, new int[]{-1}, new int[]{-1}, new Foo[]{Foo.FOO}, 1);
        offsets.setOffsets("foo", 10, 20);
        assertEquals(0, offsets.indexOf("foo"));
        assertEquals(10, offsets.start("foo"));
        assertEquals(20, offsets.end("foo"));
        NamedSemanticRegionPositionIndex<Foo> ix = offsets.index();
        for (int i = 10; i < 20; i++) {
            NamedSemanticRegion<Foo> item = ix.regionAt(10);
            assertEquals("foo", item.name());
            assertEquals(10, item.start());
            assertEquals(20, item.end());
            assertEquals(19, item.stop());
            assertEquals(0, item.index());
        }
        for (int i = 0; i < 10; i++) {
            assertNull(ix.regionAt(i));
        }
        for (int i = 20; i < 30; i++) {
            assertNull(ix.regionAt(i));
        }
    }

    @Test
    public void testEmpty() {
        NamedSemanticRegions<Foo> offsets = new NamedSemanticRegions<>(new String[0], new Foo[0], 0);
        assertEquals(0, offsets.size());
        assertNull(offsets.index().regionAt(0));
        assertNull(offsets.index().regionAt(1));
        assertNull(offsets.index().regionAt(-1));
        assertNull(offsets.index().regionAt(Integer.MAX_VALUE));
        assertNull(offsets.index().regionAt(Integer.MIN_VALUE));
        assertFalse(offsets.contains(""));
        assertFalse(offsets.iterator().hasNext());
        assertEquals(-1, offsets.indexOf(""));
    }

    public void testOffsets(String[] names, Foo[] foos, int dist) {
        assert names.length == foos.length;
        names = Arrays.copyOf(names, names.length);

        NamedSemanticRegionsBuilder<Foo> bldr = NamedSemanticRegions.builder(Foo.class);
        for (int i = 0; i < names.length; i++) {
            assertNotNull(i + "", names[i]);
            assertNotNull(i + "", foos[i]);
            bldr.add(names[i], foos[i]);
        }
        NamedSemanticRegions<Foo> offsets = bldr.arrayBased().build();

//        NamedSemanticRegions<Foo> offsets = new NamedSemanticRegions<>(names, foos);
        assertEquals(names.length, offsets.size());
        int pos = 0;
        for (String name : names) {
            offsets.setOffsets(name, pos, pos + dist);
            pos += dist * 2;
            assertTrue(offsets.contains(name));
        }
        assertEquals(names.length, offsets.size());
        int ix = 0;
        for (NamedSemanticRegion<Foo> i : offsets) {
            assertEquals(ix, i.index());
            assertNotNull(i.kind());
            if (ix % 2 == 0) {
                assertEquals(Foo.FOO, i.kind());
            } else {
                assertEquals(Foo.BAR, i.kind());
            }
            assertEquals(names[ix++], i.name());
            assertTrue(i.contains(i.start()));
            assertTrue(i.contains(i.start() + 1));
            assertTrue(i.contains(i.end() - 1));
        }
        pos = 0;
        NamedSemanticRegionPositionIndex<Foo> index = offsets.index();
        for (int i = 0; i < names.length; i++) {
            int start = pos;
            int end = pos + dist;
            NamedSemanticRegion<Foo> it = index.withStart(start);
            assertNotNull(it);
            assertEquals(names[i], it.name());
            assertEquals(i, it.index());
            it = index.withEnd(end);
            assertNotNull(it);
            assertEquals(names[i], it.name());
            assertEquals(i, it.index());

            for (int j = start; j < end; j++) {
                it = index.regionAt(j);
                assertNotNull("Got null for offset " + j, it);
                assertEquals(names[i], it.name());
                assertEquals(i, it.index());
            }
            for (int j = 1; j < dist - 1; j++) {
                assertNull("" + (end + j), index.regionAt(end + j));
            }
            assertNull("" + (end + 4), index.regionAt(end + 4));
            assertNull("" + (end + 3), index.regionAt(end + 3));
            assertNull("" + (end + 2), index.regionAt(end + 2));
            assertNull("" + (end + 1), index.regionAt(end + 1));
            assertNull("-1", index.regionAt(-1));
            assertNull("" + Integer.MAX_VALUE, index.regionAt(Integer.MAX_VALUE));
            assertNull("" + Integer.MIN_VALUE, index.regionAt(Integer.MIN_VALUE));
            pos += dist * 2;
        }
        String[] toRemove = new String[]{"A", "C", "E", "G", "I", "K", "Y"};
        NamedSemanticRegions<Foo> sans = offsets.sans(toRemove);
        for (String s : toRemove) {
            assertFalse(s, sans.contains(s));
        }
        assertNotSame(offsets, sans);
        for (String n : names) {
            switch (n) {
                case "A":
                case "C":
                case "E":
                case "G":
                case "I":
                case "K":
                case "Y":
                    assertFalse(sans.contains(n));
                    break;
                default:
                    assertTrue(sans.contains(n));
                    assertEquals(offsets.start(n), sans.start(n));
                    assertEquals(offsets.start(n), sans.start(n));
                    assertEquals(offsets.end(n), sans.end(n));
            }
        }
        assertEquals(sans + "", (offsets.size() - toRemove.length), sans.size());

        int oldSize = sans.size();
        assertTrue(sans.contains("Q"));
        sans.remove("Q");
        assertEquals(oldSize - 1, sans.size());
        assertFalse(sans.contains("Q"));

        NamedSemanticRegions<Foo> withoutOffsets = offsets.secondary();
        assertEquals(new HashSet<>(Arrays.asList(names)), withoutOffsets.regionsWithUnsetOffsets());

        NamedRegionReferenceSetsBuilder<Foo> setsBuilder = offsets.newReferenceSetsBuilder();
        assertNotNull(setsBuilder);
        setsBuilder.addReference("B", 5, 6);
        setsBuilder.addReference("C", 7, 8);
        setsBuilder.addReference("B", 16, 17);
        setsBuilder.addReference("C", 92, 93);
        setsBuilder.addReference("N", 107, 108);

        NamedRegionReferenceSets<Foo> sets = setsBuilder.build();

        NamedRegionReferenceSet<Foo> s = sets.references("B");
        assertEquals(2, s.size());
        assertTrue(s.contains(5));
        assertTrue(s.contains(16));
        assertFalse(s.contains(0));
        assertFalse(s.contains(7));
        assertFalse(s.contains(8));

        assertFalse(s.contains(17));
        assertFalse(s.contains(6));

        assertNotNull("B", sets.itemAt(16));
        assertEquals("B", sets.itemAt(16).name());

        System.out.println("\n---------- SETS ----------------");
        System.out.println(sets);

        int ct = 0;
        for (NamedRegionReferenceSet<Foo> set : sets) {
            assertNotNull("Null set at " + ct + " in " + sets, set);
            String nm = set.name();
            if (set.size() == 0) {
                switch(nm) {
                    case "B":
                    case "C":
                    case "N":
                        fail("Got an empty set for " + nm);
                }
                continue;
            }
            System.out.println("\n ---------- SET " + nm + " -----------------------");
            for (NamedSemanticRegionReference<Foo> x : set) {
                System.out.println("  " + x);
            }
            assertNotNull(nm);
            Iterator<NamedSemanticRegionReference<Foo>> iter;
            iter = set.iterator();
            switch (nm) {
                case "B":
                    assertEquals(set.size(), 2);
                    for (int i = 0; i < set.size(); i++) {
                        NamedSemanticRegionReference<Foo> n = set.forIndex(i);
                        switch (i) {
                            case 0:
                                assertEquals(5, n.start());
                                assertEquals(6, n.end());
                                assertEquals(0, n.index());
                                break;
                            case 1:
                                assertEquals(16, n.start());
                                assertEquals(17, n.end());
                                assertEquals(1, n.index());
                                break;
                        }
                        assertEquals("B", n.name());
                        assertTrue(iter.hasNext());
                        NamedSemanticRegionReference<Foo> fromIter = iter.next();
                        assertEquals(n, fromIter);
                        assertEquals(n.name(), fromIter.name());
                        assertEquals(n.start(), fromIter.start());
                        assertEquals(n.end(), fromIter.end());
                        assertTrue(n.isReference());
                        assertFalse(n.isForeign());
                        assertEquals(i, set.indexOf(n));
                        int six = set.indexOf(n);
                        assertNotEquals(-1, six);
                    }
                    break;
                case "C":
                    assertEquals(set.size(), 2);
                    for (int i = 0; i < set.size(); i++) {
                        NamedSemanticRegionReference<Foo> n = set.forIndex(i);
                        switch (i) {
                            case 0:
                                assertEquals(7, n.start());
                                assertEquals(8, n.end());
                                assertEquals(0, n.index());
                                break;
                            case 1:
                                assertEquals(92, n.start());
                                assertEquals(93, n.end());
                                assertEquals(1, n.index());
                                break;
                        }
                        assertEquals("C", n.name());
                        assertTrue(iter.hasNext());
                        NamedSemanticRegionReference<Foo> fromIter = iter.next();
                        assertEquals(n, fromIter);
                        assertEquals(n.name(), fromIter.name());
                        assertEquals(n.start(), fromIter.start());
                        assertEquals(n.end(), fromIter.end());
                        assertTrue(n.isReference());
                        assertFalse(n.isForeign());
                        assertEquals(i, set.indexOf(n));
                        int six = set.indexOf(n);
                        assertNotEquals(-1, six);
                    }

                    break;
                case "N":
                    assertEquals(set.size(), 1);
                    assertTrue(set.iterator().hasNext());
                    NamedSemanticRegionReference<Foo> item = set.iterator().next();
                    assertEquals("N", item.name());
                    assertEquals(107, item.start());
                    assertEquals(108, item.end());
                    assertTrue(item.isReference());
                    assertFalse(item.isForeign());
                    assertEquals(0, set.indexOf(item));
                    break;
            }
            ct++;
        }
    }

    @Test
    public void testBuilderCreatesRegionsThatUseArrayWhenNeeded (){
        NamedSemanticRegionsBuilder b = new NamedSemanticRegionsBuilder(Stuff.class);
        b.add("hey", Stuff.A, 0, 3);
        NamedSemanticRegions<Stuff> nsr = b.build();
        assertTrue(nsr.areRegionSizesNameLengths());
        assertEquals(1, nsr.size());
        assertEquals("hey", nsr.iterator().next().name());
        assertEquals(0, nsr.iterator().next().start());
        assertEquals(3, nsr.iterator().next().end());
        b = new NamedSemanticRegionsBuilder(Stuff.class);
        b.add("hey", Stuff.A, 0, 13);
        nsr = b.build();
        assertFalse(nsr.areRegionSizesNameLengths());
        assertEquals(1, nsr.size());
        assertEquals("hey", nsr.iterator().next().name());
        assertEquals(0, nsr.iterator().next().start());
        assertEquals(13, nsr.iterator().next().end());
    }

    enum Stuff {
        A, B
    }

    @Before
    public void setup() {
        foos = new Foo[chars.length];
        names = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            names[i] = new String(new char[]{chars[i]});
            if (i % 2 == 0) {
                foos[i] = Foo.FOO;
            } else {
                foos[i] = Foo.BAR;
            }
        }
        foos2 = new Foo[chars2.length];
        names2 = new String[chars2.length];
        for (int i = 0; i < chars2.length; i++) {
            names2[i] = new String(new char[]{chars2[i]});
            if (i % 2 == 0) {
                foos2[i] = Foo.FOO;
            } else {
                foos2[i] = Foo.BAR;
            }
        }
    }
}
