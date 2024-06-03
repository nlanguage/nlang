enum class TokenType
{
    IDENTIFIER,
    NUMERIC,
    BOOLEAN,
    CHARACTER,
    FUN,
    EXTERN,
    RETURN,
    VAL,
    VAR,
    ADD,
    SUB,
    MUL,
    DIV,
    EQUALS,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    COMMA,
    COLON,
    ARROW,
    SQUOTE,
    EOF,
}

data class Token(val type: TokenType, val text: String, val filePos: FilePos)

class Lexer(private val input: String)
{
    // current and lookahead tokens will be valid after prime()

    lateinit var current: Token
        private set

    lateinit var lookahead: Token
        private set

    private var len = input.length;

    private var pos = 0;
    private var line = 1;
    private var col = 1;

    fun isAtEnd(): Boolean = pos >= len;

    private fun peek(): Char
    {
        if (isAtEnd())
        {
            return '\u0000'
        }
        else
        {
            return input[pos]
        }
    }

    private fun advance(): Char
    {
        if (input[pos] == '\n')
        {
            col = 1
            line++
        }
        else
        {
            col++
        }

        pos++;

        return input[pos - 1]
    }

    private fun skipWhitespace()
    {
        while (!isAtEnd() && peek().isWhitespace())
        {
            advance()
        }
    }

    private fun number(): Token
    {
        val start = pos - 1

        while (!isAtEnd() && peek().isDigit())
        {
            advance()
        }

        return Token(TokenType.NUMERIC, input.substring(start, pos), FilePos(line, col))
    }

    // Handles both identifiers and keywords
    private fun identifier(): Token
    {
        val start = pos - 1

        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_'))
        {
            advance()
        }

        val text = input.substring(start, pos)

        val tokenType = when (text)
        {
            "fun"    -> TokenType.FUN
            "@extern" -> TokenType.EXTERN
            "return" -> TokenType.RETURN
            "val"    -> TokenType.VAL
            "var"    -> TokenType.VAR

            "true",
            "false"  -> TokenType.BOOLEAN

            else     -> TokenType.IDENTIFIER
        }

        return Token(tokenType, text, FilePos(line, col))
    }

    fun prime()
    {
        // Eat twice to load a token into 'lookahead' and 'current'
        eat()
        eat()
    }

    fun eatOnMatch(expected: String)
    {
        if (current.text != expected)
        {
            reportError("Parsing", current.filePos, "Expected $expected but found '${current.text}'")
        }

        eat()
    }

    fun eat(): String
    {
        val currentText = if (this::current.isInitialized)
        {
            current.text
        } else {
            ""
        }

        if (this::lookahead.isInitialized)
        {
            current = lookahead
        }

        skipWhitespace()

        if (isAtEnd())
        {
            lookahead = Token(TokenType.EOF, "", FilePos(line, col))
            return currentText
        }

        val cur = advance()

        lookahead = when (cur)
        {
            '*'              -> Token(TokenType.MUL,    cur.toString(), FilePos(line, col))
            '/'              -> Token(TokenType.DIV,    cur.toString(), FilePos(line, col))
            '+'              -> Token(TokenType.ADD,    cur.toString(), FilePos(line, col))
            ')'              -> Token(TokenType.RPAREN, cur.toString(), FilePos(line, col))
            '{'              -> Token(TokenType.LBRACE, cur.toString(), FilePos(line, col))
            '}'              -> Token(TokenType.RBRACE, cur.toString(), FilePos(line, col))
            '('              -> Token(TokenType.LPAREN, cur.toString(), FilePos(line, col))
            ','              -> Token(TokenType.COMMA,  cur.toString(), FilePos(line, col))
            ':'              -> Token(TokenType.COLON,  cur.toString(), FilePos(line, col))
            '='              -> Token(TokenType.EQUALS, cur.toString(), FilePos(line, col))
            '\''             -> Token(TokenType.SQUOTE, cur.toString(), FilePos(line, col))

            '-'              ->
            {
                if (peek() == '>')
                {
                    // Consume '>'
                    advance()
                    Token(TokenType.ARROW, "->", FilePos(line, col))
                }
                else
                {
                    Token(TokenType.SUB, "-", FilePos(line, col))
                }
            }

            in '0'..'9'      -> number()

            in 'a'..'z',
            in 'A'..'Z',
               '_', '@',     -> identifier()

            else             -> reportError("lex", FilePos(line, col), "Unexpected character '$cur'")
        }

        return currentText
    }
}