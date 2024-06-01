class Parser(private val lexer: Lexer)
{
    fun parseProgram(): Program
    {
        val nodes = mutableListOf<AstNode>()
        while (!lexer.isAtEnd())
        {
            when (lexer.current.type)
            {
                TokenType.EXTERN -> nodes += parseExtern()
                TokenType.FUN    -> nodes += parseDefinition()
                else             -> reportParseError("'fun' or 'extern'")
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
        val name = lexer.current.text

        // Consume identifier
        lexer.eat()

        if (lexer.current.type != TokenType.LPAREN)
        {
            reportParseError("'('")
        }

        // Consume '('
        lexer.eat()

        val args = mutableListOf<String>()
        if (lexer.current.type != TokenType.RPAREN) {
            while (true) {
                args += lexer.current.text

                // Consume identifier
                lexer.eat()

                if (lexer.current.type == TokenType.RPAREN) {
                    break;
                }

                if (lexer.current.type != TokenType.COMMA) {
                    reportParseError("','")
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

        while (lexer.current.type != TokenType.RBRACE)
        {
            when (lexer.current.type)
            {
                TokenType.RETURN     -> statements += parseReturnStatement()

                TokenType.VAL,
                TokenType.VAR        -> statements += parseDeclarationStatement()

                TokenType.IDENTIFIER ->
                {
                    if (lexer.lookahead.type == TokenType.EQUALS)
                    {
                        statements += parseAssignStatement()
                    }
                    else
                    {
                        statements += ExprStatement(parseExpr())
                    }
                }

                else -> reportParseError("statement")
            }
        }

        // consume '}'
        lexer.eat()

        return Block(statements)
    }

    private fun parseAssignStatement(): Statement
    {
        val name = lexer.current.text

        // Consume identifier
        lexer.eat()

        if (lexer.current.type != TokenType.EQUALS)
        {
            reportParseError(expected = "'='")
        }

        // Consume '='
        lexer.eat()

        val expr = parseExpr()

        return AssignStatement(name, expr)
    }

    private fun parseDeclarationStatement(): Statement
    {
        val mutable = when (lexer.current.type)
        {
            TokenType.VAL -> false
            TokenType.VAR -> true
            else          -> reportParseError("'val' or 'var'")
        }

        // Consume 'val' or 'var'
        lexer.eat()

        val name = lexer.current.text

        // Consume Identifier
        lexer.eat()

        if (lexer.current.type != TokenType.EQUALS)
        {
            reportParseError("'='")
        }

        // Consume '='
        lexer.eat()

        val expr = parseExpr()

        return DeclareStatement(mutable, name, expr)
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

            val operator = lexer.current.text

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
        return when (lexer.current.type)
        {
            TokenType.IDENTIFIER -> parseIdent()
            TokenType.NUMERIC    -> parseNumeric()
            TokenType.LPAREN     -> parseParen()
            else -> reportParseError("expression")
        }
    }

    private fun parseParen(): Expr
    {
        // Consume '('
        lexer.eat()

        // parseExpr() will consume any tokens between '(' and ')'
        val inner = parseExpr()

        if (lexer.current.type != TokenType.RPAREN)
        {
            reportParseError("')'")
        }

        // Consume ')'
        lexer.eat()

        return inner
    }

    private fun parseNumeric(): Expr
    {
        val result = NumberExpr(lexer.current.text.toBigDecimal());

        // Consume Number
        lexer.eat()

        return result
    }

    // Parses either a variable or a function call
    private fun parseIdent(): Expr
    {
        val ident = lexer.current.text

        // Consume Identifier
        lexer.eat()

        // Variable
        if (lexer.current.type != TokenType.LPAREN)
        {
            return VariableExpr(ident)
        }

        // Function call

        // Consume '('
        lexer.eat()

        val args = mutableListOf<Expr>()
        if (lexer.current.type != TokenType.RPAREN)
        {
            while (true)
            {
                args += parseExpr()

                if (lexer.current.type == TokenType.RPAREN)
                {
                    break;
                }

                if (lexer.current.type != TokenType.COMMA)
                {
                    reportParseError("','")
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
        return opPrecedence[lexer.current.text] ?: -1
    }

    private val opPrecedence = mapOf(
        "*" to 40,
        "/" to 40,
        "-" to 20,
        "+" to 20,
    )

    private fun reportParseError(expected: String): Nothing
    {
        reportError("Parsing", lexer.current.filePos, "Expected $expected but found '${lexer.current.text}'")
    }
}