lexer grammar MarkdownLexer;

@members {
private int indentDepth;
private int lastIndentChange;
private boolean updateIndentDepth() {
    int indents = 0;
    loop: for(int spaces=0, i=-1;; i--) {
        int val = _input.LA(i);
        if (val == CharStream.EOF) {
            indents = 0;
            break;
        }
        char c = (char) val;
        switch(c) {
            case ' ':
                spaces++;
                break;
            case '\t':
                indents++;
                break;
            case '\n':
                int count = spaces / 2;
                if (spaces % 2 != 0) {
                    count++;
                }
                indents+=count;
                break loop;
            default :
                indents = 0;
                spaces = 0;
        }
    }
    boolean result = indents > indentDepth;
    lastIndentChange = indents - indentDepth;
    indentDepth = indents;
    return result;
}

private boolean lastIndentChangeWasNegative() {
    boolean result = lastIndentChange < 0;
    lastIndentChange = 0;
    return result;
}

private void clearIndentDepth() {
    indentDepth = 0;
}
}

OpenHeading
    : POUND+ -> pushMode ( HEADING );

OpenPara
    : ( LETTER | DIGIT | OPEN_BRACE | OPEN_PAREN ) -> more, pushMode ( PARAGRAPH );

OpenBulletList
    : INLINE_WHITESPACE+ ASTERISK -> pushMode ( LIST );

OpenNumberedList
    : INLINE_WHITESPACE+ DIGIT+ DOT { updateIndentDepth(); } -> more, pushMode ( ORDERED_LIST );

OpenBlockQuote
    : INLINE_WHITESPACE* GT { clearIndentDepth(); } -> pushMode ( BLOCKQUOTE );

OpenPreformattedText
    : BACKTICK BACKTICK BACKTICK -> pushMode ( PREFORMATTED );

Whitespace
    : ALL_WS;

OpenHr
    : (( DASH DASH DASH+ ) | ( ASTERISK ASTERISK ASTERISK+ ) | ( DASH SPACE DASH
        SPACE ( DASH SPACE )+ ) | ( ASTERISK SPACE ASTERISK SPACE ( ASTERISK
        SPACE )+ SPACE* )) { clearIndentDepth(); } -> more, mode ( HR );


mode PREFORMATTED;

PreformattedContent
    :~[`]+
    | ( BACKTICK BACKTICK~[`]? )+;

ClosePreformattedContent
    : BACKTICK BACKTICK BACKTICK -> popMode;


mode HR;

HorizontalRule
    : (( DASH DASH DASH+ ) | ( ASTERISK ASTERISK ASTERISK+ ) | ( DASH SPACE DASH
        SPACE ( DASH SPACE )+ ) | ( ASTERISK SPACE ASTERISK SPACE ( ASTERISK
        SPACE )+ SPACE* )) HorizontalRuleTail;

HorizontalRuleTail
    : NEWLINE;

HrExit
    : NON_HR -> more, mode ( DEFAULT_MODE );


mode PARAGRAPH;

ParaItalic
    : NEWLINE? UNDERSCORE SAFE_PUNCTUATION?;

ParaBold
    : NEWLINE? ASTERISK SAFE_PUNCTUATION?;

ParaStrikethrough
    : NEWLINE? STRIKE SAFE_PUNCTUATION?;

ParaCode
    : NEWLINE? BACKTICK SAFE_PUNCTUATION?;

ParaBracketOpen
    : NEWLINE? SAFE_PUNCTUATION? OPEN_BRACKET;

ParaBracketClose
    : NEWLINE? CLOSE_BRACKET SAFE_PUNCTUATION?;

ParaOpenParen
    : NEWLINE? SAFE_PUNCTUATION? OPEN_PAREN;

ParaCloseParen
    : NEWLINE? CLOSE_PAREN SAFE_PUNCTUATION?;

ParaLink
    : LINK_TEXT+ COLON SLASH SLASH? LINK_TEXT+;

ParaHorizontalRule
    : NEWLINE? (( DASH DASH DASH+ ) | ( ASTERISK ASTERISK ASTERISK+ ) | ( DASH
        SPACE DASH SPACE ( DASH SPACE )+ ) | ( ASTERISK SPACE ASTERISK SPACE ( ASTERISK
        SPACE )+ SPACE* )) NEWLINE -> more, mode ( HR );

ParaBangBracket
    : BANG OPEN_BRACKET;

