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
package org.nemesis.antlr.language.formatting.config;

import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "NO_HANDLING=None",
    "INDENT=Indent",
    "ALIGN_WITH_PARENTHESES=Align With Parentheses"
})
public enum OrHandling {
    NO_HANDLING,
    INDENT,
    ALIGN_WITH_PARENTHESES;

    @Override
    public String toString() {
        return NbBundle.getMessage(OrHandling.class, name());
    }
}
