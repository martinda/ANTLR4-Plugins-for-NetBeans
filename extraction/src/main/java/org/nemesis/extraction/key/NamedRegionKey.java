package org.nemesis.extraction.key;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.Serializable;
import java.util.Objects;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 * Key type for named regions, used in <code>Extraction.namedRegions()</code>.
 *
 * @author Tim Boudreau
 */
public final class NamedRegionKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T>, RegionKey<T> {

    private final String name;
    final Class<T> type;

    private NamedRegionKey(String name, Class<T> type) {
        this.name = checkName(notNull("name", name));
        this.type = type;
    }

    static String checkName(String name) {
        int max = name.length();
        for (int i = 0; i < max; i++) {
            switch(name.charAt(i)) {
                case '/':
                case '\\':
                case '"':
                case '\'':
                case ':':
                case ';':
                    throw new IllegalArgumentException("Key names may be used "
                            + "as file paths, and may not contain the characters"
                            + ": /'\":;");
            }
        }
        return name;
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(name);
        hasher.writeString(type.getName());
    }

    public static <T extends Enum<T>> NamedRegionKey<T> create(Class<T> type) {
        return new NamedRegionKey<>(type.getSimpleName(), type);
    }

    public static <T extends Enum<T>> NamedRegionKey<T> create(String name, Class<T> type) {
        return new NamedRegionKey<>(name, type);
    }

    public NameReferenceSetKey<T> createReferenceKey(String name) {
        return new NameReferenceSetKey<>(name, this);
    }

    @Override
    public String toString() {
        return name + ":" + type.getSimpleName();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.name);
        hash = 37 * hash + Objects.hashCode(this.type);
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
        final NamedRegionKey<?> other = (NamedRegionKey<?>) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.type, other.type);
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }
}
