enum class TokenType
{
    IDENTIFIER,
    NUMERIC,
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

    private fun peek(): Char = if (isAtEnd()) '\u0000' else input[pos]

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
            "extern" -> TokenType.EXTERN
            "return" -> TokenType.RETURN
            "val"    -> TokenType.VAL
            "var"    -> TokenType.VAR
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

    fun eat()
    {
        if (this::lookahead.isInitialized)
        {
            current = lookahead
        }

        skipWhitespace()

        if (isAtEnd())
        {
            lookahead = Token(TokenType.EOF, "", FilePos(line, col))
            return
        }

        val cur = advance()

        lookahead = when (cur)
        {
            '*'              -> Token(TokenType.MUL, cur.toString(), FilePos(line, col))
            '/'              -> Token(TokenType.DIV, cur.toString(), FilePos(line, col))
            '+'              -> Token(TokenType.ADD, cur.toString(), FilePos(line, col))
            '-'              -> Token(TokenType.SUB, cur.toString(), FilePos(line, col))
            ')'              -> Token(TokenType.RPAREN, cur.toString(), FilePos(line, col))
            '{'              -> Token(TokenType.LBRACE, cur.toString(), FilePos(line, col))
            '}'              -> Token(TokenType.RBRACE, cur.toString(), FilePos(line, col))
            '('              -> Token(TokenType.LPAREN, cur.toString(), FilePos(line, col))
            ','              -> Token(TokenType.COMMA, cur.toString(), FilePos(line, col))
            '='              -> Token(TokenType.EQUALS, cur.toString(), FilePos(line, col))

            in '0'..'9'      -> number()

            in 'a'..'z',
            in 'A'..'Z', '_' -> identifier()

            else             -> reportError("Lex", FilePos(line, col), "Unexpected character '$cur'")
        }
    }
}