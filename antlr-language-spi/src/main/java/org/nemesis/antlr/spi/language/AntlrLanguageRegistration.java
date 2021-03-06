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
package org.nemesis.antlr.spi.language;

import org.nemesis.antlr.spi.language.highlighting.TokenCategorizer;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.netbeans.spi.lexer.EmbeddingPresence;

/**
 * Register an Antlr-based language, generating all necessary configuration and
 * implementation classes for a basic implementation of language support and
 * syntax highlighting.
 * <p>
 * Creating full featured IDE support for a language starts here.
 * </p>
 *
 * @author Tim Boudreau
 */
@Retention( RetentionPolicy.SOURCE )
@Target( value = ElementType.TYPE )
public @interface AntlrLanguageRegistration {

    /**
     * The language name - used as a prefix in generated file names.
     *
     * @return The name
     *
     * @deprecated Unused. This can cause severe problems in that annotations processed
     * separatey cannot guess what this value is, but may need to access generated
     * classes such as the LanguageHierarchy or Token subclasses generated from
     * this annotation, but will not have any way to guess it. The preferred
     * way of handling this is to embed it in the MIME type as a key-value pair,
     * which at least stands a better chance of being portable - e.g.
     * <code>text/x-g4;prefix=Antlr</code>. By default, the back half of the
     * MIME type is used, omitting any leading <code>x-</code>, so
     * <code>text/x-g4</code> results in <code>G4DataObject</code>,
     * <code>G4LanguageHierarchy</code>, etc.
     */
    @Deprecated
    String name();

    /**
     * If true and if the <code>mimeType</code> does not contain a
     * <code>prefix=...</code> key-value pair, then derive the name of
     * the language (used to prefix class names) from the lexer name -
     * so IntercalLexer results in a file name prefix "Intercal".
     * <p>
     * While this could in theory cause a problem with annotations in other
     * modules not knowing the prefix to derive the name of the language's
     * LanguageHierarchy or DataObject subclasses, in practice, every
     * annotation that needs to do this also knows the lexer class and can
     * figure it out for itself. Nonetheless, it is off by default.
     * </p><p>
     * If this is not set, and no prefix is specified in the mime type,
     * the back half of the MIME type, sans any leading <code>x-</code>
     * or text preceding a <code>+</code> character is used (so
     * <code>text/xml+docbook</code> gets "Docbook".
     * </p>
     *
     * @return true if, if no prefix= pair is present in the mime type,
     *         the prefix should be derived from the grammar name as implied
     *         by the lexer name
     */
    boolean useImplicitLanguageNameFromLexerName() default false;

    /**
     * The mime type this language is registered under. To ensure that
     * the generated API classes are named reasonably intuitively, the
     * mime type may contain a key-value pair "prefix=..." - for example,
     * <code>text/x-g4;prefix=Antlr</code> and the class name prefix
     * <code>Antlr</code> will be used instead of the less intuitive
     * <code>G4</code>.
     *
     * @return The mime type
     */
    String mimeType();

    /**
     * The Antlr lexer class.
     *
     * @return The class of the generated Antlr lexer
     */
    Class<? extends Lexer> lexer();

    /**
     * The bundle to use for display names of generated files.
     *
     * @return A bundle, such as com.foo.Bundle
     */
    String localizingBundle() default "";

    /**
     * If you will do your own token categorizing (which determines which tokens
     * belong to which fonts and colors categories / colorings), you can
     * implement a custom one and pass its class here; alternately, you can use
     * the categories() property below and the necessary files will be
     * generated. If you use both, the token categorizer will take priority.
     *
     * @return A categorizer
     */
    Class<? extends TokenCategorizer> tokenCategorizer() default TokenCategorizer.class;

    /**
     * Define categories of tokens which will be used to color those tokens in
     * the editor.
     *
     * @return An array of token categories
     */
    TokenCategory[] categories() default {};

    /**
     * Breif sample text in the language in question, which can either be raw
     * text or a / delimited file path within the module's JAR (any whitespace will
     * cause it to be assumed to be raw sample text). If set it will be used in
     * the <b>Fonts and Colors</b> options dialog page for your language.
     *
     * @return Sample text in the language in question, or a path within sources to it
     */
    String sample() default "";

    /**
     * If present, generate a NetBeans parser using the Antlr parser this field
     * specifies, and which will run any Extractors registered against your
     * file's mime type.
     *
     * @return A ParserControl
     */
    ParserControl parser() default @ParserControl( entryPointRule = Integer.MIN_VALUE, type = Parser.class );

