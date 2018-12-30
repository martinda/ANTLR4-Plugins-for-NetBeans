package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.IOException;
import java.io.Serializable;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.ExtractionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.IndexAddressable.NamedIndexAddressable;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameAndOffsets;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameExtractorInfo;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionData;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NamedRegionKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.ReferenceExtractorInfo;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.ReferenceExtractorInfo.ExtractorReturnType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.ReferenceExtractorPair;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.UnknownNameReference;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedRegionReferenceSetsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegionsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegionsBuilder;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath;
import org.nemesis.antlr.v4.netbeans.v8.util.reflection.ReflectionPath.ResolutionContext;

/**
 *
 * @author Tim Boudreau
 */
public final class NamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

    private final Class<T> keyType;
    private NamedRegionKey<T> namePositionKey;
    private NamedRegionKey<T> ruleRegionKey;
    private Set<NameExtractorInfo<?, T>> nameExtractors = new HashSet<>();
    private final Set<ReferenceExtractorPair<T>> referenceExtractors = new HashSet<>();
    private final Function<NameExtractors, Ret> buildFunction;
    private final ResolutionContext ctx = new ResolutionContext();

    NamedRegionExtractorBuilder(Class<T> keyType, Function<NameExtractors, Ret> buildFunction) {
        this.keyType = keyType;
        this.buildFunction = buildFunction;
    }

    public NamedRegionExtractorBuilderWithNameKey<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type;
        namePositionKey = key;
        return new NamedRegionExtractorBuilderWithNameKey<>(this);
    }

    public NamedRegionExtractorBuilderWithRuleKey<T, Ret> recordingRuleRegionUnder(NamedRegionKey<T> key) {
        assert keyType == key.type;
        ruleRegionKey = key;
        return new NamedRegionExtractorBuilderWithRuleKey<>(this);
    }

    void addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
        this.referenceExtractors.add(pair);
    }

    Ret _build() {
        return buildFunction.apply(new NameExtractors<>(keyType, namePositionKey, ruleRegionKey, nameExtractors, referenceExtractors));
    }

    static final class NamedRegionExtractorBuilderWithNameKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        public NamedRegionExtractorBuilderWithNameKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingRulePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type;
            bldr.ruleRegionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    static final class NamedRegionExtractorBuilderWithRuleKey<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithRuleKey(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NamedRegionExtractorBuilderWithBothKeys<T, Ret> recordingNamePositionUnder(NamedRegionKey<T> key) {
            assert bldr.keyType == key.type;
            bldr.namePositionKey = key;
            return new NamedRegionExtractorBuilderWithBothKeys<>(bldr);
        }
    }

    static final class NamedRegionExtractorBuilderWithBothKeys<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        NamedRegionExtractorBuilderWithBothKeys(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }
    }

    private static <R extends ParserRuleContext, T extends Enum<T>> Function<R, NamedRegionData<T>> func(T kind, ReflectionPath<?> path, ResolutionContext ctx) {
        return rule -> {
            ReflectionPath.ResolutionResult rr = ctx.getResult(path, rule);
            if (rr.thrown() != null) {
                Logger.getLogger(NamedRegionExtractorBuilder.class.getName()).log(Level.WARNING,
                        "Exception invoking " + path + " reflectively on " + rule, rr.thrown());
                return null;
            }
            Object result = rr.value();
            if (result == null) {
                return null;
            } else if (result instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) result;
                return new NamedRegionData<T>(prc.getText(), kind, prc.getStart().getStartIndex(), prc.getStop().getStopIndex() + 1);
            } else if (result instanceof TerminalNode) {
                TerminalNode tn = (TerminalNode) result;
                return new NamedRegionData<T>(tn.getText(), kind, tn.getSymbol().getStartIndex(), tn.getSymbol().getStopIndex() + 1);
            } else if (result instanceof Token) {
                Token tok = (Token) result;
                return new NamedRegionData<T>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            } else {
                throw new IllegalStateException("Don't know how to convert " + result.getClass().getName()
                        + " to a NamedRegionData");
            }
        };
    }

    public static final class NameExtractorBuilder<R extends ParserRuleContext, T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;
        private final Class<R> type;
        private Predicate<RuleNode> qualifiers;

        NameExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr, Class<R> type) {
            this.bldr = bldr;
            this.type = type;
        }

        public NameExtractorBuilder<R, T, Ret> whenInAncestorRule(Class<? extends RuleNode> qualifyingAncestorType) {
            return whenAncestorMatches(new QualifierPredicate(qualifyingAncestorType));
        }

        public NameExtractorBuilder<R, T, Ret> whenAncestorMatches(Predicate<RuleNode> ancestorTest) {
            if (qualifiers == null) {
                qualifiers = ancestorTest;
            } else {
                qualifiers = qualifiers.or(ancestorTest);
            }
            return this;
        }

        /**
         * Pass a reflection path, such as
         *
         * @param reflectionPath
         * @param kind
         * @see ReflectionPath
         * @return
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(String reflectionPath, T kind) {
            return derivingNameWith(func(kind, new ReflectionPath<Object>(reflectionPath, Object.class), bldr.ctx));
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameWith(Function<R, NamedRegionData<T>> extractor) {
//            bldr.nameExtractors.add(new NameExtractorInfo<R, T>(type, extractor, qualifiers);
            NameExtractorInfo<R, T> info = new NameExtractorInfo<>(type, extractor, qualifiers);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTokenWith(T kind, Function<R, Token> extractor) {
            return derivingNameWith(rule -> {
                Token tok = extractor.apply(rule);
                if (tok == null) {
                    return null;
                }
                return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
            });
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodeWith(T kind, Function<R, TerminalNode> extractor) {
            return derivingNameWith(rule -> {
                TerminalNode tn = extractor.apply(rule);
                if (tn != null) {
                    Token tok = tn.getSymbol();
                    if (tok != null) {
                        return new NamedRegionData<>(tok.getText(), kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                    }
                }
                return null;
            });
        }

        /**
         * Derive names from a terminal node list, which some kinds of rule can
         * return if there is no parser rule for the name definition itself. In
         * this case, the offset for both the name and bounds will be set to the
         * bounds of the terminal node, and the terminal node's
         * getSymbol().getText() value is the name.
         *
         * @param argType The type to use for all returned names
         * @param extractor A function which finds the list of terminal nodes in
         * the rule
         * @return a builder which can be finished
         */
        public FinishableNamedRegionExtractorBuilder<T, Ret> derivingNameFromTerminalNodes(T argType, Function<R, List<? extends TerminalNode>> extractor) {
            NameExtractorInfo<R, T> info = new NameExtractorInfo<>(type, qualifiers, argType, extractor);
            bldr.nameExtractors.add(info);
            return new FinishableNamedRegionExtractorBuilder<>(bldr);
        }
    }

    static final class QualifierPredicate implements Predicate<RuleNode>, Hashable {

        private final Class<? extends RuleNode> qualifyingType;

        QualifierPredicate(Class<? extends RuleNode> qualifyingType) {
            this.qualifyingType = qualifyingType;
        }

        @Override
        public boolean test(RuleNode t) {
            return qualifyingType.isInstance(t);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString("QP");
            hasher.writeString(qualifyingType.getName());
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.qualifyingType);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final QualifierPredicate other = (QualifierPredicate) obj;
            if (!Objects.equals(this.qualifyingType, other.qualifyingType)) {
                return false;
            }
            return true;
        }
    }

    static final class NameExtractorInfo<R extends ParserRuleContext, T extends Enum<T>> implements Hashable {

        private final Class<R> type;
        private final Function<R, NamedRegionData<T>> extractor;
        private final Predicate<RuleNode> ancestorQualifier;
        private final T argType;
        private final Function<R, List<? extends TerminalNode>> terminalFinder;

        NameExtractorInfo(Class<R> type, Function<R, NamedRegionData<T>> extractor, Predicate<RuleNode> ancestorQualifier) {
            this.type = type;
            this.extractor = extractor;
            this.ancestorQualifier = ancestorQualifier;
            this.terminalFinder = null;
            this.argType = null;
        }

        NameExtractorInfo(Class<R> type, Predicate<RuleNode> ancestorQualifier, T argType, Function<R, List<? extends TerminalNode>> terminalFinder) {
            this.type = type;
            this.extractor = null;
            this.ancestorQualifier = ancestorQualifier;
            this.argType = argType;
            this.terminalFinder = terminalFinder;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(type.getName());
            hasher.hashObject(extractor);
            if (ancestorQualifier != null) {
                hasher.hashObject(ancestorQualifier);
            }
            if (argType != null) {
                hasher.writeInt(argType.ordinal());
            }
            if (terminalFinder != null) {
                hasher.hashObject(terminalFinder);
            }
        }

        public void find(R node, BiConsumer<NamedRegionData<T>, TerminalNode> cons) {
            if (extractor != null) {
                cons.accept(extractor.apply(node), null);
            } else {
                List<? extends TerminalNode> nds = terminalFinder.apply(node);
                if (nds != null) {
                    for (TerminalNode tn : nds) {
                        Token tok = tn.getSymbol();
                        if (tok != null) {
                            cons.accept(new NamedRegionData<>(tn.getText(), argType, tok.getStartIndex(), tok.getStopIndex() + 1), tn);
                        }
                    }
                }
            }
        }
    }

    public static final class FinishableNamedRegionExtractorBuilder<T extends Enum<T>, Ret> {

        private final NamedRegionExtractorBuilder<T, Ret> bldr;

        FinishableNamedRegionExtractorBuilder(NamedRegionExtractorBuilder<T, Ret> bldr) {
            this.bldr = bldr;
        }

        public <R extends ParserRuleContext> NameExtractorBuilder<R, T, Ret> whereRuleIs(Class<R> type) {
            return new NameExtractorBuilder<>(bldr, type);
        }

        public NameReferenceCollectorBuilder<T, Ret> collectingReferencesUnder(NameReferenceSetKey<T> refSetKey) {
            return new NameReferenceCollectorBuilder<>(this, refSetKey);
        }

        FinishableNamedRegionExtractorBuilder<T, Ret> addReferenceExtractorPair(ReferenceExtractorPair<T> pair) {
            bldr.addReferenceExtractorPair(pair);
            return this;
        }

        public Ret finishNamedRegions() {
            return bldr._build();
        }
    }

    public static final class NameReferenceCollectorBuilder<T extends Enum<T>, Ret> {

        private final FinishableNamedRegionExtractorBuilder<T, Ret> bldr;
        private final NameReferenceSetKey<T> refSetKey;
        private final Set<ReferenceExtractorInfo<?, ?>> referenceExtractors = new HashSet<>();

        public NameReferenceCollectorBuilder(FinishableNamedRegionExtractorBuilder<T, Ret> bldr, NameReferenceSetKey<T> refSetKey) {
            this.bldr = bldr;
            this.refSetKey = refSetKey;
        }

        public <R extends ParserRuleContext> ReferenceExtractorBuilder<R, T, Ret> whereReferenceContainingRuleIs(Class<R> ruleType) {
            return new ReferenceExtractorBuilder<>(this, ruleType);
        }

        public FinishableNamedRegionExtractorBuilder<T, Ret> finishReferenceCollector() {
            ReferenceExtractorPair<T> extractorPair = new ReferenceExtractorPair<>(referenceExtractors, refSetKey);
            bldr.addReferenceExtractorPair(extractorPair);
            return bldr;
        }
    }

    static class ReferenceExtractorPair<T extends Enum<T>> implements Hashable {

        private final Set<ReferenceExtractorInfo<?, ?>> referenceExtractors;
        private final NameReferenceSetKey<T> refSetKey;

        ReferenceExtractorPair(Set<ReferenceExtractorInfo<?, ?>> referenceExtractors, NameReferenceSetKey<T> refSetKey) {
            this.refSetKey = refSetKey;
            this.referenceExtractors = new HashSet<>(referenceExtractors);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(refSetKey);
            for (ReferenceExtractorInfo<?, ?> e : referenceExtractors) {
                hasher.hashObject(e);
            }
        }
    }

    public static final class ReferenceExtractorBuilder<R extends ParserRuleContext, T extends Enum<T>, Ret> {

        private final NameReferenceCollectorBuilder<T, Ret> bldr;
        private final Class<R> ruleType;
        private Predicate<RuleNode> ancestorQualifier;

        ReferenceExtractorBuilder(NameReferenceCollectorBuilder<T, Ret> bldr, Class<R> ruleType) {
            this.bldr = bldr;
            this.ruleType = ruleType;
        }

        public <A extends RuleNode> ReferenceExtractorBuilder<R, T, Ret> whenAncestorRuleOf(Class<A> ancestorRuleType) {
            if (ancestorQualifier == null) {
                ancestorQualifier = new QualifierPredicate(ancestorRuleType);
            } else {
                ancestorQualifier = ancestorQualifier.or(new QualifierPredicate(ancestorRuleType));
            }
            return this;
        }

        private NameReferenceCollectorBuilder<T, Ret> finish(ReferenceExtractorInfo<R, T> info) {
            bldr.referenceExtractors.add(info);
            return bldr;
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsExplicitlyWith(Function<R, NameAndOffsets> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.NAME_AND_OFFSETS, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromRuleWith(Function<R, ParserRuleContext> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.PARSER_RULE_CONTEXT, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTokenWith(Function<R, Token> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TOKEN, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTerminalNodeWith(Function<R, TerminalNode> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TERMINAL_NODE, ancestorQualifier));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsExplicitlyWith(T typeHint, Function<R, NameAndOffsets> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.NAME_AND_OFFSETS, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromRuleWith(T typeHint, Function<R, ParserRuleContext> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.PARSER_RULE_CONTEXT, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTokenWith(T typeHint, Function<R, Token> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TOKEN, ancestorQualifier, typeHint));
        }

        public NameReferenceCollectorBuilder<T, Ret> derivingReferenceOffsetsFromTerminalNodeWith(T typeHint, Function<R, TerminalNode> offsetsExtractor) {
            return finish(new ReferenceExtractorInfo<>(ruleType, offsetsExtractor, ExtractorReturnType.TERMINAL_NODE, ancestorQualifier, typeHint));
        }

    }

    public static class NameAndOffsets {

        final String name;
        final int start;
        final int end;

        NameAndOffsets(String name, int start, int end) {
            assert name != null;
            assert start >= 0;
            assert end >= 0;
            this.name = name;
            this.start = start;
            this.end = end;
        }

        public static NameAndOffsets create(String name, int start, int end) {
            return new NameAndOffsets(name, start, end);
        }

        public String toString() {
            return name + "@" + start + ":" + end;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + Objects.hashCode(this.name);
            hash = 41 * hash + this.start;
            hash = 41 * hash + this.end;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameAndOffsets other = (NameAndOffsets) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }
    }

    public static class NamedRegionData<T extends Enum<T>> extends NameAndOffsets {

        final T kind;

        NamedRegionData(String string, T kind, int start, int end) {
            super(string, start, end);
            this.kind = kind;
        }

        public static <T extends Enum<T>> NamedRegionData<T> create(String name, T kind, int start, int end) {
            return new NamedRegionData<>(name, kind, start, end);
        }

        @Override
        public String toString() {
            return name + ":" + kind + ":" + start + ":" + end;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NamedRegionData<?> other = (NamedRegionData<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }
    }

    static final class ReferenceExtractorInfo<R extends ParserRuleContext, T extends Enum<T>> implements Hashable {

        final Class<R> ruleType;
        final Function<R, NameAndOffsets> offsetsExtractor;
        private final Predicate<RuleNode> ancestorQualifier;
        final T typeHint;

        public ReferenceExtractorInfo(Class<R> ruleType, Function<R, ?> offsetsExtractor, ExtractorReturnType rtype, Predicate<RuleNode> ancestorQualifier) {
            this(ruleType, offsetsExtractor, rtype, ancestorQualifier, null);
        }

        public ReferenceExtractorInfo(Class<R> ruleType, Function<R, ?> offsetsExtractor, ExtractorReturnType rtype, Predicate<RuleNode> ancestorQualifier, T typeHint) {
            this.ruleType = ruleType;
            this.typeHint = typeHint;
            this.offsetsExtractor = rtype.wrap(offsetsExtractor, typeHint);
            this.ancestorQualifier = ancestorQualifier;
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
            NAME_AND_OFFSETS,
            PARSER_RULE_CONTEXT,
            TOKEN,
            TERMINAL_NODE;

            @Override
            public void hashInto(Hasher hasher) {
                hasher.writeInt(ordinal());
            }

            <R extends ParserRuleContext, T extends Enum<T>> Function<R, NameAndOffsets> wrap(Function<R, ?> func, T typeHint) {
                return new HashableAndThen(this, func, typeHint);
            }

            static class HashableAndThen<R extends ParserRuleContext, T extends Enum<T>> implements Function<R, NameAndOffsets>, Hashable {

                private final ExtractorReturnType rt;
                private final Function<R, ?> func;
                private final T typeHint;

                public HashableAndThen(ExtractorReturnType rt, Function<R, ?> func, T typeHint) {
                    this.rt = rt;
                    this.func = func;
                    this.typeHint = typeHint;
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

    /**
     * Marker interface for extractor methods which take a heterogenous set of
     * keys all of which must return named things.
     *
     * @param <T>
     */
    public interface NamedExtractionKey<T extends Enum<T>> extends ExtractionKey<T> {

    }

    public static final class NamedRegionKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T> {

        private final String name;
        private final Class<T> type;

        private NamedRegionKey(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(name);
            hasher.writeString(type.getName());
        }

        public static <T extends Enum<T>> NamedRegionKey create(Class<T> type) {
            return new NamedRegionKey(type.getSimpleName(), type);
        }

        public static <T extends Enum<T>> NamedRegionKey create(String name, Class<T> type) {
            return new NamedRegionKey(name, type);
        }

        public String toString() {
            return name + ":" + type.getSimpleName();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NamedRegionKey<?> other = (NamedRegionKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }
    }

    public static final class NameReferenceSetKey<T extends Enum<T>> implements Serializable, Hashable, NamedExtractionKey<T> {

        private final String name;
        private final Class<T> type;

        private NameReferenceSetKey(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public void hashInto(Hashable.Hasher hasher) {
            hasher.writeString(name);
            hasher.writeString(type.getName());
        }

        public static final <T extends Enum<T>> NameReferenceSetKey<T> create(Class<T> type) {
            return new NameReferenceSetKey(type.getSimpleName(), type);
        }

        public static final <T extends Enum<T>> NameReferenceSetKey<T> create(String name, Class<T> type) {
            return new NameReferenceSetKey(name, type);
        }

        public String toString() {
            return name + "(" + type.getName() + ")";
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameReferenceSetKey<?> other = (NameReferenceSetKey<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }
    }

    public interface UnknownNameReference<T extends Enum<T>> extends Named, IndexAddressable.IndexAddressableItem, Serializable {

        T expectedKind();

        public interface UnknownNameReferenceResolver<R, I extends NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> {

            <X> X resolve(Extraction extraction, UnknownNameReference ref, ResolutionConsumer<R, I, N, T, X> c) throws IOException;
        }

        public interface ResolutionConsumer<R, I extends NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>, X> {

            X resolved(UnknownNameReference unknown, R resolutionSource, I in, N element);
        }

        default <R, I extends NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> ResolvedForeignNameReference<R, I, N, T> resolve(Extraction extraction, UnknownNameReferenceResolver<R, I, N, T> resolver) throws IOException {
            return resolver.resolve(extraction, this, (u, src, in, element) -> {
                return new ResolvedForeignNameReference<R, I, N, T>(u, src, in, element, extraction);
            });
        }
    }

    public static final class ResolvedForeignNameReference<R, I extends NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> implements Named, IndexAddressable.IndexAddressableItem {

        private final UnknownNameReference<T> unk;
        private final R resolutionSource;
        private final I in;
        private final N element;
        private final Extraction extraction;

        private ResolvedForeignNameReference(UnknownNameReference<T> unk, R resolutionSource, I in, N element, Extraction extraction) {
            this.unk = unk;
            this.resolutionSource = resolutionSource;
            this.in = in;
            this.element = element;
            this.extraction = extraction;
        }

        public Extraction from() {
            return extraction;
        }

        public T expectedKind() {
            return unk.expectedKind();
        }

        public boolean isTypeConflict() {
            T ek = expectedKind();
            T actualKind = element.kind();
            return ek != null && actualKind != null && ek != actualKind;
        }

        public R source() {
            return resolutionSource;
        }

        public I in() {
            return in;
        }

        public N element() {
            return element;
        }

        @Override
        public String name() {
            return unk.name();
        }

        @Override
        public int start() {
            return unk.start();
        }

        @Override
        public int end() {
            return unk.end();
        }

        @Override
        public int index() {
            return unk.index();
        }

        @Override
        public String toString() {
            return "for:" + element + "<<-" + resolutionSource;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(this.unk);
            hash = 79 * hash + Objects.hashCode(this.resolutionSource);
            hash = 79 * hash + Objects.hashCode(this.in);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ResolvedForeignNameReference<?, ?, ?, ?> other = (ResolvedForeignNameReference<?, ?, ?, ?>) obj;
            if (!Objects.equals(this.unk, other.unk)) {
                return false;
            }
            if (!Objects.equals(this.resolutionSource, other.resolutionSource)) {
                return false;
            }
            if (!Objects.equals(this.in, other.in)) {
                return false;
            }
            return true;
        }

    }

    static final class UnknownNameReferenceImpl<T extends Enum<T>> implements UnknownNameReference<T> {

        private final T expectedKind;

        private final int start;
        private final int end;
        private final String name;
        private final int index;

        public UnknownNameReferenceImpl(T expectedKind, int start, int end, String name, int index) {
            this.expectedKind = expectedKind;
            this.start = start;
            this.end = end;
            this.name = name;
            this.index = index;
        }

        public String name() {
            return name;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public boolean isReference() {
            return true;
        }

        public int index() {
            return index;
        }

        @Override
        public String toString() {
            return name + "@" + start + ":" + end;
        }

        @Override
        public int hashCode() {
            return start + (end * 73) + 7 * Objects.hashCode(name);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof UnknownNameReference) {
                UnknownNameReference u = (UnknownNameReference) o;
                return u.start() == start() && u.end() == end && Objects.equals(name(), u.name());
            }
            return false;
        }

        @Override
        public T expectedKind() {
            return expectedKind;
        }
    }

    static class NameExtractors<T extends Enum<T>> implements Hashable {

        private final Class<T> keyType;
        private final NamedRegionKey<T> namePositionKey;
        private final NamedRegionKey<T> ruleRegionKey;
        private final NameExtractorInfo<?, T>[] nameExtractors;
        private final ReferenceExtractorPair<?>[] referenceExtractors;

        public NameExtractors(Class<T> keyType, NamedRegionKey<T> namePositionKey, NamedRegionKey<T> ruleRegionKey, Set<NameExtractorInfo<?, T>> nameExtractors, Set<ReferenceExtractorPair<T>> referenceExtractors) {
            this.keyType = keyType;
            assert namePositionKey != null || ruleRegionKey != null;
            this.namePositionKey = namePositionKey;
            this.ruleRegionKey = ruleRegionKey;
            this.nameExtractors = nameExtractors.toArray((NameExtractorInfo<?, T>[]) new NameExtractorInfo<?, ?>[nameExtractors.size()]);
            this.referenceExtractors = referenceExtractors.toArray(new ReferenceExtractorPair<?>[referenceExtractors.size()]);
        }

        void invoke(ParserRuleContext ctx, NameInfoStore store) {
            RuleNameAndBoundsVisitor v = new RuleNameAndBoundsVisitor();
            ctx.accept(v);
            NamedSemanticRegions<T> names = v.namesBuilder == null ? null : v.namesBuilder.build();
            NamedSemanticRegions<T> ruleBounds = v.ruleBoundsBuilder.build();

            if (namePositionKey != null) {
                store.addNamedRegions(namePositionKey, names);
            }
            if (ruleRegionKey != null) {
                store.addNamedRegions(ruleRegionKey, ruleBounds);
            }
            if (v.namesBuilder != null) {
                v.namesBuilder.retrieveDuplicates((name, duplicates) -> {
                    store.addDuplicateNamedRegions(namePositionKey, name, duplicates);
                });
            }
            if (v.ruleBoundsBuilder != null) {
                v.ruleBoundsBuilder.retrieveDuplicates((name, duplicates) -> {
                    store.addDuplicateNamedRegions(ruleRegionKey, name, duplicates);
                });
            }

            ReferenceExtractorVisitor v1 = new ReferenceExtractorVisitor(ruleBounds);
            ctx.accept(v1);
            v1.conclude(store);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(keyType.getName());
            hasher.hashObject(namePositionKey);
            hasher.hashObject(ruleRegionKey);
            for (NameExtractorInfo<?, ?> ne : nameExtractors) {
                hasher.hashObject(ne);
            }
            for (ReferenceExtractorPair<?> p : referenceExtractors) {
                hasher.hashObject(p);
            }
        }

        interface NameInfoStore {

            <T extends Enum<T>> void addNamedRegions(NamedRegionKey<T> key, NamedSemanticRegions<T> regions);

            <T extends Enum<T>> void addReferences(NameReferenceSetKey<T> key, NamedSemanticRegions.NamedRegionReferenceSets<T> regions);

            <T extends Enum<T>> void addReferenceGraph(NameReferenceSetKey<T> refSetKey, BitSetStringGraph stringGraph);

            <T extends Enum<T>> void addUnknownReferences(NameReferenceSetKey<T> refSetKey, SemanticRegions<UnknownNameReference<T>> build);

            <T extends Enum<T>> void addDuplicateNamedRegions(NamedRegionKey<T> key, String name, Iterable<? extends NamedSemanticRegion<T>> duplicates);
        }

        class ReferenceExtractorVisitor extends AbstractParseTreeVisitor<Void> {

            int[] lengths;
            int[][] activations;
            int unknownCount = 0;
//            Set<NameAndOffsets> unknown = new HashSet<>();
            SemanticRegionsBuilder<UnknownNameReference<T>> unknown = SemanticRegions.builder(UnknownNameReference.class);

            ReferenceExtractorInfo<?, ?>[][] infos;
            private final NamedSemanticRegions<T> regions;
            NamedSemanticRegions.NamedRegionReferenceSetsBuilder<T>[] refs;
            private final BitSet[][] references, reverseReferences;

            ReferenceExtractorVisitor(NamedSemanticRegions<T> regions) {
                activations = new int[referenceExtractors.length][];
                lengths = new int[referenceExtractors.length];
                references = new BitSet[referenceExtractors.length][regions.size()];
                reverseReferences = new BitSet[referenceExtractors.length][regions.size()];

                infos = new ReferenceExtractorInfo[referenceExtractors.length][];
                refs = (NamedRegionReferenceSetsBuilder<T>[]) new NamedRegionReferenceSetsBuilder<?>[referenceExtractors.length];

                for (int i = 0; i < referenceExtractors.length; i++) {
                    ReferenceExtractorInfo[] ex = referenceExtractors[i].referenceExtractors.toArray(new ReferenceExtractorInfo[referenceExtractors[i].referenceExtractors.size()]);
                    lengths[i] = ex.length;
                    infos[i] = ex;
                    activations[i] = new int[ex.length];
                    refs[i] = regions.newReferenceSetsBuilder();
                    for (int j = 0; j < ex.length; j++) {
                        if (ex[i].ancestorQualifier == null) {
                            activations[i][j] = 1;
                        }
                    }
                    for (int j = 0; j < regions.size(); j++) {
                        references[i][j] = new BitSet(regions.size());
                        reverseReferences[i][j] = new BitSet(regions.size());
                    }
                }
                this.regions = regions;
            }

            void conclude(NameInfoStore store) {
                for (int i = 0; i < referenceExtractors.length; i++) {
                    ReferenceExtractorPair r = referenceExtractors[i];
                    store.addReferences(r.refSetKey, refs[i].build());
                    BitSetGraph graph = new BitSetGraph(reverseReferences[i], references[i]);
                    BitSetStringGraph stringGraph = new BitSetStringGraph(graph, regions.nameArray());
                    store.addReferenceGraph(r.refSetKey, stringGraph);
                    store.addUnknownReferences(r.refSetKey, unknown.build());
                }
            }

            private <L extends ParserRuleContext> NameAndOffsets doRunOne(ReferenceExtractorInfo<L, ?> ext, L nd) {
                return ext.offsetsExtractor.apply(nd);
            }

            private <L extends ParserRuleContext> NameAndOffsets runOne(ReferenceExtractorInfo<L, ?> ext, RuleNode nd) {
                return doRunOne(ext, ext.ruleType.cast(nd));
            }

            @Override
            public Void visitChildren(RuleNode node) {
                boolean[][] activeScratch = new boolean[referenceExtractors.length][];
                for (int i = 0; i < lengths.length; i++) {
                    activeScratch[i] = new boolean[lengths[i]];
                    for (int j = 0; j < lengths[i]; j++) {
                        ReferenceExtractorInfo<?, ?> info = infos[i][j];
                        if (info.ancestorQualifier != null) {
                            if (info.ancestorQualifier.test(node)) {
                                activeScratch[i][j] = true;
                                activations[i][j]++;
                            }
                        }
                        if (activations[i][j] > 0 && info.ruleType.isInstance(node)) {
                            NameAndOffsets referenceOffsets = runOne(info, node);
                            if (referenceOffsets != null) {
                                if (regions.contains(referenceOffsets.name)) {
                                    refs[i].addReference(referenceOffsets.name, referenceOffsets.start, referenceOffsets.end);
                                    NamedSemanticRegions.NamedSemanticRegion<T> containedBy = regions.index().regionAt(referenceOffsets.start);
                                    int referencedIndex = regions.indexOf(referenceOffsets.name);
                                    if (containedBy != null && referencedIndex != -1) {
//                                        System.out.println("REGION AT " + referenceOffsets.start + " IS " + containedBy.start() + ":" + containedBy.end() + " - " + containedBy.name());
                                        assert containedBy.containsPosition(referenceOffsets.start) :
                                                "Index returned bogus result for position " + referenceOffsets.start + ": " + containedBy + " from " + regions.index() + "; code:\n" + regions.toCode();
//                                        System.out.println("ENCOUNTERED " + referenceOffsets + " index " + referencedIndex + " inside " + containedBy.name() + " index " + containedBy.index() + " in " + regions);
                                        int referenceIndex = containedBy.index();

                                        references[i][referencedIndex].set(referenceIndex);
                                        reverseReferences[i][referenceIndex].set(referencedIndex);
                                    }
                                } else {
                                    System.out.println("ADD UNKNOWN: " + referenceOffsets);
                                    T kind = referenceOffsets instanceof NamedRegionData<?>
                                            && ((NamedRegionData<?>) referenceOffsets).kind != null
                                                    ? (T) ((NamedRegionData<?>) referenceOffsets).kind : null;

                                    unknown.add(new UnknownNameReferenceImpl(kind, referenceOffsets.start, referenceOffsets.end, referenceOffsets.name, unknownCount++), referenceOffsets.start, referenceOffsets.end);
                                }
                            }
                        }
                    }
                }
                super.visitChildren(node);
                for (int i = 0; i < lengths.length; i++) {
                    for (int j = 0; j < lengths[i]; j++) {
                        if (activeScratch[i][j]) {
                            activations[i][j]--;
                        }
                    }
                }
                return null;
            }

        }

        class RuleNameAndBoundsVisitor extends AbstractParseTreeVisitor<Void> {

            private final NamedSemanticRegionsBuilder<T> namesBuilder;
            private final NamedSemanticRegionsBuilder<T> ruleBoundsBuilder;
            private final int[] activations;

            public RuleNameAndBoundsVisitor() {
                activations = new int[nameExtractors.length];
                for (int i = 0; i < activations.length; i++) {
                    if (nameExtractors[i].ancestorQualifier == null) {
                        activations[i] = 1;
                    }
                }
                if (namePositionKey != null) {
                    namesBuilder = NamedSemanticRegions.builder(keyType);
                } else {
                    namesBuilder = null;
                }
                ruleBoundsBuilder = NamedSemanticRegions.builder(keyType);
                assert namesBuilder != null || ruleBoundsBuilder != null;
            }

            @Override
            public Void visitChildren(RuleNode node) {
                if (node instanceof ParserRuleContext) {
                    onVisit((ParserRuleContext) node);
                } else {
                    super.visitChildren(node);
                }
                return null;
            }

            private void onVisit(ParserRuleContext node) {
                boolean activationScratch[] = new boolean[nameExtractors.length];
                for (int i = 0; i < nameExtractors.length; i++) {
                    if (nameExtractors[i].ancestorQualifier != null) {
                        activationScratch[i] = nameExtractors[i].ancestorQualifier.test(node);
                        activations[i]++;
                    }
                }
                try {
                    for (int i = 0; i < nameExtractors.length; i++) {
                        if (activations[i] > 0) {
                            runOne(node, nameExtractors[i]);
                        }
                    }
                    super.visitChildren(node);
                } finally {
                    for (int i = 0; i < activationScratch.length; i++) {
                        if (activationScratch[i]) {
                            activations[i]--;
                        }
                    }
                }
            }

            private <R extends ParserRuleContext> void runOne(ParserRuleContext node, NameExtractorInfo<R, T> nameExtractor) {
                if (nameExtractor.type.isInstance(node)) {
                    doRunOne(nameExtractor.type.cast(node), nameExtractor);
                }
            }

            private <R extends ParserRuleContext> void doRunOne(R node, NameExtractorInfo<R, T> e) {
                e.find(node, (NamedRegionData<T> nm, TerminalNode tn) -> {
                    // If we are iterating TerminalNodes, tn will be non-null; otherwise
                    // it will be null and we are doing single extraction - this is so we can,
                    // for example, in an ANTLR grammar for ANTLR, create token names and
                    // references from an import tokens statement where there is no rule
                    // definition, but we should not point the definition position for all
                    // of the names to the same spot
                    if (nm != null) {
                        if (namesBuilder != null) {
                            // XXX, the names extractor actually needs to return the name AND the offsets of the name
                            // use the same code we use for finding the reference
                            namesBuilder.add(nm.name, nm.kind, nm.start, nm.end);
                        }
                        if (ruleBoundsBuilder != null) {
                            if (tn == null) {
                                ruleBoundsBuilder.add(nm.name, nm.kind, node.start.getStartIndex(), node.stop.getStopIndex() + 1);
                            } else {
                                Token tok = tn.getSymbol();
                                ruleBoundsBuilder.add(nm.name, nm.kind, tok.getStartIndex(), tok.getStopIndex() + 1);
                            }
                        }
                    }
                });
            }
        }
    }
}
