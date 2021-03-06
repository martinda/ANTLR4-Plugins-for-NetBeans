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
package org.nemesis.antlr.file.impl;

import com.mastfrog.function.TriConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.FragmentRuleDeclarationContext;
import org.nemesis.antlr.ANTLRv4Parser.FragmentRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.ANTLRv4Parser.GrammarSpecContext;
import org.nemesis.antlr.ANTLRv4Parser.GrammarTypeContext;
import org.nemesis.antlr.ANTLRv4Parser.LexComModeContext;
import org.nemesis.antlr.ANTLRv4Parser.LexComPushModeContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDeclarationContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.RuleSpecContext;
import org.nemesis.antlr.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.antlr.ANTLRv4Parser.TokenRuleDefinitionContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import static org.nemesis.antlr.common.extractiontypes.EbnfProperty.*;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion.FoldableKind;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.common.extractiontypes.LexerModes;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.NamedRegionData;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrExtractor {

    public static final String MISSING_TOKEN_ID = "<missing TOKEN_ID>";
    public static final String MISSING_ID = "<missing ID>";

    private final Extractor<ANTLRv4Parser.GrammarFileContext> extractor;

    private static boolean containsNonTrailingNewlines(ParserRuleContext ctx) {
        return containsNonTrailingNewlines(ctx.getText());
    }

    private static boolean containsNonTrailingNewlines(String txt) {
        int max = txt.length()-1;
        boolean nonWhitespaceSeen = false;
        for (int i = max; i >=0; i--) {
            char c = txt.charAt(i);
            if (!Character.isWhitespace(c)) {
                nonWhitespaceSeen = true;
            } else if (c == '\n') {
                if (nonWhitespaceSeen) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean extractActionBounds(ParserRuleContext ctx, BiPredicate<FoldableRegion, ParserRuleContext> cons) {
        return containsNonTrailingNewlines(ctx) && cons.test(FoldableKind.ACTION.createFor(ctx.getText()), ctx);
    }

    private static FoldableRegion regionForToken(Token token) {
        if (!containsNonTrailingNewlines(token.getText())) {
            return null;
        }
        switch (token.getType()) {
            case ANTLRv4Lexer.CHN_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_BLOCK_COMMENT:
            case ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT:
            case ANTLRv4Lexer.ID_BLOCK_COMMENT:
            case ANTLRv4Lexer.IMPORT_BLOCK_COMMENT:
            case ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT:
            case ANTLRv4Lexer.OPT_BLOCK_COMMENT:
            case ANTLRv4Lexer.PARDEC_BLOCK_COMMENT:
            case ANTLRv4Lexer.TOK_BLOCK_COMMENT:
            case ANTLRv4Lexer.BLOCK_COMMENT:
//                System.out.println("EXTRACT COMMENT FOR " + ANTLRv4Lexer.tokenNames[token.getType()]);
                return FoldableKind.COMMENT.createFor(token.getText());
            case ANTLRv4Lexer.DOC_COMMENT:
//                System.out.println("EXTRACT DOC COMMENT FOR " + ANTLRv4Lexer.tokenNames[token.getType()]);
                return FoldableKind.DOC_COMMENT.createFor(token.getText());
            default:
                return null;
        }
    }

    static boolean extractRuleSpecFoldBounds(RuleSpecContext ctx, BiPredicate<FoldableRegion, ParserRuleContext> cons) {
        if (ctx.getText().length() < 40) {
            // we can't test for newlines because they are routed to a different
            // channel
            return false;
        }
        String name = null;
        ANTLRv4Parser.TokenRuleSpecContext lexSpec = ctx.tokenRuleSpec();
        ANTLRv4Parser.FragmentRuleSpecContext fragSpec = ctx.fragmentRuleSpec();
        if (lexSpec != null) {
            TokenRuleDeclarationContext tokDec = lexSpec.tokenRuleDeclaration();
            if (tokDec != null && tokDec.id != null) {
                name = tokDec.id.getText();
            }
        } else if (fragSpec != null) {
            FragmentRuleDeclarationContext fragDec = fragSpec.fragmentRuleDeclaration();
            if (fragDec != null && fragDec.id != null) {
                name = fragDec.id.getText();
            }
        } else {
            ANTLRv4Parser.ParserRuleSpecContext parSpec = ctx.parserRuleSpec();
            if (parSpec != null) {
                ParserRuleDeclarationContext parDec = parSpec.parserRuleDeclaration();
                if (parDec != null && parDec.parserRuleIdentifier() != null) {
                    name = parDec.parserRuleIdentifier().getText();
                }
            }
        }
        boolean result = name != null;
        if (result) {
            result = cons.test(FoldableKind.RULE.createFor(name), ctx);
        }
        return result;
    }

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE, entryPoint = GrammarFileContext.class)
    public static void populateBuilder(ExtractorBuilder<? super GrammarFileContext> bldr) {
        // First build code-fold regions - all comments and rules, plus
        // miscellaneous such as action and options blocks
        bldr.extractingRegionsUnder(AntlrKeys.FOLDABLES)
                .whenTokenTypeMatches(
                        ANTLRv4Lexer.BLOCK_COMMENT,
                        ANTLRv4Lexer.DOC_COMMENT,
                        ANTLRv4Lexer.CHN_BLOCK_COMMENT,
                        ANTLRv4Lexer.HEADER_BLOCK_COMMENT,
                        ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT,
                        ANTLRv4Lexer.ID_BLOCK_COMMENT,
                        ANTLRv4Lexer.IMPORT_BLOCK_COMMENT,
                        ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT,
                        ANTLRv4Lexer.OPT_BLOCK_COMMENT,
                        ANTLRv4Lexer.PARDEC_BLOCK_COMMENT,
                        ANTLRv4Lexer.TOK_BLOCK_COMMENT)
                .derivingKeyWith(AntlrExtractor::regionForToken)
                .whenRuleType(RuleSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractRuleSpecFoldBounds)
                .whenRuleType(ANTLRv4Parser.ActionContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.ActionBlockContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.AnalyzerDirectiveSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.TokenVocabSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.RuleActionContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .whenRuleType(ANTLRv4Parser.OptionsSpecContext.class)
                .extractingKeyAndBoundsFromRuleWith(AntlrExtractor::extractActionBounds)
                .finishRegionExtractor()
                // Extract a singleton, the grammar type
                .extractingSingletonUnder(AntlrKeys.GRAMMAR_TYPE)
                .using(GrammarSpecContext.class)
                .extractionObjectAndBoundsWith(AntlrExtractor::findGrammarType)
                .finishObjectExtraction()
                // No extract imports
                .extractNamedRegionsKeyedTo(ImportKinds.class)
                .recordingNamePositionUnder(AntlrKeys.IMPORTS)
                .whereRuleIs(ANTLRv4Parser.TokenVocabSpecContext.class)
                .derivingNameFromTokenWith(ImportKinds.TOKEN_VOCAB, AntlrExtractor::extractImportFromTokenVocab)
                .whereRuleIs(ANTLRv4Parser.DelegateGrammarContext.class)
                .derivingNameFromTerminalNodeWith(ImportKinds.DELEGATE_GRAMMAR, AntlrExtractor::extractImportFromDelegateGrammar)
                .finishNamedRegions()
                // Extract named regions so they are addressable by name or position in file
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                // Store the bounds of the entire rule in the resulting Extraction under the key
                // RULE_BOUNDS, which will be parameterized on RuleTypes so we can query for a
                // NamedSemanticRegions<RuleTypes>
                .recordingRuleRegionUnder(AntlrKeys.RULE_BOUNDS)
                // Store the bounds of just the rule name (for goto declaration) under the key
                // RULE_NAMES in the resulting extractoin
                .recordingNamePositionUnder(AntlrKeys.RULE_NAMES)
                // Extracts the rule id from a token declaration
                .whereRuleIs(ANTLRv4Parser.TokenRuleSpecContext.class)
                .derivingNameWith(AntlrExtractor::deriveIdFromTokenRuleSpec)
                // Extracts the rule id from a parser rule declaration, using reflection instead of
                // a method, to show that works
                .whereRuleIs(ANTLRv4Parser.ParserRuleSpecContext.class)
                .derivingNameWith(AntlrExtractor::deriveIdFromParserRuleSpec)
                //                .derivingNameWith("parserRuleDeclaration().parserRuleIdentifier()", RuleTypes.PARSER)
                // Extracts the rule id from a fragment declaration
                .whereRuleIs(ANTLRv4Parser.FragmentRuleSpecContext.class)
                .derivingNameWith(AntlrExtractor::deriveIdFromFragmentSpec)
                // Generate fake definitions for declared tokens so we don't flag them as errors
                .whereRuleIs(ANTLRv4Parser.TokenListContext.class)
                .derivingNameFromTerminalNodes(RuleTypes.LEXER, AntlrExtractor::deriveTokenIdFromTokenList)
                //                .whereRuleIs(ANTLRv4Parser.ParserRuleLabeledAlternativeContext.class)
                //                //                .derivingNameWith("identifier().ID()", Alternatives.NAMED_ALTERNATIVES)
                //                .derivingNameWith(AntlrExtractor::extractAlternativeLabelInfo)

                // Now collect usages of the rule ids we just collected.  This gets us both
                // the ability to query for a NamedReferenceSet<RuleTypes> which has the reference
                // and can resolve the reference, and a bidirectional graph built from arrays of BitSets
                // which can let us walk the closure of a rule in either direction
                .collectingReferencesUnder(AntlrKeys.RULE_NAME_REFERENCES)
                //                .whereReferenceContainingRuleIs(ANTLRv4Parser.TerminalContext.class)
                //                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferencedNameFromTerminalContext)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.ParserRuleReferenceContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .whenAncestorRuleOf(ParserRuleDefinitionContext.class)
                .derivingReferenceOffsetsFromTokenWith(RuleTypes.PARSER, AntlrExtractor::deriveReferenceFromParserRuleReference)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TokenRuleIdentifierContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .whenAncestorRuleOf(ParserRuleDefinitionContext.class)
                .derivingReferenceOffsetsFromTokenWith(RuleTypes.LEXER, AntlrExtractor::deriveReferenceFromTokenRuleReference)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.TokenRuleIdentifierContext.class)
                .whenAncestorRuleOf(TokenRuleDefinitionContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferenceFromTokenRuleReference)
                .whereReferenceContainingRuleIs(ANTLRv4Parser.FragmentRuleIdentifierContext.class)
                .whenAncestorRuleOf(FragmentRuleDefinitionContext.class)
                // Include the type hint PARSER so we can detect references that should be parser rules but are not
                .derivingReferenceOffsetsFromTokenWith(AntlrExtractor::deriveReferenceFromFragmentRuleReference)
                // Done specifying how to collect references
                .finishReferenceCollector()
                .finishNamedRegions()
                // Just collect the offsets of all BlockContext trees, in a nested data structure
                .extractingRegionsUnder(AntlrKeys.BLOCKS)
                .summingTokensFor(ANTLRv4Lexer.VOCABULARY)
                .whenRuleType(ANTLRv4Parser.AltListContext.class)
                .extractingBoundsFromRule()
                .whenRuleType(ANTLRv4Parser.LexerRuleBlockContext.class)
                .extractingBoundsFromRule()
                .finishRegionExtractor()
                // More complex nested region extraction for EBNF contexts, so we can progressively
                // darket the background color in the editor of nested repetitions
                .extractingRegionsUnder(AntlrKeys.EBNFS)
                .whenRuleType(ANTLRv4Parser.EbnfContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfPropertiesFromEbnfContext)
                .whenRuleType(ANTLRv4Parser.ParserRuleElementContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfRegionFromParserRuleElement)
                .whenRuleType(ANTLRv4Parser.LexerRuleElementContext.class)
                .extractingKeyAndBoundsWith(AntlrExtractor::extractEbnfRegionFromLexerRuleElement)
                .finishRegionExtractor()
                // Extract named regions
                .extractNamedRegionsKeyedTo(RuleTypes.class)
                .recordingNamePositionUnder(AntlrKeys.NAMED_ALTERNATIVES)
                .whereRuleIs(ANTLRv4Parser.ParserRuleLabeledAlternativeContext.class)
                //                .derivingNameWith("identifier().ID()", Alternatives.NAMED_ALTERNATIVES)
                .derivingNameWith(AntlrExtractor::extractAlternativeLabelInfo)
                .finishNamedRegions()
                // Collect various header stuff we want to italicize
                .extractingRegionsUnder(AntlrKeys.HEADER_MATTER)
                .whenRuleType(ANTLRv4Parser.MemberActionContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.MEMBERS_DECLARATION)
                .whenRuleType(ANTLRv4Parser.HeaderActionBlockContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.HEADER_ACTION)
                .whenRuleType(ANTLRv4Parser.ImportDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.IMPORT)
                .whenRuleType(ANTLRv4Parser.TokensSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.TOKENS)
                .whenRuleType(ANTLRv4Parser.OptionsSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.OPTIONS)
                .whenRuleType(ANTLRv4Parser.PackageDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.PACKAGE)
                .whenRuleType(ANTLRv4Parser.ImportDeclarationContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.IMPORT)
                .whenRuleType(ANTLRv4Parser.AnalyzerDirectiveSpecContext.class)
                .extractingBoundsFromRuleUsingKey(HeaderMatter.DIRECTIVE)
                .finishRegionExtractor()
                .extractNamedRegionsKeyedTo(LexerModes.class)
                .recordingNamePositionUnder(AntlrKeys.MODES)
                .whereRuleIs(ANTLRv4Parser.ModeDecContext.class)
                .derivingNameFromTerminalNodeWith(LexerModes.MODE, decl -> {
                    return decl.identifier() == null ? null : decl.identifier().ID();
                })
                .collectingReferencesUnder(AntlrKeys.MODE_REFS)
                .whereReferenceContainingRuleIs(LexComModeContext.class)
                .derivingReferenceOffsetsFromTerminalNodeWith(LexerModes.MODE, lcmc -> {
                    return lcmc.identifier() == null ? null : lcmc.identifier().ID();
                })
                .whereReferenceContainingRuleIs(LexComPushModeContext.class)
                .derivingReferenceOffsetsFromTerminalNodeWith(lcpmc -> {
                    return lcpmc.identifier() == null ? null : lcpmc.identifier().ID();
                }).finishReferenceCollector().finishNamedRegions();
    }

    AntlrExtractor() {
        ExtractorBuilder<GrammarFileContext> builder = Extractor.builder(GrammarFileContext.class, ANTLR_MIME_TYPE);
        populateBuilder(builder);
        extractor = builder.build();
    }

    static void findGrammarType(ANTLRv4Parser.GrammarSpecContext spec, TriConsumer<GrammarDeclaration, Integer, Integer> cons) {
        GrammarTypeContext gtc = spec.grammarType();
        GrammarType grammarType = GrammarType.UNDEFINED;
        if (gtc == null) {
            grammarType = GrammarType.COMBINED;
        } else {
            TerminalNode lexer = gtc.LEXER();
            TerminalNode parser = gtc.PARSER();
            TerminalNode grammar = gtc.GRAMMAR();
            if (lexer != null
                    && parser == null
                    && grammar != null) {
                grammarType = GrammarType.LEXER;
            }
            if (lexer == null
                    && parser != null
                    && grammar != null) {
                grammarType = GrammarType.PARSER;
            }
            if (lexer == null
                    && parser == null
                    && grammar != null) {
                grammarType = GrammarType.COMBINED;
            }
        }
        ANTLRv4Parser.IdentifierContext idContext = spec.identifier();
        if (idContext != null && idContext.ID() != null && idContext.ID().getSymbol() != null) { // broken source
            String name = idContext.ID().getText();
            GrammarDeclaration result = new GrammarDeclaration(grammarType,
                    name, spec.start.getStartIndex(),
                    spec.stop.getStopIndex() + 1);
            cons.accept(result, idContext.ID().getSymbol().getStartIndex(),
                    idContext.ID().getSymbol().getStopIndex() + 1);
        } else {
            return;
        }
    }
/*
    public Extraction extract(GrammarFileContext ctx, GrammarSource<?> src) {
        Supplier<TokenStream> streamSource = () -> {
            try {
                ANTLRv4Lexer lex = new ANTLRv4Lexer(src.stream());
                lex.removeErrorListeners();
                BufferedTokenStream stream = new BufferedTokenStream(lex);
                return stream;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        };
        return extract(ctx, src, streamSource);
    }

    public Extraction extract(GrammarFileContext ctx, GrammarSource<?> src, Supplier<TokenStream> tokensSource) {
        List<Token> tokens = new ArrayList<>();
        TokenStream st = tokensSource.get();
        for (int i = 0; i < st.size(); i++) {
            tokens.add(st.get(i));
        }
        return extractor.extract(ctx, src, tokens);
    }

    public Extraction extract(GrammarSource<?> src) throws IOException {
        ANTLRv4Lexer lex = new ANTLRv4Lexer(src.stream());
        lex.removeErrorListeners();
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex, 0));
        parser.removeErrorListeners();
        return extract(parser.grammarFile(), src);
    }

    private static final AntlrExtractor INSTANCE = new AntlrExtractor();
    public static AntlrExtractor getDefault() {
        return INSTANCE;
    }
*/

    private static boolean hasAncestor(ParserRuleContext ctx, Class<? extends ParserRuleContext> anc) {
        boolean found = false;
        while (ctx != null) {
            if (anc.isInstance(ctx)) {
                found = true;
                break;
            }
            ctx = ctx.getParent();
        }
        return found;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromTokenRuleSpec(ANTLRv4Parser.TokenRuleSpecContext id) {
        return deriveIdFromTokenRuleDeclaration(id.tokenRuleDeclaration());
    }

    private static NamedRegionData<RuleTypes> deriveIdFromTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext id) {
        if (id == null) {
            return null;
        }
        return deriveIdFromTokenRuleIdentifier(id.id);
    }

    private static NamedRegionData<RuleTypes> deriveIdFromTokenRuleIdentifier(ANTLRv4Parser.TokenRuleIdentifierContext id) {
        if (id == null) {
            return null;
        }
        assert hasAncestor(id, TokenRuleDeclarationContext.class) : "Not in a token rule definition";
        TerminalNode tn = id.TOKEN_ID();
        if (tn != null) {
            org.antlr.v4.runtime.Token tok = tn.getSymbol();
            if (tok != null && !MISSING_ID.equals(tok.getText())) {
                NamedRegionData<RuleTypes> result = NamedRegionData.create(tok.getText(), RuleTypes.LEXER, tok.getStartIndex(), tok.getStopIndex() + 1);
                return result;
            }
        }
        return null;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromParserRuleIdentifier(ANTLRv4Parser.ParserRuleIdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        assert hasAncestor(ctx, ParserRuleDeclarationContext.class) : "Not in a ParserRuleDefinitionContext: " + ctx;
        if (ctx.PARSER_RULE_ID() != null && !MISSING_ID.equals(ctx.PARSER_RULE_ID().getText())) {
            return NamedRegionData.create(ctx.PARSER_RULE_ID().getSymbol().getText(), RuleTypes.PARSER, ctx.PARSER_RULE_ID().getSymbol().getStartIndex(),
                    ctx.PARSER_RULE_ID().getSymbol().getStopIndex() + 1);
        }
        return null;
    }

    private static NamedRegionData<RuleTypes> deriveIdFromParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext decl) {
        if (decl == null) {
            return null;
        }
        ANTLRv4Parser.ParserRuleIdentifierContext ctx = decl.parserRuleIdentifier();
        return deriveIdFromParserRuleIdentifier(ctx);
    }

    private static NamedRegionData<RuleTypes> deriveIdFromParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext decl) {
        return deriveIdFromParserRuleDeclaration(decl.parserRuleDeclaration());
    }

    private static NamedRegionData<RuleTypes> deriveIdFromFragmentSpec(ANTLRv4Parser.FragmentRuleSpecContext ctx) {
        return deriveIdFromFragmentDeclaration(ctx.fragmentRuleDeclaration());
    }

    private static NamedRegionData<RuleTypes> deriveIdFromFragmentDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
        if (ctx == null) {
            return null;
        }
        return deriveIdFromFragmentDeclaration(ctx.id);
    }

    private static NamedRegionData<RuleTypes> deriveIdFromFragmentDeclaration(ANTLRv4Parser.FragmentRuleIdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        assert hasAncestor(ctx, FragmentRuleDeclarationContext.class) : "Not in a fragment rule definition";
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            if (idToken != null && !MISSING_TOKEN_ID.equals(idToken.getText())) {
                return NamedRegionData.create(idToken.getText(), RuleTypes.FRAGMENT, idToken.getStartIndex(), idToken.getStopIndex() + 1);
//                    names.add(idToken.getText(), AntlrRuleKind.FRAGMENT_RULE, idToken.getStartIndex(), idToken.getStopIndex() + 1);
            }
        }
        return null;
    }

    private static List<? extends TerminalNode> deriveTokenIdFromTokenList(ANTLRv4Parser.TokenListContext ctx) {
//        return ctx.fragmentRuleIdentifier() != null ?
        List<TerminalNode> result = new ArrayList<>();
        if (ctx.fragmentRuleIdentifier() != null) {
            for (ANTLRv4Parser.FragmentRuleIdentifierContext frag : ctx.fragmentRuleIdentifier()) {
                if (frag.TOKEN_ID() != null) {
                    result.add(frag.TOKEN_ID());
                }
            }
        }
        if (ctx.tokenRuleIdentifier() != null) {
            for (ANTLRv4Parser.TokenRuleIdentifierContext tok : ctx.tokenRuleIdentifier()) {
                if (tok.TOKEN_ID() != null) {
                    result.add(tok.TOKEN_ID());
                }
            }
        }
        Collections.sort(result, (a, b) -> {
            return Integer.compare(a.getSourceInterval().a, b.getSourceInterval().a);
        });
        return result;
    }

    private static Token deriveReferencedNameFromTerminalContext(ANTLRv4Parser.TerminalContext ctx) {
        TerminalNode idTN
                = ctx.fragmentRuleIdentifier() != null
                && ctx.fragmentRuleIdentifier().TOKEN_ID() != null
                ? ctx.fragmentRuleIdentifier().TOKEN_ID()
                : ctx.tokenRuleIdentifier() != null
                && ctx.tokenRuleIdentifier().TOKEN_ID() != null
                ? ctx.tokenRuleIdentifier().TOKEN_ID() : null;

        if (idTN != null) {
            Token idToken = idTN.getSymbol();
//            System.out.println("IN TERMINAL CONTEXT '" + ctx.getText() + "' with tok '" + idToken.getText() + "'");
            if (idToken != null) {
                String id = idToken.getText();
                if (!MISSING_ID.equals(id) && !MISSING_TOKEN_ID.equals(id)) {
                    return idToken;
                }
            }
        }
        return null;
    }

    private static Token deriveReferenceFromParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
        ANTLRv4Parser.ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
        if (pric != null) {
            TerminalNode pridTN = pric.PARSER_RULE_ID();
            if (pridTN != null) {
                Token tok = pridTN.getSymbol();
                if (tok != null && !MISSING_ID.equals(tok.getText())) {
//                        names.onReference(tok.getText(), tok.getStartIndex(), tok.getStopIndex() + 1);
                    return tok;
                }
            }
        }
        return null;
    }

    private static Token deriveReferenceFromTokenRuleReference(ANTLRv4Parser.TokenRuleIdentifierContext pric) {
        TerminalNode pridTN = pric.TOKEN_ID();
        if (pridTN != null) {
            Token tok = pridTN.getSymbol();
            if (tok != null && !MISSING_ID.equals(tok.getText())) {
                return tok;
            }
        }
        return null;
    }

    private static Token deriveReferenceFromFragmentRuleReference(ANTLRv4Parser.FragmentRuleIdentifierContext pric) {
        TerminalNode pridTN = pric.TOKEN_ID();
        if (pridTN != null) {
            Token tok = pridTN.getSymbol();
            if (tok != null && !MISSING_ID.equals(tok.getText())) {
                return tok;
            }
        }
        return null;
    }

    private static boolean extractEbnfPropertiesFromEbnfContext(ANTLRv4Parser.EbnfContext ctx, BiPredicate<Set<EbnfProperty>, int[]> c) {
        return maybeAddEbnf(ctx.block(), ctx.ebnfSuffix(), c);
    }

    private static boolean extractEbnfRegionFromParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx, BiPredicate<Set<EbnfProperty>, int[]> c) {
        if (ctx.parserRuleAtom() != null) {
            return maybeAddEbnf(ctx.parserRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.labeledParserRuleElement() != null) {
            return maybeAddEbnf(ctx.labeledParserRuleElement(), ctx.ebnfSuffix(), c);
        }
        return false;
    }

    private static boolean extractEbnfRegionFromLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx, BiPredicate<Set<EbnfProperty>, int[]> c) {
        if (ctx.lexerRuleAtom() != null) {
            return maybeAddEbnf(ctx.lexerRuleAtom(), ctx.ebnfSuffix(), c);
        } else if (ctx.lexerRuleElementBlock() != null) {
            return maybeAddEbnf(ctx.lexerRuleElementBlock(), ctx.ebnfSuffix(), c);
        }
        return false;
    }

    private static NamedRegionData<RuleTypes> extractAlternativeLabelInfo(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx) {
        ANTLRv4Parser.IdentifierContext idc = ctx.identifier();
        if (idc != null && ctx.SHARP() != null) {
            TerminalNode idTN = idc.ID();
//            return idTN;
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String altnvLabel = labelToken.getText();
                    NamedRegionData<RuleTypes> result = NamedRegionData.create(altnvLabel,
                            RuleTypes.NAMED_ALTERNATIVES, ctx.SHARP().getSymbol().getStartIndex(), labelToken.getStopIndex() + 1);
                    return result;
                }
            }
        }
        return null;
    }

    private static Token extractImportFromTokenVocab(ANTLRv4Parser.TokenVocabSpecContext ctx) {
        ANTLRv4Parser.IdentifierContext idctx = ctx.identifier();
        if (idctx != null) {
            TerminalNode tn = idctx.ID();
            if (tn != null) {
                return tn.getSymbol();
            }
        }
        return null;
    }

    private static TerminalNode extractImportFromDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
        ANTLRv4Parser.GrammarIdentifierContext gic = ctx.grammarIdentifier();
        if (gic != null) {
            ANTLRv4Parser.IdentifierContext ic = gic.identifier();
            if (ic != null) {
                return ic.ID();
            }
        }
        return null;
    }

    private static boolean maybeAddEbnf(ParserRuleContext repeated, ANTLRv4Parser.EbnfSuffixContext suffix, BiPredicate<Set<EbnfProperty>, int[]> c) {
        if (suffix == null || repeated == null) {
            return false;
        }
        String ebnfString = suffix.getText();
        if (!ebnfString.isEmpty()) {
            Set<EbnfProperty> key = ebnfPropertiesForSuffix(suffix);
            if (!key.isEmpty()) {
                int start = repeated.getStart().getStartIndex();
                int end = suffix.getStop().getStopIndex() + 1;
                return c.test(key, new int[]{start, end});
            }
        }
        return false;
    }

    private static Set<EbnfProperty> ebnfPropertiesForSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        Set<EbnfProperty> result = EnumSet.noneOf(EbnfProperty.class);
        if (ctx.PLUS() != null) {
            result.add(PLUS);
        }
        if (ctx.STAR() != null) {
            result.add(STAR);
        }
        if (ctx.QUESTION() != null && ctx.QUESTION().size() > 0) {
            //                System.out.println("question; " + ctx.QUESTION());
            result.add(QUESTION);
        }
        return result;
    }
}