    /**
     * Causes an ExtSyntax from the editor api to be generated, which impacts
     * a few standard editor features (specifies whitespace tokens, etc.) The
     * list of whitespace tokens is also provided here, and several things
     * make use of that (without it, the system will attempt to figure it out
     * based on the literal names of tokens and other ways, all of which are
     * slower than querying a bitset or binary searching an int[]).
     *
     * @return A syntax info
     */
    SyntaxInfo syntax() default @SyntaxInfo;

    /**
     * If the lanugage has a line comment prefix such as //, specifiy
     * that here and the comment-line action will be implemented for you.
     *
     * @return A string
     */
    String lineCommentPrefix() default "";

    /**
     * If configured, generic code completion will be generated,
     * which uses Antlr's internal ability to predict what tokens
     * or rules can possibly come next to decide what completions
     * are possible. It can do basic keyword completion with minimal
     * configuration, and complete names from configured extracted
     * NamedSemanticRegions if the grammar is written so it is amenable
     * to that, and the extraction captures names for those rules which
     * are predicted. What that means, specifically, is:
     * <ul>
     * <li>Your grammar uses separate parser rules for different kinds
     * of identifier you want to capture. In other words, if you capture
     * a lot names of things that have different purposes simply using
     * a lexer token ID, no completion will be offered, because your
     * extracted tokens won't be mapped internally to any lexer rule;
     * and if you use a generic "identifier" rule, every known identifier
     * will be offered as code completion, whether it is legal or not.
     * SO... if you have a generic "identifier" rule used for heterogenous
     * things that should not be completions of each other, turn those
     * into separate rules.
     * </li>
     * <li>When defining an extractor, make sure <i>those</i> rules
     * are the ones you use in your ExtractionBuilder (test on an ancestor
     * rule if you use the same rule of <i>definitions</i> and
     * <i>references to</i> that rule, to only capture as names those
     * things that are <i>definitions</i>).</li>
     * </ul>
     *
     * @return A code completion configuration
     */
    CodeCompletion genericCodeCompletion() default @CodeCompletion;

    /**
     * Defines properties for generic code completion. At a minimum,
     * one of <code>ignoreTokens()</code> or <code>preferredRules</code>
     * must be set, or nothing will be generated.
     */
    public @interface CodeCompletion {

        /**
         * An array of token ids (constants defined on your
         * Antlr-generated Lexer) that are irrelevant for code
         * completion - things like comment markers, whitespace,
         * and punctuation that isn't terribly useful in code
         * completion.
         *
         * @return An array of token ids.
         */
        int[] ignoreTokens() default {};

        /**
         * An array of rule ids (constants defined on your
         * Antlr-generated parser with names that start with
         * <code>RULE_</code>) which you are interested in
         * completions for - and <i>for which your extraction
         * will capture names</i>, or you are providing a CompletionSupplier
         * that is interested in and can return completions for. If you are not directly
         * capturing names, you can also implement CompletionsSupplier
         * and it will be used in place of looking up names from
         * your extraction.
         * <p>
         * This is an important and fairly touchy thing to set up -
         * the code completion engine does a combination of using
         * and simulating Antlr's predictions for what the next rule
         * and/or token might be. If you are not seeing code completion
         * for some place you expect to, turn on generation of the
         * syntax tree navigator panel, and, when running, switch to
         * that and place the caret in the spot you expect to see
         * code completion. Most likely the rule that the syntax tree
         * shows the caret to be "in" (in can mean "immediately adjacent to"),
         * and make sure the constant for that rule is listed here.
         * </p>
         *
         * @return The set of rules to complete
         */
        int[] preferredRules() default {};

        /**
         * Generic code completion automatically completes tokens which
         * have predefined content (in the Lexer's Vocabulary, its
         * <i>symbolic name</i> is non-null). Completion items for these will
         * be generated automatically (unless they are in the ignore set).
         * However, if you use the same fragment in multiple places in
         * a grammar using the
         * <a href="https://github.com/antlr/antlr4/blob/master/doc/lexer-rules.md#type">type lexer command</a>, you
         * will wind up with tokens which have a fixed,
         * always-the-same value, but have no symbolic name reported by the
         * vocabulary. This method allows you to fill those
         * back in; and also to provide useful supplementary text - for example,
         * the Antlr lexer command <code>pushMode</code> takes an argument in
         * parentheses, but to Antlr, the <code>()</code> are something
         * separate - what you want is to insert <code>pushMode()</code> and
         * place the caret between the parentheses (the default insertion
         * engine special-cases caret handling for things that end in "()").
         *
         * @return A set of supplementary token completions
         */
        SupplementaryTokenCompletion[] tokenCompletions() default {};

