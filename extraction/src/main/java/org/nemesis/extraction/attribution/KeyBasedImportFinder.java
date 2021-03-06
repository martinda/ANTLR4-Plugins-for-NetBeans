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
package org.nemesis.extraction.attribution;

import com.mastfrog.util.strings.Strings;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
final class KeyBasedImportFinder implements ImportFinder, ImportKeySupplier {

    private final NamedRegionKey<?>[] importKeys;

    KeyBasedImportFinder(NamedRegionKey<?>[] importKeys) {
        this.importKeys = importKeys;
    }

    @Override
    public NamedRegionKey<?>[] get() {
        return Arrays.copyOf(importKeys, importKeys.length);
    }

    public <T extends Enum<T>> void importsForKey(
            Set<? super GrammarSource<?>> result,
            NamedRegionKey<T> k,
            Extraction importer,
            Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
        NamedSemanticRegions<?> regions = importer.namedRegions(k);
        if (regions != null && !regions.isEmpty()) {
            for (NamedSemanticRegion<?> r : regions) {
                GrammarSource<?> src = importer
                        .source()
                        .resolveImport(r.name());
                if (src != null) {
                    result.add(src);
                } else {
                    notFound.add(r);
                }
            }
        }
    }

    @Override
    public Set<GrammarSource<?>> allImports(Extraction importer,
            Set<? super NamedSemanticRegion<? extends Enum<?>>> notFound) {
        Set<GrammarSource<?>> result = new LinkedHashSet<>();
        for (NamedRegionKey<?> k : importKeys) {
            importsForKey(result, k, importer, notFound);
        }
        return result;
    }

    @Override
    public String toString() {
        return "KeyBasedImportFinder(" + Strings.join(',', (Object[]) importKeys) + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Arrays.deepHashCode(this.importKeys);
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
        final KeyBasedImportFinder other = (KeyBasedImportFinder) obj;
        return Arrays.deepEquals(this.importKeys, other.importKeys);
    }
}
