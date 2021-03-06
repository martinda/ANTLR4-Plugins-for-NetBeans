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

import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.ZOrder;

final class HighlightsLayerFactoryImpl implements HighlightsLayerFactory {
    
    public HighlightsLayer[] createLayers(Context context) {
        return new HighlightsLayer[] {
            //"above" mark occurrences, "below" search layers:
            HighlightsLayer.create(InstantRenamePerformer.class.getName(), ZOrder.CARET_RACK.forPosition(75), false, InstantRenamePerformer.getHighlightsBag(context.getDocument())),
        };
    }

}