        public @interface SupplementaryTokenCompletion {
            /**
             * The token identifier (constant on your lexer).
             *
             * @return The token id
             */
            int tokenId();

            /**
             * The text to use in completion.
             *
             * @return The text
             */
            String text();
        }

        /**
         * In the case that you are using one parser rule for names of
         * things, and a different parser rule for <i>references to</i>
         * that name. Generic code completion looks for names that were
         * extracted inside a rule ID and offers completions when
         * <i>that exact rule ID is one of those that could follow the
         * caret token</i>. In the case that the token at the caret uses
         * a different rule ID, use &#064;RuleSubstitutions to request
         * names for the rule ID you collected them under instead.
         *
         * @return An array of substitution token pairs
         */
        RuleSubstitutions[] ruleSubstitutions() default {};

        public @interface RuleSubstitutions {
            int complete();

            int withCompletionsOf();
        }
    }

    /**
     * While the ExtSyntax which is generated does little in the IDE today,
     * the lists of tokens defined here are used by various other parts of
     * the system to know what to skip.
     */
    public @interface SyntaxInfo {

        /**
         * A list of token types (static fields on your generated lexer) that
         * indicate comments.
         *
         * @return the comment tokens
         */
        int[] commentTokens() default {};

        /**
         * A list of token types (static fields on your generated lexer) which
         * are whitespace.
         *
         * @return the whitespace tokens
         */
        int[] whitespaceTokens() default {};

        /**
         * A list of token types (static fields on your generated lexer) to skip
         * when matching braces
         *
         * @return the whitespace tokens
         */
        int[] bracketSkipTokens() default {};
    }

    public @interface ParserControl {

        /**
         * Get the entry point rule for the parser (all rules will be identified
         * by static int fields on your generated Antlr lexer). This determines
         * what class and parser method is the entry point, and <i>must</i> be
         * the type any registered ExtractorRegistrations expect. This should be
         * the ID of the Antlr parser rule which defines an entire file or
         * compilation unit in the language; conventionally, this is the first
         * rule in the grammar file, so the default is 0.
         *
         * @return The rule id
         */
        int entryPointRule() default 0;

        /**
         * The specific type of the Antlr parser to instantiate.
         *
         * @return A parser class
         */
        Class<? extends Parser> type();

        /**
         * The parser helper need not be defined, but allows you to hook into
         * the lifecycle of each parse and configure the parser, or perform
         * syntactic error analysis or similar, programmatically.
         *
         * @return A helper class
         */
        Class<? extends NbParserHelper> helper() default NbParserHelper.class;

        /**
         * If true, the generated NetBeans parser class will have a static
         * method that causes all non-garbage-collected parsers to fire a change
         * event, forcing a reparse. This is only useful for languages which
         * have global configuration (such as PHP) which can affect how the
         * language is parsed, what is or isn't an error, etc. If you are
         * dealing with such a language, return true here and listen on whatever
         * file or settings affect parsing, and call the generated global
         * reparse method when those settings change.
         *
         * @return True if the generated NetBeans parsers should support firing
         *         changes to anything that might be using them
         */
        boolean changeSupport() default false;

        /**
         * Antlr supports multiple "channels" within a token stream from a
         * lexer, with different tokens assigned to different channels (for
         * example, routing comments or whitespace to a different channel,
         * rather than having every parser rule define every possible location
         * of whitespace or comments). Antlr parsers generally want only the
         * channel with content relevant to them passed. This is typically
         * channel 0, but can be set otherwise here.
         *
         * @return The channel to use when constructing a CommonTokenStream from
         *         the Antlr lexer to pass to the Antlr parser.
         */
        int parserStreamChannel() default 0;

        /**
         * If true, generate a navigator panel which will show the syntax tree of
         * this language. You will need the antlr-navigators project on the
         * classpath for the generated class to compile. Useful when debugging
         * parsers or language support.
         *
         * @return true if the panel will be generated
         */
        boolean generateSyntaxTreeNavigatorPanel() default false;

        /**
         * If true, generate a navigator panel which will show the contents
         * of the Extraction from the last parse of the source - useful for
         * debugging while developing language support.
         *
         * @return If true the panel will be generated
         */
        boolean generateExtractionDebugNavigatorPanel() default false;

        /**
         * If true (the default), syntax errors will automatically be
         * highlighted, with descriptions taken from the Antlr parse errors; set to false if you want to provide your
         * own error highlighting in place of that.
         *
         * @return true by default
         */
        boolean defaultErrorHighlightingEnabled() default true;
    }

