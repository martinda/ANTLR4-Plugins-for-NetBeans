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
package org.nemesis.extraction;

import java.util.Objects;
import org.nemesis.data.IndexAddressable;
import com.mastfrog.abstractions.Named;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.key.NamedExtractionKey;

/**
 *
 * @author Tim Boudreau
 */
public final class AttributedForeignNameReference<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> implements Named, IndexAddressable.IndexAddressableItem {

    private final UnknownNameReference<T> unk;
    private final R resolutionSource;
    private final I in;
    private final N element;
    private final Extraction extraction;
    private final Extraction target;
    private final NamedExtractionKey<T> key;

    AttributedForeignNameReference(NamedExtractionKey<T> key,
            UnknownNameReference<T> unk,
            R resolutionSource,
            I in,
            N element,
            Extraction extraction,
            Extraction target) {
        this.key = key;
        this.unk = unk;
        this.resolutionSource = resolutionSource;
        this.in = in;
        this.element = element;
        this.extraction = extraction;
        this.target = target;
    }

    public NamedExtractionKey<T> key() {
        return key;
    }

    public Extraction foundIn() {
        return extraction;
    }

    public Extraction attributedTo() {
        return target;
    }

    public T expectedKind() {
        return unk.expectedKind();
    }

    public boolean isTypeConflict() {
        T ek = expectedKind();
        T actualKind = element.kind();
        return ek != null && actualKind != null && ek != actualKind;
    }

    public R source() {
        return resolutionSource;
    }

    public I in() {
        return in;
    }

    public N element() {
        return element;
    }

    @Override
    public String name() {
        return unk.name();
    }

    @Override
    public int start() {
        return unk.start();
    }

    @Override
    public int end() {
        return unk.end();
    }

    @Override
    public int index() {
        return unk.index();
    }

    @Override
    public String toString() {
        return "for:" + element + "<<-" + resolutionSource;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.unk);
        hash = 79 * hash + Objects.hashCode(this.resolutionSource);
        hash = 79 * hash + Objects.hashCode(this.in);
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
        final AttributedForeignNameReference<?, ?, ?, ?> other = (AttributedForeignNameReference<?, ?, ?, ?>) obj;
        if (!Objects.equals(this.unk, other.unk)) {
            return false;
        }
        if (!Objects.equals(this.resolutionSource, other.resolutionSource)) {
            return false;
        }
        return Objects.equals(this.in, other.in);
    }

}
