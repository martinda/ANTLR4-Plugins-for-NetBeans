/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.language.code.completion;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.text.Document;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.FRAGDEC_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.PARSER_RULE_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKDEC_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKEN_ID;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.completion.AntlrCompletionProvider;
import org.nemesis.antlr.completion.CompletionItemProvider;
import org.nemesis.antlr.completion.StringKind;
import org.nemesis.antlr.completion.TokenMatch;
import org.nemesis.antlr.completion.annos.InsertAction;
import org.nemesis.antlr.file.AntlrHierarchy;
import org.nemesis.antlr.language.code.completion.RulesAndTokensCompletions.AntlrCompletionItem;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.CompletionProvider;

/**
 *
 * @author Tim Boudreau
 */
public class RulesAndTokensCompletions implements CompletionItemProvider<AntlrCompletionItem> {

    private static final String AT_TOKEN_COLON = "tokenRules";
    private static final String AT_PARSER_RULE_COLON = "parserRules";
    private static final String IN_NO_RULE = "noRule";

    @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = CompletionProvider.class)
    public static CompletionProvider get() {
        RulesAndTokensCompletions c = new RulesAndTokensCompletions();

        CompletionProvider result = AntlrCompletionProvider.builder(RulesAndTokensCompletions::lexerFor)
                .<AntlrCompletionItem>add()
                .whenPrecedingTokensMatch(AT_PARSER_RULE_COLON, ANTLRv4Lexer.PARDEC_WS)
                .whenPrecedingTokensMatch(AT_PARSER_RULE_COLON, ANTLRv4Lexer.COLON)
                .withInsertAction(InsertAction.INSERT_AFTER_CURRENT_TOKEN)
                .andNoNextTokenMatchNeeded()
                .whenPrecedingTokensMatch(AT_TOKEN_COLON, ANTLRv4Lexer.TOKDEC_WS)
                .whenPrecedingTokensMatch(AT_TOKEN_COLON, ANTLRv4Lexer.COLON)
                .withInsertAction(InsertAction.INSERT_BEFORE_CURRENT_TOKEN)
                .andNoNextTokenMatchNeeded()
                .build()
                .setStringifier(RulesAndTokensCompletions::stringify)
                .build(c)
                .build();
        return result;
    }

    static String stringify(StringKind kind, AntlrCompletionItem item) {
        switch (kind) {
            case TEXT_TO_INSERT:
                return item.toString() + " ";
            case DISPLAY_DIFFERENTIATOR:
                return item.kind();
            case DISPLAY_NAME:
                return item.toString();
            case SORT_TEXT:
                return item.toString().toLowerCase();
        }
        return item.toString();
    }

    @Override
    public Collection<AntlrCompletionItem> fetch(Document document, int caretPosition, List<Token> tokens, int[] tokenFrequencies, Token caretToken, TokenMatch tokenPatternMatchName) throws Exception {
        Set<AntlrCompletionItem> result = null;
        for (Token tk : tokens) {
            if (tk == caretToken) {
                continue;
            }
            switch (tokenPatternMatchName.name()) {
                case AT_TOKEN_COLON:
                    switch (tk.getType()) {
                        case ANTLRv4Lexer.TOKEN_ID:
                            if (result == null) {
                                result = new LinkedHashSet<>(10);
                            }
                            result.add(new TokenRulesCompletionItem(tk.getText(), tk.getType()));
                            break;
                    }
                    break;
                case AT_PARSER_RULE_COLON:
                    switch (tk.getType()) {
                        case ANTLRv4Lexer.PARSER_RULE_ID:
                        case ANTLRv4Lexer.TOKEN_ID:
                            if (result == null) {
                                result = new LinkedHashSet<>(10);
                            }
                            result.add(new ParserRulesCompletionItem(tk.getText(), tk.getType()));
                            break;
                    }
            }
        }
        System.out.println("FETCH FOR " + tokenPatternMatchName + ": " + result + " from " + tokens.size());
        return result == null ? Collections.emptyList() : result;
    }

    private static Lexer lexerFor(Document doc) throws IOException {
        GrammarSource<?> src = GrammarSource.find(doc, ANTLR_MIME_TYPE);
        return AntlrHierarchy.createAntlrLexer(src.stream());
    }

    static int compareCompletionItems(AntlrCompletionItem a, AntlrCompletionItem b) {
        if (a instanceof ParserRulesCompletionItem && b instanceof TokenRulesCompletionItem) {
            return -1;
        } else if (a instanceof TokenRulesCompletionItem && b instanceof ParserRulesCompletionItem) {
            return 1;
        } else {
            return a.toString().compareToIgnoreCase(b.toString());
        }
    }

    interface AntlrCompletionItem {

        String kind();
    }

    static class TokenRulesCompletionItem implements AntlrCompletionItem {

        private final String text;
        private final int type;

        public TokenRulesCompletionItem(String text, int type) {
            this.text = text;
            this.type = type;
        }

        @Override
        public String kind() {
            switch (type) {
                case FRAGDEC_ID:
                    return "(fragment)";
                case TOKDEC_ID:
                case TOKEN_ID:
                    return "(lexer)";
                case PARSER_RULE_ID:
                case PARDEC_ID:
                    return "(parser)";
            }
            return "Token";
        }

        @Override
        public String toString() {
            return text;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 29 * hash + Objects.hashCode(this.text);
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
            final TokenRulesCompletionItem other = (TokenRulesCompletionItem) obj;
            return Objects.equals(this.text, other.text);
        }
    }

    static class ParserRulesCompletionItem implements AntlrCompletionItem {

        private final String text;
        private final int type;

        public ParserRulesCompletionItem(String text, int type) {
            this.text = text;
            this.type = type;
        }

        @Override
        public String toString() {
            return text;
        }

        @Override
        public String kind() {
            switch (type) {
                case FRAGDEC_ID:
                    return "(fragment)";
                case TOKDEC_ID:
                case TOKEN_ID:
                    return "(lexer)";
                case PARSER_RULE_ID:
                case PARDEC_ID:
                    return "(parser)";
            }
            return "Token";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.text);
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
            final ParserRulesCompletionItem other = (ParserRulesCompletionItem) obj;
            return Objects.equals(this.text, other.text);
        }
    }
}