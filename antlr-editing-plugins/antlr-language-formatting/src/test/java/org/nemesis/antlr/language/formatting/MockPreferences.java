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
package org.nemesis.antlr.language.formatting;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 *
 * @author Tim Boudreau
 */
final class MockPreferences extends Preferences {

    private final Map<String, String> map = new HashMap<>();

    public static MockPreferences of(Object... pairs) {
        MockPreferences result = new MockPreferences();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] instanceof Enum<?>) {
                result.map.put(pairs[i].toString(), ((Enum<?>) pairs[i + 1]).ordinal() + "");
            } else {
                result.map.put(pairs[i].toString(), pairs[i + 1].toString());
            }
        }
        return result;
    }

    public String filename(String base, String ext) {
        StringBuilder sb = new StringBuilder(base);
        for (Iterator<String> it =new TreeSet<>(map.keySet()).iterator(); it.hasNext();) {
            String key = it.next();
            sb.append(key).append('-').append(map.get(key));
            if (it.hasNext()) {
                sb.append('_');
            }
        }
        return sb.append('.').append(ext).toString();
    }

    @Override
    public void put(String key, String value) {
        doPut(key, value);
    }

    @Override
    public String get(String key, String def) {
        return map.getOrDefault(key, def);
    }

    @Override
    public void remove(String key) {
        String old = map.remove(key);
        if (old != null) {
            PreferenceChangeEvent evt = new PreferenceChangeEvent(this, key, null);
            for (PreferenceChangeListener l : listeners) {
                l.preferenceChange(evt);
            }
        }
    }

    @Override
    public void clear() throws BackingStoreException {
        Map<String, String> old = new HashMap<>(map);
        map.clear();
        for (Map.Entry<String, String> e : old.entrySet()) {
            PreferenceChangeEvent evt = new PreferenceChangeEvent(this, e.getKey(), null);
            for (PreferenceChangeListener l : listeners) {
                l.preferenceChange(evt);
            }
        }
    }

    @Override
    public void putInt(String key, int value) {
        doPut(key, Integer.toString(value));
    }

    @Override
    public int getInt(String key, int def) {
        String s = map.get(key);
        return s == null ? def : Integer.parseInt(s);
    }

    @Override
    public void putLong(String key, long value) {
        doPut(key, Long.toString(value));
    }

    @Override
    public long getLong(String key, long def) {
        String s = map.get(key);
        return s == null ? def : Long.parseLong(s);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        doPut(key, Boolean.toString(value));
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        String s = map.get(key);
        return s == null ? def : "true".equals(s);
    }

    @Override
    public void putFloat(String key, float value) {
        doPut(key, Float.toString(value));
    }

    @Override
    public float getFloat(String key, float def) {
        String s = map.get(key);
        return s == null ? def : Float.parseFloat(s);
    }

    @Override
    public void putDouble(String key, double value) {
        doPut(key, Double.toString(value));
    }

    @Override
    public double getDouble(String key, double def) {
        String v = map.get(key);
        return v == null ? def : Double.parseDouble(v);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        doPut(key, Base64.getUrlEncoder().encodeToString(value));
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        String val = map.get(key);
        return val == null ? def : Base64.getUrlDecoder().decode(val);
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return map.keySet().toArray(new String[0]);
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        return new String[0];
    }

    @Override
    public Preferences parent() {
        return null;
    }

    @Override
    public Preferences node(String pathName) {
        return this;
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        return pathName.isEmpty();
    }

    @Override
    public void removeNode() throws BackingStoreException {
        clear();
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public String absolutePath() {
        return "";
    }

    @Override
    public boolean isUserNode() {
        return true;
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public void flush() throws BackingStoreException {
    }

    @Override
    public void sync() throws BackingStoreException {
    }

    private void doPut(String key, String val) {
        String old = map.put(key, val);
        if (!Objects.equals(old, val)) {
            PreferenceChangeEvent pce = new PreferenceChangeEvent(this, key, val);
            for (PreferenceChangeListener l : listeners) {
                l.preferenceChange(pce);
            }
        }
    }
    private final List<PreferenceChangeListener> listeners = new ArrayList<>();

    @Override
    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
        listeners.add(pcl);
    }

    @Override
    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
        listeners.remove(pcl);
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener ncl) {
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener ncl) {
    }

    @Override
    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    }

    @Override
    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    }

}
