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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.FRAGMENT_RULE_DECLARATION;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementKind.FRAGMENT_RULE_REFERENCE;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrFragmentHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, Extraction> {

    AntlrFragmentHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, findExtraction());
    }

    @Override
    protected void refresh(Document doc, Void argument, Extraction ext, ANTLRv4ParserResult result) {
        Map<RuleElementKind, AttributeSet> colorings = RuleElementKind.colorings();
        NamedSemanticRegions<RuleTypes> rns = ext.namedRegions(AntlrKeys.RULE_NAMES);
        NamedRegionReferenceSets<RuleTypes> rfs = ext.nameReferences(AntlrKeys.RULE_NAME_REFERENCES);
        Iterable<NamedSemanticRegion<RuleTypes>> iter = rns.combinedIterable(rfs, true);
        for (NamedSemanticRegion<RuleTypes> fragment : iter) {
            if (fragment.kind() == RuleTypes.FRAGMENT) {
                if (!fragment.isReference()) {
                    bag.addHighlight(fragment.start(), fragment.end(), colorings.get(FRAGMENT_RULE_DECLARATION));
                } else {
                    bag.addHighlight(fragment.start(), fragment.end(), colorings.get(FRAGMENT_RULE_REFERENCE));
                }
            }
        }
    }
}
