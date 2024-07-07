import ast.FilePos

enum class TokenType
{
    IDENTIFIER,
    NUMERIC,
    BOOLEAN,
    CHARACTER,
    STRING,
    OPERATOR,
    DELIMITER,
    KEYWORD,
    EOF

}

data class Token(
    val type: TokenType,
    val text: String,
    val pos: FilePos
)

class Lexer(private val name: String, private val input: String)
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
        while (!isAtEnd())
        {
            when (peek())
            {
                ' ', '\n', '\r', '\t' -> advance()

                '/' ->
                {
                    if (pos + 1 < len && input[pos + 1] == '/')
                    {
                        skipComment()
                    }
                    else
                    {
                        return
                    }
                }

                else -> return
            }
        }
    }

    private fun skipComment()
    {
        while (!isAtEnd() && peek() != '\n')
        {
            advance()
        }
    }

    private fun handleNumeric(): Token
    {
        val start = pos - 1

        while (!isAtEnd() && peek().isDigit())
        {
            advance()
        }

        return Token(
            TokenType.NUMERIC,
            input.substring(start, pos),
            FilePos(name, line, col)
        )
    }

    // Handles both identifiers and keywords
    private fun handleAlpha(): Token
    {
        val start = pos - 1

        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_'))
        {
            advance()
        }

        val text = input.substring(start, pos)

        val tokenType = when (text)
        {
            "fun",
            "return",
            "val",
            "var",
            "loop",
            "else",
            "when",
            "class",
            "import"  -> TokenType.KEYWORD

            "true",
            "false"   -> TokenType.BOOLEAN

            else      -> TokenType.IDENTIFIER
        }

        return Token(tokenType, text, FilePos(name, line, col))
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
            reportError("parsing", current.pos, "Expected '$expected' but found '${current.text}'")
        }

        eat()
    }

    fun eat(): String
    {
        val currentText = if (this::current.isInitialized)
        {
            current.text
        }
        else
        {
            ""
        }

        if (this::lookahead.isInitialized)
        {
            current = lookahead
        }

        skipWhitespace()

        if (isAtEnd())
        {
            lookahead = Token(TokenType.EOF, "", FilePos(name, line, col))
            return currentText
        }

        var cur = advance()

        lookahead = when (cur)
        {
            '+' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.DELIMITER, "+=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, "+", FilePos(name, line, col))
                }
            }

            '-' ->
            {
                if (peek() == '>')
                {
                    // Consume '>'
                    advance()
                    Token(TokenType.DELIMITER, "->", FilePos(name, line, col))
                }
                else if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.DELIMITER, "-=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, "-", FilePos(name, line, col))
                }
            }

            '*' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.DELIMITER, "*=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, "*", FilePos(name, line, col))
                }
            }

            '/' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.DELIMITER, "/=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, "/", FilePos(name, line, col))
                }
            }

            ')',
            '{',
            '}',
            '(',
            ',',
            ':',
            '@' -> Token(TokenType.DELIMITER, cur.toString(), FilePos(name, line, col))

            '=' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.OPERATOR, "==", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, cur.toString(), FilePos(name, line, col))
                }
            }

            '>' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.OPERATOR, ">=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, cur.toString(), FilePos(name, line, col))
                }
            }

            '<' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.OPERATOR, "<=", FilePos(name, line, col))
                }
                else
                {
                    Token(TokenType.OPERATOR, cur.toString(), FilePos(name, line, col))
                }
            }

            '!' ->
            {
                if (peek() == '=')
                {
                    // Consume '='
                    advance()
                    Token(TokenType.OPERATOR, "!=", FilePos(name, line, col))
                }
                else
                {
                    reportError("lex", FilePos(name, line, col), "Unexpected character '$cur'")
                }
            }

            '.' -> Token(TokenType.OPERATOR, ".", FilePos(name, line, col))

            '\'' ->
            {
                val value = advance().toString()

                if (advance() != '\'')
                {
                    reportError("lex", FilePos(name, line, col), "Expected ''' to terminate char literal")
                }

                Token(TokenType.CHARACTER, value, FilePos(name, line, col))
            }

            '"' ->
            {
                val start = pos

                while (advance() != '"') {}

                Token(TokenType.STRING, input.substring(start, pos - 1), FilePos(name, line, col))
            }


            in '0'..'9'  -> handleNumeric()

            in 'a'..'z',
            in 'A'..'Z',
            '_',
                         -> handleAlpha()

            else         -> reportError("lex", FilePos(name, line, col), "Unexpected character '$cur'")
        }

        return currentText
    }
}