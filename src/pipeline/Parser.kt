package pipeline

import ast.*
import pipeline.lexer.Lexer
import pipeline.lexer.TokenType
import util.InternalCompilerException
import util.reportError
import java.io.File

class Parser(path: String)
{
    val lexer: Lexer

    init
    {
        val file = File(path)

        lexer = Lexer(file.name.substring(0..file.name.length - 3), file.readText())

        lexer.prime()
    }

    fun parseModule(): ModuleDef
    {
        val nodes = mutableListOf<Node>()
        val imports = mutableListOf<Import>()
        while (!lexer.isAtEnd())
        {
            if (lexer.current.text == "import")
            {
                imports += parseImport()
            }
            else
            {
                nodes += parseNode(null)
            }
        }

        return ModuleDef(lexer.name, imports, nodes)
    }

    private fun parseImport(): Import
    {
        // Consume 'import'
        lexer.eat()

        val pos = lexer.current.pos

        return Import(lexer.eat(), pos)
    }

    private fun parseClassDef(): ClassDef
    {
        val pos = lexer.current.pos;

        // Consume 'class'
        lexer.eat()

        val name = lexer.eat()

        lexer.eatOnMatch("{")

        val nodes = mutableListOf<Node>()
        while (lexer.current.text != "}")
        {
            nodes += parseNode(name)
        }

        // Consume '}'
        lexer.eat()

        return ClassDef(name, listOf(), nodes, pos)
    }

    private fun parseNode(parent: String?): Node
    {
        return when (lexer.current.text)
        {
            "fun"    -> parseFunctionDef(parent)
            "class"  -> parseClassDef()

            "val",
            "var"    -> parseDeclarationStatement()

            else     ->
            {
                reportError(
                    "parse",
                    lexer.current.pos,
                    "Expected top-level declaration, found '${lexer.current.text}'"
                )
            }
        }
    }

    private fun parseFunctionDef(parent: String?): FunctionDef
    {
        val pos = lexer.current.pos

        // Consume 'fun'
        lexer.eat()

        val name = lexer.eat()

        lexer.eatOnMatch("(")

        var instance = false
        val params = hashMapOf<String, String>()

        // Check if the first parameter is 'this'
        if (lexer.current.text == "this")
        {
            instance = true
            lexer.eat()
            // Check if there are more parameters
            if (lexer.current.text == ",")
            {
                lexer.eat()
            }
        }

        // Parse parameters
        while (lexer.current.text != ")")
        {
            val paramName = lexer.eat()
            lexer.eatOnMatch(":")
            val paramType = lexer.eat()

            params[paramName] = paramType

            // Check for more parameters
            if (lexer.current.text == ",")
            {
                lexer.eat()
            }
        }

        // Consume ')'
        lexer.eat()

        var returnType = "void"
        if (lexer.current.text == "->")
        {
            // Consume '->'
            lexer.eat()
            returnType = lexer.eat()
        }

        val mods = mutableListOf<String>()
        while (lexer.current.text == "@")
        {
            mods += parseModifier()
        }

        var block: Block? = null
        if (lexer.current.text == "{")
        {
            block = parseBlock()
        }

        return FunctionDef(name, parent, instance, params, mods, returnType, block, pos)
    }
    private fun parseModifier(): String
    {
        // Consume '@'
        lexer.eat()

        return lexer.eat()
    }

    private fun parseBlock(): Block
    {
        lexer.eatOnMatch("{")

        val statements = mutableListOf<Statement>()

        while (lexer.current.text != "}")
        {
            val pos = lexer.current.pos

            when (lexer.current.text)
            {
                "return" -> statements += parseReturnStatement()

                "val",
                "var"    -> statements += parseDeclarationStatement()

                "when"   -> statements += parseWhenStatement()
                "loop"   -> statements += parseLoopStatement()

                else     ->
                {
                    if (lexer.current.type == TokenType.IDENTIFIER)
                    {
                        statements += ExprStatement(parseExpr(), pos)
                    }
                    else
                    {
                        reportError(
                            "parse",
                            lexer.current.pos,
                            "Expected statement, found '${lexer.current.text}'"
                        )
                    }
                }
            }
        }

        lexer.eatOnMatch("}")

        return statements
    }

