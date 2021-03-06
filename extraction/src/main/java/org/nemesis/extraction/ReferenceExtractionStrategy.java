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
package org.nemesis.extraction;

import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
final class ReferenceExtractionStrategy<R extends ParserRuleContext, T extends Enum<T>> implements Hashable {

    final Class<R> ruleType;
    final Function<R, NameAndOffsets> offsetsExtractor;
    final Predicate<RuleNode> ancestorQualifier;
    final T typeHint;

    ReferenceExtractionStrategy(Class<R> ruleType, Function<R, ?> offsetsExtractor, ExtractorReturnType rtype, Predicate<RuleNode> ancestorQualifier) {
        this(ruleType, offsetsExtractor, rtype, ancestorQualifier, null);
    }

    ReferenceExtractionStrategy(Class<R> ruleType, Function<R, ?> offsetsExtractor, ExtractorReturnType rtype, Predicate<RuleNode> ancestorQualifier, T typeHint) {
        this.ruleType = ruleType;
        this.typeHint = typeHint;
        this.offsetsExtractor = rtype.wrap(offsetsExtractor, typeHint);
        this.ancestorQualifier = ancestorQualifier;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append('(');
        sb.append(ruleType == null ? null : ruleType.getSimpleName());
        if (ancestorQualifier != null) {
            sb.append("q=").append(ancestorQualifier).append(", ");
        }
        if (typeHint != null) {
            sb.append("typeHint=").append(typeHint);
        }
        sb.append("extractor=").append(offsetsExtractor);
        return sb.append(')').toString();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(ruleType.getName());
        hasher.hashObject(offsetsExtractor);
        if (ancestorQualifier != null) {
            hasher.hashObject(ancestorQualifier);
        }
        if (typeHint != null) {
            hasher.writeInt(typeHint.ordinal());
        } else {
            hasher.writeInt(-1);
        }
    }

    enum ExtractorReturnType implements Hashable {
        NAME_AND_OFFSETS, PARSER_RULE_CONTEXT, TOKEN, TERMINAL_NODE;

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(ordinal());
        }

        <R extends ParserRuleContext, T extends Enum<T>> Function<R, NameAndOffsets> wrap(Function<R, ?> func, T typeHint) {
            return new HashableAndThen<>(this, func, typeHint);
        }

        static class HashableAndThen<R extends ParserRuleContext, T extends Enum<T>> implements Function<R, NameAndOffsets>, Hashable {

            private final ExtractorReturnType rt;
            private final Function<R, ?> func;
            private final T typeHint;

            HashableAndThen(ExtractorReturnType rt, Function<R, ?> func, T typeHint) {
                this.rt = rt;
                this.func = func;
                this.typeHint = typeHint;
            }

            public String toString() {
                return "HashableAndThen{" + typeHint + ", " + func + ", " + rt + "}";
            }

            @Override
            public NameAndOffsets apply(R t) {
                Object o = func.apply(t);
                return rt.apply(o, typeHint);
            }

            @Override
            public void hashInto(Hasher hasher) {
                hasher.writeInt(rt.ordinal());
                hasher.hashObject(func);
            }
        }

        public <T extends Enum<T>> NameAndOffsets apply(Object t, T typeHint) {
            if (t == null) {
                return null;
            }
            NameAndOffsets result = null;
            switch (this) {
                case NAME_AND_OFFSETS:
                    result = (NameAndOffsets) t;
                    break;
                case PARSER_RULE_CONTEXT:
                    ParserRuleContext c = (ParserRuleContext) t;
                    result = new NameAndOffsets(c.getText(), c.start.getStartIndex(), c.stop.getStopIndex() + 1);
                    break;
                case TOKEN:
                    Token tok = (Token) t;
                    result = new NameAndOffsets(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                    break;
                case TERMINAL_NODE:
                    TerminalNode tn = (TerminalNode) t;
                    tok = tn.getSymbol();
                    if (tok != null) {
                        result = new NameAndOffsets(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                    }
                    break;
                default:
                    throw new AssertionError(this);
            }
            if (result != null && typeHint != null) {
                result = new NamedRegionData<>(result.name, typeHint, result.start, result.end);
            }
            return result;
        }
    }

}