ParaWords
    : ( WORD_LIKE SAFE_PUNCTUATION? ( NEWLINE*? INLINE_WHITESPACE+ WORD_LIKE
        SAFE_PUNCTUATION? )* )
    | ( WORD_LIKE SAFE_PUNCTUATION? ( INLINE_WHITESPACE*? WORD_LIKE
        SAFE_PUNCTUATION? )* )
    | ( WORD_LIKE SAFE_PUNCTUATION? ( NEWLINE*? WORD_LIKE SAFE_PUNCTUATION? )* )
    | ( SAFE_PUNCTUATION+? INLINE_WHITESPACE?? )
    | ( INLINE_WHITESPACE DIGIT+? INLINE_WHITESPACE?? );

ParaTransitionToBulletListItem
    : ( ParaDoubleNewline | NEWLINE ) INLINE_WHITESPACE+ ASTERISK -> more, mode ( LIST );

ParaTransitionToOrderedListItem
    : ( ParaDoubleNewline | NEWLINE ) INLINE_WHITESPACE+ DIGIT DOT -> more, mode
        ( ORDERED_LIST );

ParaNewline
    : NEWLINE -> more, popMode;

ParaBlockquoteHead
    : ( NEWLINE GT ) -> mode ( BLOCKQUOTE );

ParaHeadingHead
    : ( NEWLINE POUND+ ) -> mode ( HEADING );

ParaBreak
    : ParaDoubleNewline -> mode ( DEFAULT_MODE );

ParaDoubleNewline
    : INLINE_WHITESPACE*? NEWLINE INLINE_WHITESPACE*? NEWLINE ( INLINE_WHITESPACE*?
        NEWLINE )*?;

ParaInlineWhitespace
    : INLINE_WHITESPACE+;

ParaEof
    : EOF -> more, popMode;


mode BLOCKQUOTE;

BlockQuotePrologue
    : INLINE_WHITESPACE;

BlockQuote
    : NON_WHITESPACE -> more, mode ( PARAGRAPH );

BlockquoteDoubleNewline
    : INLINE_WHITESPACE* NEWLINE INLINE_WHITESPACE* NEWLINE ( INLINE_WHITESPACE*?
        NEWLINE )* -> popMode;


mode ORDERED_LIST;

NestedOrderedListPrologue
    : (( DOUBLE_NEWLINE? INLINE_WHITESPACE ) | ( ParaDoubleNewline | NEWLINE )?
        INLINE_WHITESPACE+ DIGIT DOT INLINE_WHITESPACE ) { updateIndentDepth() }?;

ReturningOrderedListPrologue
    : (( DOUBLE_NEWLINE? INLINE_WHITESPACE ) | ( ParaDoubleNewline | NEWLINE )?
        INLINE_WHITESPACE+ DIGIT DOT INLINE_WHITESPACE ) { lastIndentChangeWasNegative() }?;

OrderedListPrologue
    : ( DOUBLE_NEWLINE? INLINE_WHITESPACE )
    | ( ParaDoubleNewline | NEWLINE )? INLINE_WHITESPACE+ DIGIT DOT
        INLINE_WHITESPACE;

OrderedListItem
    : ( LETTER | DIGIT ) -> more, pushMode ( PARAGRAPH );

OrderedNestedListItemHead
    : ( INLINE_WHITESPACE+ DIGIT+ DOT ) INLINE_WHITESPACE;

OrderedListHorizontalRule
    : NEWLINE? (( DASH DASH DASH+ ) | ( ASTERISK ASTERISK ASTERISK+ ) | ( DASH
        SPACE DASH SPACE ( DASH SPACE )+ NEWLINE ) | ( ASTERISK SPACE ASTERISK
        SPACE ( ASTERISK SPACE )+ SPACE* )) NEWLINE -> more, mode ( HR );

CloseOrderedListItem
    : DOUBLE_NEWLINE { clearIndentDepth(); } -> popMode;

CloseOrderedListAndForward
    : NEWLINE NON_WHITESPACE -> more, popMode;


mode LIST;

ListPrologue
    : (( DOUBLE_NEWLINE? INLINE_WHITESPACE )
    | (NEWLINE+? INLINE_WHITESPACE+ ASTERISK INLINE_WHITESPACE));

ListItem
    : ( LETTER | DIGIT )+ -> more, pushMode ( PARAGRAPH );

NestedListItemHead
    : (( INLINE_WHITESPACE+ ASTERISK ) | ( INLINE_WHITESPACE+ DIGIT+ DOT ))
        INLINE_WHITESPACE;

