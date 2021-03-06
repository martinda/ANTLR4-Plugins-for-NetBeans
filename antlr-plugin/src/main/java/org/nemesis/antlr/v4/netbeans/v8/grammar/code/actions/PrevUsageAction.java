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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.actions;

import org.netbeans.api.editor.EditorActionRegistration;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({"prev-usage=Next Usage"})
@EditorActionRegistration(
        name = "prev-usage",
        weight = Integer.MAX_VALUE,
        category = "Editing",
        popupText = "Prev Usage",
        menuPath = "Source",
        menuText = "Prev Usage",
        mimeType = "text/g-4")
public class PrevUsageAction extends NextUsageAction {

    public PrevUsageAction() {
        super(false);
    }
}
