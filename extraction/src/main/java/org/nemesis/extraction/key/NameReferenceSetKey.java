package org.nemesis.extraction.key;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.Serializable;
import org.nemesis.data.Hashable;
import static org.nemesis.extraction.key.NamedRegionKey.checkName;

/**
 * Key for reference sets.
 *
 * @author Tim Boudreau
 */
public final class NameReferenceSetKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T> {

    final String name;
    private final NamedRegionKey<T> orig;

    NameReferenceSetKey(String name, NamedRegionKey<T> orig) {
        assert orig != null : "Orig null";
        this.name = checkName(notNull("name", name));;
        this.orig = orig;
    }

    public NamedRegionKey<T> referencing() {
        return orig;
    }

    @Override
    public void hashInto(Hashable.Hasher hasher) {
        hasher.writeInt(720930);
        hasher.writeString(name);
        orig.hashInto(hasher);
    }

    @Override
    public String toString() {
        return name + "(" + name + "-" + orig + ")";
    }

    @Override
    public int hashCode() {
        return (37 * orig.hashCode()) + name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof NameReferenceSetKey<?>) {
            NameReferenceSetKey<?> nr = (NameReferenceSetKey<?>) obj;
            return name.equals(nr.name) && orig.equals(nr.orig);
        }
        return false;
    }

    @Override
    public Class<T> type() {
        return orig.type();
    }

    @Override
    public String name() {
        return name;
    }

}
