/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.folding;

import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.netbeans.api.editor.fold.FoldTemplate;
import org.netbeans.api.editor.fold.FoldType;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@Messages({"comment=Comment", "action=Action", "rule=Rule"})
public class GrammarFoldType {
    private static final String FOLDED_COMMENT = "/*...*/";
    public static final FoldType COMMENT_FOLD_TYPE = FoldType.create
                   ("comment"           ,
                    Bundle.comment()           ,
                    new FoldTemplate
                        (2             , // length of the guarded starting token
                         2             , // length of the guarded end token
                         FOLDED_COMMENT));
    private static final String FOLDED_ACTION = "{...}";
    public static final FoldType ACTION_FOLD_TYPE = FoldType.create
                   ("action"            ,
                    Bundle.action()            ,
                    new FoldTemplate
                        (1             , // length of the guarded starting token
                         1             , // length of the guarded end token
                         FOLDED_ACTION));
    private static final String FOLDED_RULE = "<rule>";
    public static final FoldType RULE_FOLD_TYPE = FoldType.create
                   ("rule"            ,
                    Bundle.rule()            ,
                    new FoldTemplate
                        (0             , // length of the guarded starting token
                         0             , // length of the guarded end token
                         FOLDED_RULE));

    public static FoldType forFoldableRegion(FoldableRegion region) {
        switch (region.kind) {
            case ACTION:
                return ACTION_FOLD_TYPE;
            case COMMENT:
            case DOC_COMMENT:
                return COMMENT_FOLD_TYPE;
            case RULE:
                return RULE_FOLD_TYPE;
            default:
                throw new AssertionError(region.kind);
        }
    }
}