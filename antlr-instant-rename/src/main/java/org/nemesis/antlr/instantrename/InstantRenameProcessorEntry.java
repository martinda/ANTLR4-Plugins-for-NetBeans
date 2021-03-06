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
package org.nemesis.antlr.instantrename;

import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.editor.BaseDocument;

interface InstantRenameProcessorEntry<T, X extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<? extends I>> {

    X key();

    RenameParticipant<T, X, I, C> participant();

    FindItemsResult<T, X, I, C> find(Extraction extraction, BaseDocument document, int caret, String identifier);
}
