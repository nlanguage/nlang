import ast.*

class Parser(val m: Module)
{
    fun parse()
    {
        m.lexer.prime()

        while (!m.lexer.isAtEnd())
        {
            when (m.lexer.current.text)
            {
                "fun"    -> m.nodes += parseFunction()
                "import" -> m.nodes += parseImport()
                "class"  -> m.nodes += parseClass()

                else     ->
                {
                    reportError(
                        "parse",
                        m.lexer.current.pos,
                        "Expected top-level declaration, found '${m.lexer.current.text}'"
                    )
                }
            }
        }
    }

    private fun parseImport(): Import
    {
        // Consume 'import'
        m.lexer.eat()

        val pos = m.lexer.current.pos

        return Import(m.lexer.eat(), pos)
    }

    private fun parseClass(): Class
    {
        val pos = m.lexer.current.pos;

        // consume 'class'
        m.lexer.eat()

        val name = m.lexer.eat()

        m.lexer.eatOnMatch("(")

        val members = hashMapOf<String, VarData>()
        while (m.lexer.current.text != ")")
        {
            val pos = m.lexer.current.pos

            val mutable = when (m.lexer.eat())
            {
                "val" -> false
                "var" -> true
                else  ->
                {
                    reportError(
                        "parse",
                        m.lexer.current.pos,
                        "Expected member declaration, found '${m.lexer.current.text}'"
                    )
                }
            }

            val name = m.lexer.eat()

            m.lexer.eatOnMatch(":")

            val type = m.lexer.eat()

            if (members[name] != null)
            {
                reportError(
                    "parse",
                    pos,
                    "Class cannot have more than one member named '$name'"
                )
            }

            members[name] = VarData(type, mutable, null, false, pos)
        }

        // Consume ')'
        m.lexer.eat()

        return Class(name, members, pos)
    }

    private fun parseFunction(): Node
    {
        val pos = m.lexer.current.pos

        // Consume 'fun'
        m.lexer.eat()

        val proto = parsePrototype()

        if (m.lexer.current.text == "{")
        {
            return FunctionDecl(FunctionDef(proto, pos), parseBlock())
        }
        else
        {
            return FunctionDef(proto, pos)
        }
    }

    private fun parsePrototype(): Prototype
    {
        val name = m.lexer.eat()

        m.lexer.eatOnMatch("(")

        val args = hashMapOf<String, String>()
        if (m.lexer.current.text != ")")
        {
            while (true)
            {
                val name = m.lexer.eat()

                m.lexer.eatOnMatch(":")

                val type = m.lexer.eat()

                args[name] = type

                if (m.lexer.current.text == ")") {
                    break;
                }

                m.lexer.eatOnMatch(",")
            }
        }

        // Consume ')'
        m.lexer.eat()

        var returnType = "void"
        if (m.lexer.current.text == "->")
        {
            // Consume '->'
            m.lexer.eat()

            returnType = m.lexer.eat()
        }

        val flags = mutableListOf<Flag>()
        while (m.lexer.current.text == "@")
        {
            flags += parseFlag()
        }

        return Prototype(name, "", args, returnType, flags)
    }

    private fun parseFlag(): Flag
    {
        // Consume '@'
        m.lexer.eat()

        return Flag(m.lexer.eat())
    }

    private fun parseBlock(): Block
    {
        m.lexer.eatOnMatch("{")

        val statements = mutableListOf<Statement>()

        while (m.lexer.current.text != "}")
        {
            val pos = m.lexer.current.pos

            when (m.lexer.current.text)
            {
                "return" -> statements += parseReturnStatement()

                "val",
                "var"    -> statements += parseDeclarationStatement()

                "when"   -> statements += parseWhenStatement()
                "loop"   -> statements += parseLoopStatement()

                else     ->
                {
                    if (m.lexer.current.type == TokenType.IDENTIFIER)
                    {
                        statements += ExprStatement(parseExpr(), pos)
                    }
                    else
                    {
                        reportError(
                            "parse",
                            m.lexer.current.pos,
                            "Expected statement, found '${m.lexer.current.text}'"
                        )
                    }
                }
            }
        }

        m.lexer.eatOnMatch("}")

        return Block(statements)
    }

    private fun parseLoopStatement(): Statement
    {
        val pos = m.lexer.current.pos

        // Consume 'while'
        m.lexer.eat()

        var expr: Expr = BooleanExpr(true, m.lexer.current.pos)

        if (m.lexer.current.text == "(")
        {
            // Consume '('
            m.lexer.eat()

            expr = parseExpr()

            m.lexer.eatOnMatch(")")
        }

        return LoopStatement(expr, parseBlock(), pos)
    }

