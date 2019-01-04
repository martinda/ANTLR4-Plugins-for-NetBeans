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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.openide.util.Exceptions;


/**
 *
 * @author Fred Yvon Vinet inspired from James Reid work
 */
public class ANTLRv4TokenFileReader {
    private final HashMap<String, String> tokenTypes;
    private final ArrayList<ANTLRv4TokenId> tokens;

    public ANTLRv4TokenFileReader() {
        tokenTypes = new HashMap<>();
        tokens = new ArrayList<>();
        init();
    }

    /**
     * Initializes the map to include any keywords in the ANTLR language.
     */
    private void init() {
        tokenTypes.put("TOKEN_ID", "token");
        tokenTypes.put("PARSER_RULE_ID", "parserRuleIdentifier");
        tokenTypes.put("LEXER_CHAR_SET", "lexerCharSet");
        
        tokenTypes.put("DOC_COMMENT", "comment");
        tokenTypes.put("BLOCK_COMMENT", "comment");
        tokenTypes.put("LINE_COMMENT", "comment");
        
        tokenTypes.put("INT", "literal");
        tokenTypes.put("STRING_LITERAL", "literal");
        tokenTypes.put("UNTERMINATED_STRING_LITERAL", "literal");
        
        tokenTypes.put("BEGIN_ARGUMENT", "punctuation");
        tokenTypes.put("BEGIN_ACTION", "punctuation");
        
        tokenTypes.put("OPTIONS", "keyword");
        tokenTypes.put("LANGUAGE", "keyword");
        tokenTypes.put("SUPER_CLASS", "keyword");
        tokenTypes.put("TOKEN_VOCAB", "keyword");
        tokenTypes.put("TOKEN_LABEL_TYPE", "keyword");
        tokenTypes.put("TOKENS", "keyword");
        tokenTypes.put("CHANNELS", "keyword");
        tokenTypes.put("IMPORT", "keyword");
        tokenTypes.put("FRAGMENT", "keyword");
        tokenTypes.put("LEXER", "keyword");
        tokenTypes.put("PARSER", "keyword");
        tokenTypes.put("GRAMMAR", "keyword");
        tokenTypes.put("PROTECTED", "keyword");
        tokenTypes.put("PUBLIC", "keyword");
        tokenTypes.put("PRIVATE", "keyword");
        tokenTypes.put("RETURNS", "keyword");
        tokenTypes.put("LOCALS", "keyword");
        tokenTypes.put("INIT", "keyword");
        tokenTypes.put("AFTER", "keyword");
        tokenTypes.put("THROWS", "keyword");
        tokenTypes.put("CATCH", "keyword");
        tokenTypes.put("FINALLY", "keyword");
        tokenTypes.put("MODE", "keyword");
        tokenTypes.put("LEXCOM_SKIP", "keyword");
        tokenTypes.put("LEXCOM_MORE", "keyword");
        tokenTypes.put("LEXCOM_TYPE", "keyword");
        tokenTypes.put("LEXCOM_CHANNEL", "keyword");
        tokenTypes.put("LEXCOM_MODE", "keyword");
        tokenTypes.put("LEXCOM_PUSHMODE", "keyword");
        tokenTypes.put("LEXCOM_POPMODE", "keyword");
        tokenTypes.put("ASSOC", "keyword");
        tokenTypes.put("RIGHT", "keyword");
        tokenTypes.put("LEFT", "keyword");
        tokenTypes.put("FAIL", "keyword");
        tokenTypes.put("HEADER", "keyword");
        tokenTypes.put("MEMBERS", "keyword");
        
        tokenTypes.put("COLON", "punctuation");
        tokenTypes.put("COLONCOLON", "punctuation");
        tokenTypes.put("COMMA", "punctuation");
        tokenTypes.put("SEMI", "punctuation");
        tokenTypes.put("LPAREN", "punctuation");
        tokenTypes.put("RPAREN", "punctuation");
        tokenTypes.put("LBRACE", "punctuation");
        tokenTypes.put("RBRACE", "punctuation");
        tokenTypes.put("RARROW", "punctuation");
        tokenTypes.put("LT", "punctuation");
        tokenTypes.put("GT", "punctuation");
        tokenTypes.put("ASSIGN", "punctuation");
        tokenTypes.put("QUESTION", "punctuation");
        tokenTypes.put("STAR", "punctuation");
        tokenTypes.put("PLUS_ASSIGN", "punctuation");
        tokenTypes.put("PLUS", "punctuation");
        tokenTypes.put("OR", "punctuation");
        tokenTypes.put("DOLLAR", "punctuation");
        tokenTypes.put("RANGE", "punctuation");
        tokenTypes.put("DOT", "punctuation");
        tokenTypes.put("AT", "punctuation");
        tokenTypes.put("SHARP", "punctuation");
        tokenTypes.put("NOT", "punctuation");
        
        tokenTypes.put("ID", "identifier");
        
        tokenTypes.put("WS", "");
        
        tokenTypes.put("ERRCHAR", "");
        tokenTypes.put("END_ARGUMENT", "punctuation");
        tokenTypes.put("UNTERMINATED_ARGUMENT", "");
        tokenTypes.put("ARGUMENT_CONTENT", "");
        tokenTypes.put("END_ACTION", "punctuation");
        tokenTypes.put("UNTERMINATED_ACTION", "");
        tokenTypes.put("ACTION_CONTENT", "");
        tokenTypes.put("UNTERMINATED_CHAR_SET", "");
    }

    /**
     * Reads the token file from the ANTLR parser and generates
     * appropriate tokens.
     *
     * @return
     */
    public List<ANTLRv4TokenId> readTokenFile() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inp = classLoader.getResourceAsStream("org/nemesis/antlr/v4/netbeans/v8/grammar/code/coloring/impl/ANTLRv4Lexer.tokens");
        BufferedReader input = new BufferedReader(new InputStreamReader(inp));
        readTokenFile(input);
        return tokens;
    }

    /**
     * Reads in the token file.
     *
     * @param buff
     */
    private void readTokenFile(BufferedReader buff) {
        String line;
        try {
            while ((line = buff.readLine()) != null) {
                String[] splLine = line.split("=");
                String name = splLine[0];
             // If the token name starts with ' character then it is a doublon
             // so it is not added in the token list
                if (!name.startsWith("'")) {
                    int tok = Integer.parseInt(splLine[1].trim());
                    ANTLRv4TokenId id;
                    String tokenCategory = tokenTypes.get(name);
                    if (tokenCategory != null) {
                      //if the value exists, put it in the correct category
                        id = new ANTLRv4TokenId(name, tokenCategory, tok);
                    } else {
                      //if we don't recognize the token, consider it to a separator
                        id = new ANTLRv4TokenId(name, "separator", tok);
                    }
                 // add it into the vector of tokens
                    tokens.add(id);
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}