ListHorizontalRule
    : NEWLINE? (( DASH DASH DASH+ ) | ( ASTERISK ASTERISK ASTERISK+ ) | ( DASH
        SPACE DASH SPACE ( DASH SPACE )+ NEWLINE ) | ( ASTERISK SPACE ASTERISK
        SPACE ( ASTERISK SPACE )+ SPACE* )) NEWLINE -> more, mode ( HR );

CloseList
    : DOUBLE_NEWLINE -> popMode;

CloseList2
    : NEWLINE NON_WHITESPACE -> more, popMode;


mode HEADING;

HeadingPrologue
    : INLINE_WHITESPACE;

HeadingContent
    : WORD_LIKE PUNCTUATION? ( INLINE_WHITESPACE*? WORD_LIKE SAFE_PUNCTUATION? )+;

HeadingClose
    : NEWLINE NEWLINE* -> mode ( DEFAULT_MODE );

//    : NEWLINE NEWLINE*? -> mode ( DEFAULT_MODE );
HeadingWordLike
    : ( LETTER | DIGIT | PUNCTUATION )( LETTER | DIGIT | PUNCTUATION )*;

fragment WORDS
    : INLINE_WHITESPACE? WORD_LIKE SAFE_PUNCTUATION? INLINE_WHITESPACE??;

// allow a single underscore or asterisk in a word, i.e. _FOO_BAR_ is "FOO_BAR" italicized, 
// but "_FOO_BAR" is "FOO_BAR" with FOO italicized.
// Note the first alternative must not allow trailing punctuation, or for example,
// a sentence like "Make the wheel _rounder_." will get the first _ parsed as an
// opending italic token, and then "rounder_." treated as a word.
fragment WORD_LIKE
    : (( LETTER )+ ( LETTER | DIGIT | SAFE_PUNCTUATION )* ( LETTER | DIGIT |
        SAFE_PUNCTUATION | UNDERSCORE | ASTERISK )( LETTER | DIGIT )+ )
    | ( LETTER )( LETTER | DIGIT | SAFE_PUNCTUATION )*;

fragment WORD_OR_NUMBER_LIKE
    : ( LETTER | DIGIT | PUNCTUATION )+;

fragment SAFE_PUNCTUATION
    : [><{}/\\:;,+!@.$%^&\-='"?¿¡]
    | '\\`'
    | '\\*';

fragment PUNCTUATION
    : [\p{Punctuation}];

fragment LETTER
    : [\p{Alphabetic}];

fragment DIGIT
    : [\p{Digit}];

fragment NON_HR
    :~[ -*];

fragment ALL_WS
    : INLINE_WHITESPACE
    | NEWLINE;

fragment DOUBLE_NEWLINE
    : NEWLINE NEWLINE NEWLINE*?;

fragment NEWLINE
    : '\n';

fragment SPECIAL_CHARS
    : ASTERISK
    | STRIKE
    | BACKTICK
    | POUND
    | STRIKE;

fragment NON_WHITESPACE
    :~[ \r\n\t];

//    :~[\p{White_Space}];
fragment LINK_TEXT
    : [a-zA-Z0-9\-_$/\\.!?+=&^%#];

fragment PRE
    : '```';

fragment INLINE_WHITESPACE
    : SPACE
    | TAB
    | CARRIAGE_RETURN;

fragment SPACE
    : ' ';

fragment TAB
    : '\t';

fragment CARRIAGE_RETURN
    : '\r';

fragment BACKSLASH
    : '\\';

fragment SQUOTE
    : '\'';

fragment DQUOTE
    : '"';

fragment OPEN_PAREN
    : '(';

fragment CLOSE_PAREN
    : ')';

fragment OPEN_BRACE
    : '{';

fragment CLOSE_BRACE
    : '}';

fragment OPEN_BRACKET
    : '[';

fragment CLOSE_BRACKET
    : ']';

fragment COLON
    : ':';

fragment AMPERSAND
    : '&';

fragment COMMA
    : ',';

fragment PLUS
    : '+';

fragment DOLLARS
    : '$';

fragment PERCENT
    : '%';

fragment CAREN
    : '^';

fragment AT
    : '@';

fragment BANG
    : '!';

fragment GT
    : '>';

fragment LT
    : '<';

fragment QUESTION
    : '?';

fragment SEMICOLON
    : ';';

fragment SLASH
    : '/';

fragment UNDERSCORE
    : '_';

fragment STRIKE
    : '~~';

fragment BACKTICK
    : '`';

fragment DASH
    : '-';

fragment EQUALS
    : '=';

fragment ASTERISK
    : '*';

fragment POUND
    : '#';

fragment DOT
    : '.';