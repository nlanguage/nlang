enum class TokenType
{
    IDENTIFIER,
    NUMERIC,
    FUN,
    EXTERN,
    RETURN,
    ADD,
    SUB,
    MUL,
    DIV,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    COMMA,
    EOF,
}

data class Token(val type: TokenType, val text: String, val position: Int)

class UnexpectedCharException(message: String) : Exception(message)

class Lexer(private val input: String)
{
    // Will be valid after first call to eat();
    lateinit var currentTok: Token
        private set

    private var pos: Int = 0;
    private var len: Int = input.length;

    public fun isAtEnd(): Boolean = pos >= len;

    private fun peek(): Char = if (isAtEnd()) '\u0000' else input[pos]

    private fun advance(): Char
    {
        pos++;
        return input[pos - 1]
    }

/*
    private fun match(expected: Char): Boolean
    {
        if (isAtEnd() || input[pos] != expected)
        {
            return false
        }

        pos++

        return true
    }
*/

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

        return Token(TokenType.NUMERIC, input.substring(start, pos), start)
    }

    // Handles both identifiers and keywords
    private fun identifier(): Token
    {
        val start = pos - 1

        while (!isAtEnd() && peek().isLetterOrDigit())
        {
            advance()
        }

        val text = input.substring(start, pos)

        val tokenType = when (text)
        {
            "fun" -> TokenType.FUN
            "extern" -> TokenType.EXTERN
            "return" -> TokenType.RETURN
            else -> TokenType.IDENTIFIER
        }

        return Token(tokenType, text, start)
    }

    fun eat()
    {
        skipWhitespace()

        if (isAtEnd())
        {
            currentTok = Token(TokenType.EOF, "", pos)
            return
        }

        val cur = advance()

        currentTok = when (cur)
        {
            '-' -> Token(TokenType.SUB, cur.toString(), pos - 1)
            '*' -> Token(TokenType.MUL, cur.toString(), pos - 1)
            '/' -> Token(TokenType.DIV, cur.toString(), pos - 1)
            '+' -> Token(TokenType.ADD, cur.toString(), pos - 1)
            '(' -> Token(TokenType.LPAREN, cur.toString(), pos - 1)
            ')' -> Token(TokenType.RPAREN, cur.toString(), pos - 1)
            '{' -> Token(TokenType.LBRACE, cur.toString(), pos - 1)
            '}' -> Token(TokenType.RBRACE, cur.toString(), pos - 1)
            ',' -> Token(TokenType.COMMA, cur.toString(), pos - 1)
            in '0'..'9' -> number()
            in 'a'..'z', in 'A'..'Z', '_' -> identifier()
            else -> throw UnexpectedCharException("Unexpected character: $cur at position $pos")
        }
    }
}