    /**
     * If set, generate a DataObject class and register a file type in the IDE
     * for this language (generally you want this unless you are dealing with
     * any language that can be saved to disk, but for advanced cases you may
     * want to implement the DataObject type by hand).
     *
     * @return The file type
     */
    FileType file() default @FileType( extension = "." );

    public @interface FileType {

        /**
         * The file extension of files in this language (no dot).
         *
         * @return The extension, e.g. "java" for java files
         */
        String extension();

        /**
         * A / delimited path in the JAR to the icon to use for your file type.
         *
         * @return An icon path to an image
         */
        String iconBase() default "";

        /**
         * If this method returns true, multiple editor views will be possible,
         * and your or other modules can register additional "views" which
         * appear as tab-buttons above the editor.
         *
         * @return True if this should have a multi-view editor
         */
        boolean multiview() default false;

        /**
         * This module defines a default set of actions on the generated file
         * type. You can exclude some of those by including them here - the
         * array entries should be combination of category/action-id, e.g.
         * Edit/org.openide.actions.CutAction
         *
         * @return A list of actions to omit from the popup menu for files of
         *         this type
         */
        String[] excludedActions() default {};

        /**
         * If true, allow copying of files of this type.
         *
         * @return True if DataObject.isCopyAllowed() should return true for
         *         your file type.
         */
        boolean copyAllowed() default true;

        /**
         * If true, allow deletion of files of this type.
         *
         * @return True if DataObject.isDeleteAllowed() should return true for
         *         your file type.
         */
        boolean deleteAllowed() default true;

        /**
         * If true, allow moving of files of this type.
         *
         * @return True if DataObject.isMoveAllowed() should return true for
         *         your file type.
         */
        boolean moveAllowed() default true;

        /**
         * If true, allow renaming of files of this type.
         *
         * @return True if DataObject.isRenameAllowed() should return true for
         *         your file type.
         */
        boolean renameAllowed() default true;

        /**
         * If you want to hook into lifecycle methods of DataObjects
         * for your file type, add lookup contents, alter its presentation
         * or be notified on deletion,implement this interface and specify
         * it here.
         *
         * @return A class which implements DataObjectHooks
         */
        Class<? extends DataObjectHooks> hooks() default NoHooks.class;
    }

    /**
     * If your language can have sections of other languages embedded in it, and the
     * IDE should use its support for those languages to syntax-highlight those sections,
     * you can specify these here.
     *
     * @return An array of embedded language details
     */
    Embedding[] embeddedLanguages() default {};

    @interface Embedding {
        /**
         * The ANTLR token ids (int constants on your Antlr-generated lexer class), that
         * may contain the language you are specifying.
         *
         * @return An array of token ids
         */
        int[] tokens();

        /**
         * The mime type of the embedded language; either this, or <code>helper()</code>
         * must be specified - if there is only one language that could possibly be
         * embedded under the token ids you specify, use this; if the contents of the
         * file may be what determines the language (as with Antlr itself, where embedded
         * action code might be Java&trade;, Javascript&trade; or something else),
         * implements {@link EmbeddingHelper}, provide that type from <code>helper()</code>
         * and leave this unspecified.
         *
         * @return A mime type string
         */
        String mimeType() default "";

        /**
         * An alternate way of providing the mime type in the case that multiple languages
         * are possible and the file contents must be examined to determine which.
         *
         * @return The type of an EmbeddingHelper implementation that has a public,
         *         no-argument constructor
         */
        Class<? extends EmbeddingHelper> helper() default EmbeddingHelper.class;

        /**
         * The skip length, as specified in {@link org.netbeans.spi.lexer.LanguageEmbedding}.
         *
         * @return The number of characters to skip at the head of an embedding
         */
        int startSkipLength() default 0;

        /**
         * The skip length, as specified in {@link org.netbeans.spi.lexer.LanguageEmbedding}.
         *
         * @return The number of characters to skip at the tail of an embedding
         */
        int endSkipLength() default 0;

        /**
         * If true, join consecutive tokens that match the same language when parsing.
         *
         * @return true by default
         */
        boolean joinSections() default true;

        /**
         * Hint to the NetBeans lexer infrastructure as to whether the embedding language
         * should be checked every time a matching token encountered, or assumed to be
         * stable for the life of the document.
         *
         * @return by default CACHED_FIRST_QUERY.
         */
        EmbeddingPresence presence() default EmbeddingPresence.CACHED_FIRST_QUERY;
    }
}
