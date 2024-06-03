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
                else             -> reportParseError(expected = "'fun' or 'extern'")
            }
        }

        return Program(nodes)
    }

    private fun parseExtern(): Extern
    {
        // Consume 'extern'
        lexer.eat()

        // Consume 'fun'
        lexer.eat()

        return Extern(parsePrototype())
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
        val name = lexer.eat()

        lexer.eatOnMatch("(")

        val args = mutableListOf<Pair>()
        if (lexer.current.type != TokenType.RPAREN) {
            while (true) {
                val name = lexer.eat()

                lexer.eatOnMatch(":")

                val type = lexer.eat()

                args += Pair(name, type)

                if (lexer.current.type == TokenType.RPAREN) {
                    break;
                }

                lexer.eatOnMatch(",")
            }
        }

        // Consume ')'
        lexer.eat()

        lexer.eatOnMatch("->")

        val returnType = lexer.eat()

        return Prototype(name, args, returnType)
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

                else -> reportParseError(expected = "statement")
            }
        }

        // consume '}'
        lexer.eat()

        return Block(statements)
    }

    private fun parseAssignStatement(): Statement
    {
        val name = lexer.eat()

        lexer.eatOnMatch("=")

        val expr = parseExpr()

        return AssignStatement(name, expr)
    }

    private fun parseDeclarationStatement(): Statement
    {
        val mutable = when (lexer.current.type)
        {
            TokenType.VAL -> false
            TokenType.VAR -> true
            else          -> throw InternalCompilerException("Branch should be unreachable");
        }

        // Consume 'val' or 'var'
        lexer.eat()

        val name = lexer.eat()

        var type: String? = null
        if (lexer.current.type == TokenType.COLON)
        {
            // Consume ':'
            lexer.eat()

            type = lexer.eat()
        }

        lexer.eatOnMatch("=")

        val expr = parseExpr()

        return DeclareStatement(mutable, name, type, expr)
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

            val opPos = lexer.current.filePos

            val operator = lexer.eat()

            var rhs = parsePrimary()

            val nextPrec = getCurTokPrecedence()

            if (tokPrec < nextPrec)
            {
                rhs = parseBinOpRhs(tokPrec + 1, rhs)
            }

            lhs = BinaryExpr(lhs, operator, rhs, opPos)
        }
    }

    private fun parsePrimary(): Expr
    {
        return when (lexer.current.type)
        {
            TokenType.IDENTIFIER -> parseIdent()
            TokenType.NUMERIC    -> parseNumeric()
            TokenType.LPAREN     -> parseParen()
            TokenType.BOOLEAN    -> parseBoolean()
            TokenType.SQUOTE     -> parseChar()
            else -> reportParseError(expected = "expression")
        }
    }

    private fun parseParen(): Expr
    {
        // Consume '('
        lexer.eat()

        // parseExpr() will consume any tokens between '(' and ')'
        val inner = parseExpr()

        lexer.eatOnMatch(")")

        return inner
    }

    private fun parseChar(): Expr
    {
        val pos = lexer.current.filePos

        lexer.eat()

        val value = lexer.eat()[0]

        lexer.eatOnMatch("'")

        return CharExpr(value, pos)
    }

    private fun parseBoolean(): Expr
    {
        val pos = lexer.current.filePos

        return BooleanExpr(lexer.eat().equals("true"), pos)
    }

    private fun parseNumeric(): Expr
    {
        val pos = lexer.current.filePos

        return NumberExpr(lexer.eat().toBigDecimal(), pos);
    }

    // Parses either a variable or a function call
    private fun parseIdent(): Expr
    {
        val pos = lexer.current.filePos

        val ident = lexer.eat()

        // Variable
        if (lexer.current.type != TokenType.LPAREN)
        {
            return VariableExpr(ident, pos)
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

                lexer.eatOnMatch(",")
            }
        }

        // Consume ')'
        lexer.eat()

        return CallExpr(ident, args, pos)
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
        reportError("parse", lexer.current.filePos, "Expected $expected but found '${lexer.current.text}'")
    }
}