    private fun parseLoopStatement(): Statement
    {
        val pos = lexer.current.pos

        // Consume 'while'
        lexer.eat()

        var expr: Expr = BooleanExpr(true, lexer.current.pos)

        if (lexer.current.text == "(")
        {
            // Consume '('
            lexer.eat()

            expr = parseExpr()

            lexer.eatOnMatch(")")
        }

        return LoopStatement(expr, parseBlock(), pos)
    }

    private fun parseWhenStatement(): Statement
    {
        val pos = lexer.current.pos

        val branches = mutableListOf<Branch>()

        branches += parseBranch()

        var elseBlock: Block? = null
        while (lexer.current.text == "else")
        {
            // Consume 'else'
            lexer.eat()

            if (lexer.current.text == "when")
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
        lexer.eat()

        lexer.eatOnMatch("(")
        val expr = parseExpr()
        lexer.eatOnMatch(")")
        val block = parseBlock()

        return Branch(expr, block)
    }

    private fun parseDeclarationStatement(): Statement
    {
        val pos = lexer.current.pos

        val mutable = when (lexer.eat())
        {
            "val" -> false
            "var" -> true
            else  -> throw InternalCompilerException("Impossible case reached");
        }

        val name = lexer.eat()

        var type: String? = null
        if (lexer.current.text == ":")
        {
            // Consume ':'
             lexer.eat()

            type = lexer.eat()
        }

        var expr: Expr? = null
        if (lexer.current.text == "=")
        {
            lexer.eat()
            expr = parseExpr()
        }

        return DeclarationStatement(name, type, listOf(), expr, pos)
    }

    private fun parseReturnStatement(): Statement
    {
        val pos = lexer.current.pos

        // Consume 'return'
        lexer.eat()

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

            val opPos = lexer.current.pos

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
            TokenType.BOOLEAN    -> parseBoolean()
            TokenType.CHARACTER  -> parseChar()
            TokenType.STRING     -> parseString()
            else ->
            {
                if (lexer.current.text == "(")
                {
                    parseParen()
                }
                else
                {
                    reportError(
                        "parse",
                        lexer.current.pos,
                        "Expected expression, found '${lexer.current.text}'"
                    )
                }
            }
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

    private fun parseString(): Expr
    {
        val pos = lexer.current.pos
        return StringExpr(lexer.eat(), pos)
    }

    private fun parseChar(): Expr
    {
        val pos = lexer.current.pos
        return CharExpr(lexer.eat()[0], pos)
    }

    private fun parseBoolean(): Expr
    {
        val pos = lexer.current.pos
        return BooleanExpr(lexer.eat() == "true", pos)
    }

    private fun parseNumeric(): Expr
    {
        val pos = lexer.current.pos
        return NumberExpr(lexer.eat().toBigDecimal(), pos);
    }

    // Parses either a variable or a function call
    private fun parseIdent(): Expr
    {
        val pos = lexer.current.pos

        val ident = lexer.eat()

        // Variable
        if (lexer.current.text != "(")
        {
            return IdentExpr(ident, pos)
        }

        // Function call

        // Consume '('
        lexer.eat()

        val args = mutableListOf<Argument>()
        if (lexer.current.text != ")")
        {

            while (true)
            {
                // This is quite bad, but what we are doing is parsing each argument like an expression, and if it
                // is an assign expression, then we treat it as a named arg, otherwise it is an anonymous argument
                val arg = parseExpr()

                if (arg is BinaryExpr && arg.op == "=")
                {
                    args += NamedArgument((arg.left as IdentExpr).value, arg.right)
                }
                else
                {
                    args += AnonArgument(arg)
                }

                if (lexer.current.text == ")")
                {
                    break;
                }

                lexer.eatOnMatch(",")
            }
        }

        // Consume ')'
        lexer.eat()

        return CallExpr(ident, "", args, pos)
    }

    // Get the precedence of the current token (lexer.currentTok)
    private fun getCurTokPrecedence(): Int
    {
        // -1 is used in a comparison
        return opPrecedence[lexer.current.text] ?: -1
    }

    private val opPrecedence = mapOf(
        "."  to 70,
        "::" to 70,
        "*"  to 60,
        "/"  to 60,
        "-"  to 50,
        "+"  to 50,
        "==" to 40,
        "!=" to 40,
        ">"  to 40,
        "<"  to 40,
        ">=" to 40,
        "<=" to 40,
        "&&" to 30,
        "||" to 20,
        "="  to 10,
        "+=" to 10,
        "-=" to 10,
        "*=" to 10,
        "/=" to 10,
    )
}