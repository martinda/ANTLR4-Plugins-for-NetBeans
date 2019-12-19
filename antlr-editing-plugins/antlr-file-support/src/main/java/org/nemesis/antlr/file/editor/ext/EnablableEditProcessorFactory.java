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

package org.nemesis.antlr.file.editor.ext;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
abstract class EnablableEditProcessorFactory<T> implements EditProcessorFactory<T> {

    protected final Class<?> ownerType;
    protected final String name;
    protected final String description;
    private Preferences prefs;
    protected final String category;

    protected EnablableEditProcessorFactory(Class<?> ownerType, String name, String description, String category) {
        this.ownerType = ownerType;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    private Preferences prefs() {
        if (prefs == null) {
            prefs = NbPreferences.forModule(ownerType);
        }
        return prefs;
    }

    public final boolean isEnabled() {
        return prefs().getBoolean("ef-" + id(), true);
    }

    public final void setEnabled(boolean enabled) {
        prefs().putBoolean("ef-" + id(), enabled);
    }

    public final String category() {
        return category;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    protected abstract String id();

}
