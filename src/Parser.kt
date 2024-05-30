class UnexpectedTokenException(message: String) : Exception(message)

class Parser(private val lexer: Lexer)
{
    fun parseProgram(): Program
    {
        val nodes = mutableListOf<AstNode>()
        while (!lexer.isAtEnd())
        {
            when (lexer.currentTok.type)
            {
                TokenType.EXTERN -> nodes += parseExtern()
                TokenType.FUN -> nodes += parseDefinition()
                else -> throw UnexpectedTokenException("Expected 'fun' or 'extern', got ${lexer.currentTok.text}")
            }
        }

        return Program(nodes)
    }

    private fun parseExtern(): Prototype
    {
        // Consume 'extern'
        lexer.eat()

        // Consume 'fun'
        lexer.eat()

        return parsePrototype()
    }

    private fun parseDefinition(): Function
    {
        // Consume 'fun'
        lexer.eat()

        val proto = parsePrototype()

        val body = parseBlock()

        return Function(proto, body)
    }

    private fun parsePrototype(): Prototype
    {
        val name = lexer.currentTok.text

        // Consume identifier
        lexer.eat()

        if (lexer.currentTok.type != TokenType.LPAREN)
        {
            throw UnexpectedTokenException("Expected '(', got ${lexer.currentTok.text}")
        }

        // Consume '('
        lexer.eat()

        val args = mutableListOf<String>()
        if (lexer.currentTok.type != TokenType.RPAREN) {
            while (true) {
                args += lexer.currentTok.text

                // Consume identifier
                lexer.eat()

                if (lexer.currentTok.type == TokenType.RPAREN) {
                    break;
                }

                if (lexer.currentTok.type != TokenType.COMMA) {
                    throw UnexpectedTokenException("Expected ',' but found ${lexer.currentTok.text}")
                }

                // Consume ','
                lexer.eat()
            }
        }

        // Consume ')'
        lexer.eat()

        return Prototype(name, args)
    }

    private fun parseBlock(): Block
    {
        // consume '{'
        lexer.eat()

        val statements = mutableListOf<Statement>()

        while (lexer.currentTok.type != TokenType.RBRACE)
        {
            when (lexer.currentTok.type)
            {
                TokenType.RETURN -> statements += parseReturnStatement()
                else -> throw UnexpectedTokenException("Expected statement, got ${lexer.currentTok.type}")
            }
        }

        // consume '}'
        lexer.eat()

        return Block(statements)
    }

    private fun parseReturnStatement(): Statement
    {
        // Consume 'return'
        lexer.eat()

        return ReturnStatement(parseExpr())
    }

    private fun parseExpr(): Expr
    {
        return parseBinOpRhs(0, parsePrimary())
    }

    private fun parseBinOpRhs(exprPrec: Int, inLhs: Expr): Expr
    {
        // Parameters are immutable in kotlin
        var lhs = inLhs

        while (true)
        {
            val tokPrec = getCurTokPrecedence()

            // Return the left-hand-side if it doesn't bind as tightly (or more)
            // This will also return if the current token is not an operator (see operation of getCurTokPrecedence()),
            // which is valid behavior as 'x' is an expression, same as 'x + 2' is one
            if (tokPrec < exprPrec)
            {
                return lhs
            }

            val operator = lexer.currentTok.text

            // Consume binary operator ('+', '-', etc.)
            lexer.eat()

            var rhs = parsePrimary()

            val nextPrec = getCurTokPrecedence()

            if (tokPrec < nextPrec)
            {
                rhs = parseBinOpRhs(tokPrec + 1, rhs)
            }

            lhs = BinaryExpr(lhs, operator, rhs)
        }
    }

    private fun parsePrimary(): Expr
    {
        return when (lexer.currentTok.type)
        {
            TokenType.IDENTIFIER -> parseIdent()
            TokenType.NUMERIC -> parseNumeric()
            TokenType.LPAREN -> parseParen()
            else -> throw UnexpectedTokenException("Expected expression, got ${lexer.currentTok.type}")
        }
    }

    private fun parseParen(): Expr
    {
        // Consume '('
        lexer.eat()

        // parseExpr() will consume any tokens between '(' and ')'
        val inner = parseExpr()

        if (lexer.currentTok.type != TokenType.RPAREN)
        {
            throw UnexpectedTokenException("Expected ')'")
        }

        // Consume ')'
        lexer.eat()

        return inner
    }

    private fun parseNumeric(): Expr
    {
        val result = NumberExpr(lexer.currentTok.text.toBigDecimal());

        // Consume Number
        lexer.eat()

        return result
    }

    // Parses either a variable or a function call
    private fun parseIdent(): Expr
    {
        val ident = lexer.currentTok.text

        // Consume Identifier
        lexer.eat()

        // Variable
        if (lexer.currentTok.type != TokenType.LPAREN)
        {
            return VariableExpr(ident)
        }

        // Function call

        // Consume '('
        lexer.eat()

        val args = mutableListOf<Expr>()
        if (lexer.currentTok.type != TokenType.RPAREN)
        {
            while (true)
            {
                args += parseExpr()

                if (lexer.currentTok.type == TokenType.RPAREN)
                {
                    break;
                }

                if (lexer.currentTok.type != TokenType.COMMA)
                {
                    throw UnexpectedTokenException("Expected ',' but found ${lexer.currentTok.text}")
                }

                // Consume ','
                lexer.eat()
            }
        }

        // Consume ')'
        lexer.eat()

        return CallExpr(ident, args)
    }

    // Get the precedence of the current token (lexer.currentTok)
    private fun getCurTokPrecedence(): Int
    {
        // -1 is used a comparison
        return opPrecedence[lexer.currentTok.text] ?: -1
    }

    private val opPrecedence = mapOf(
        "*" to 40,
        "/" to 40,
        "-" to 20,
        "+" to 20,
    )
}