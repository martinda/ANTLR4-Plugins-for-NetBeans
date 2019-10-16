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
package org.nemesis.antlr.navigator;

import javax.swing.ListModel;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyListModel<T> implements ListModel<T> {

    static final ListModel<Object> EMPTY_MODEL = new EmptyListModel<>();

    @SuppressWarnings("unchecked")
    static <T> ListModel<T> emptyModel() {
        return (ListModel<T>) EMPTY_MODEL;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public T getElementAt(int index) {
        throw new IndexOutOfBoundsException(index + "");
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        //do nothing
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        // do nothing
    }

}