    private fun parseWhenStatement(): Statement
    {
        val pos = m.lexer.current.pos

        val branches = mutableListOf<Branch>()

        branches += parseBranch()

        var elseBlock: Block? = null
        while (m.lexer.current.text == "else")
        {
            // Consume 'else'
            m.lexer.eat()

            if (m.lexer.current.text == "when")
            {
                branches += parseBranch()
            }
            else
            {
                elseBlock = parseBlock()
            }
        }

        return WhenStatement(branches, elseBlock, pos)
    }

    private fun parseBranch(): Branch
    {
        // Consume 'when'
        m.lexer.eat()

        m.lexer.eatOnMatch("(")
        val expr = parseExpr()
        m.lexer.eatOnMatch(")")
        val block = parseBlock()

        return Branch(expr, block)
    }

    private fun parseDeclarationStatement(): Statement
    {
        val pos = m.lexer.current.pos

        val mutable = when (m.lexer.eat())
        {
            "val" -> false
            "var" -> true
            else  -> throw InternalCompilerException("Impossible case reached");
        }

        val name = m.lexer.eat()

        var type: String? = null
        if (m.lexer.current.text == ":")
        {
            // Consume ':'
            m. lexer.eat()

            type = m.lexer.eat()
        }

        var expr: Expr? = null
        if (m.lexer.current.text == "=")
        {
            m.lexer.eat()
            expr = parseExpr()
        }

        return DeclareStatement(name, type, mutable, expr, pos)
    }

    private fun parseReturnStatement(): Statement
    {
        val pos = m.lexer.current.pos

        // Consume 'return'
        m.lexer.eat()

        return ReturnStatement(parseExpr(), pos)
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

            val opPos = m.lexer.current.pos

            val operator = m.lexer.eat()

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
        return when (m.lexer.current.type)
        {
            TokenType.IDENTIFIER -> parseIdent()
            TokenType.NUMERIC    -> parseNumeric()
            TokenType.BOOLEAN    -> parseBoolean()
            TokenType.CHARACTER  -> parseChar()
            TokenType.STRING     -> parseString()
            else ->
            {
                if (m.lexer.current.text == "(")
                {
                    parseParen()
                }
                else
                {
                    reportError(
                        "parse",
                        m.lexer.current.pos,
                        "Expected expression, found '${m.lexer.current.text}'"
                    )
                }
            }
        }
    }

    private fun parseParen(): Expr
    {
        // Consume '('
        m.lexer.eat()

        // parseExpr() will consume any tokens between '(' and ')'
        val inner = parseExpr()

        m.lexer.eatOnMatch(")")

        return inner
    }

    private fun parseString(): Expr
    {
        val pos = m.lexer.current.pos
        return StringExpr(m.lexer.eat(), pos)
    }

    private fun parseChar(): Expr
    {
        val pos = m.lexer.current.pos
        return CharExpr(m.lexer.eat()[0], pos)
    }

    private fun parseBoolean(): Expr
    {
        val pos = m.lexer.current.pos

        return BooleanExpr(m.lexer.eat() == "true", pos)
    }

    private fun parseNumeric(): Expr
    {
        val pos = m.lexer.current.pos

        return NumberExpr(m.lexer.eat().toBigDecimal(), pos);
    }

    // Parses either a variable or a function call
    private fun parseIdent(): Expr
    {
        val pos = m.lexer.current.pos

        val ident = m.lexer.eat()

        // Variable
        if (m.lexer.current.text != "(")
        {
            return VariableExpr(ident, pos)
        }

        // Function call

        // Consume '('
        m.lexer.eat()

        val args = mutableListOf<Argument>()
        if (m.lexer.current.text != ")")
        {

            while (true)
            {
                // This is quite bad, but what we are doing is parsing each argument like an expression, and if it
                // is an assign expression, then we treat it as a named arg, otherwise it is an anonymous argument
                val arg = parseExpr()

                if (arg is BinaryExpr && arg.op == "=")
                {
                    args += NamedArgument((arg.left as VariableExpr).name, arg.right)
                }
                else
                {
                    args += AnonArgument(arg)
                }

                if (m.lexer.current.text == ")")
                {
                    break;
                }

                m.lexer.eatOnMatch(",")
            }
        }

        // Consume ')'
        m.lexer.eat()

        return CallExpr(ident, "", args, pos)
    }

    // Get the precedence of the current token (lexer.currentTok)
    private fun getCurTokPrecedence(): Int
    {
        // -1 is used in a comparison
        return opPrecedence[m.lexer.current.text] ?: -1
    }

}

private val opPrecedence = mapOf(
    "."  to 50,
    "*"  to 40,
    "/"  to 40,
    "-"  to 30,
    "+"  to 30,
    "==" to 20,
    "!=" to 20,
    ">"  to 20,
    "<"  to 20,
    ">=" to 20,
    "<=" to 20,
    "="  to 10,
    "+=" to 10,
    "-=" to 10,
    "*=" to 10,
    "/=" to 10,
)
