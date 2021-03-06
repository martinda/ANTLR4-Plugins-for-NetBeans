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
package com.mastfrog.antlr.code.completion.spi;

import com.mastfrog.util.collections.IntList;
import java.util.function.BiConsumer;

/**
 * Interface which can provide completions given a token and caret position.
 *
 * @author Tim Boudreau
 */
public interface Completer {

    /**
     * Collect completion items; by default does nothing.
     *
     * @param parserRuleId The parser rule id constant from your antlr Parser
     * @param optionalPrefix The text before the caret position
     * @param maxResultsPerKey The target number of results
     * @param optionalSuffix The text trailing the caret position, which may be
     * whitespace or a token immediately following
     * @param names A simple consumer for simple completion items
     */
    default void namesForRule(int parserRuleId, String optionalPrefix,
            int maxResultsPerKey, String optionalSuffix,
            BiConsumer<String, Enum<?>> names) throws Exception {
    }

    /**
     * Collect completion items; by default delegates to the namesForRule()
     * variant which does not take a rule path.
     *
     * @param parserRuleId The parser rule id constant from your antlr Parser
     * @param optionalPrefix The text before the caret position
     * @param maxResultsPerKey The target number of results
     * @param optionalSuffix The text trailing the caret position, which may be
     * whitespace or a token immediately following
     * @param rulePath The path to the position where the prediction is
     * occurring
     * @param names A simple consumer for simple completion items
     */
    default void namesForRule(int parserRuleId, String optionalPrefix,
            int maxResultsPerKey, String optionalSuffix, IntList rulePath,
            BiConsumer<String, Enum<?>> names) throws Exception {
        namesForRule(parserRuleId, optionalPrefix, maxResultsPerKey,
                optionalSuffix, names);
    }

    /**
     * Collect completion items; by default delegates to namesForRule().
     *
     * @param parserRuleId The rule id predicated
     * @param token The token
     * @param maxResultsPerKey The target number of results
     * @param rulePath The path the rule prediction occurred in
     * @param addTo A CompletionItems to add to
     */
    default void apply(int parserRuleId, CaretToken token, int maxResultsPerKey,
            IntList rulePath, CompletionItems addTo) throws Exception {
        namesForRule(parserRuleId, token.leadingTokenText(), maxResultsPerKey,
                token.trailingTokenText(), rulePath, addTo::add);
    }

    /**
     * Each completion item from a completer can have a score relative to other
     * items it returned (for example, the Levenshtein distance of a string to
     * the one under the caret); a given completer may also produce more or less
     * important results than others, so the value returned here is used to
     * multiply the scores of all items produced by this completer after they
     * are normalized and before they are merged with those produced by other
     * completers, and sorted.
     *
     * @return A score, default 1 (1 = do nothing)
     */
    default float scoreMultiplier() {
        return 1;
    }
}
