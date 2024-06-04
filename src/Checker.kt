data class Variable(val type: String, val mutable: Boolean)

typealias Scope = HashMap<String, Variable>

class Checker(private val root: Program)
{
    fun check()
    {
        // Register all symbols beforehand, so that order of declarations doesn't matter
        for (node in root.nodes)
        {
            when (node)
            {
                is Function  -> registerFunction(node.proto)
                is Extern    -> registerExtern(node.proto)
            }
        }

        for (node in root.nodes)
        {
            if (node is Function)
            {
                checkFunction(node)
            }
        }
    }

    private fun registerExtern(proto: Prototype)
    {
        // Function redefinition not allowed
        if (root.syms[proto.name] != null)
        {
            reportError(
                "check",
                proto.pos,
                "Multiple definitions of '${proto.name}' with the same parameters is not allowed"
            )
        }

        root.syms[proto.name] = proto
    }

    private fun registerFunction(proto: Prototype)
    {
        val mangled = StringBuilder("_Z${proto.name}")

        for (arg in proto.args)
        {
            mangled.append("_${arg.type}")
        }

        // Don't mangle main
        val protoName = if (proto.name == "main")
        {
            "main"
        }
        else
        {
            mangled.toString()
        }

        // Function redefinition not allowed
        if (root.syms[protoName] != null)
        {
            reportError(
                "check",
                proto.pos,
                "Multiple definitions of '${proto.name}' with the same parameters is not allowed"
            )
        }

        proto.name = protoName

        root.syms[protoName] = proto
    }

    private fun checkFunction(func: Function)
    {
        val scope = Scope()

        for (arg in func.proto.args)
        {
            scope[arg.name] = Variable(arg.type, false)
        }

        checkBlock(func.body, func.proto, scope)
    }

    private fun checkBlock(block: Block, proto: Prototype, parentScope: Scope)
    {
        val scope = Scope(parentScope)

        for (stmnt in block.statements)
        {
            when (stmnt)
            {
                is AssignStatement  -> checkAssignStatement(stmnt, scope)
                is DeclareStatement -> checkDeclarationStatement(stmnt, scope)
                is ReturnStatement  -> checkReturnStatement(stmnt, proto, scope)
                is ExprStatement    -> checkExprStatement(stmnt, scope)
                is IfStatement      -> checkIfStatement(stmnt, proto, scope)
                else                -> throw InternalCompilerException("Unchecked statement")
            }
        }
    }

    private fun checkIfStatement(stmnt: IfStatement, proto: Prototype, scope: Scope)
    {
        for (branch in stmnt.branches)
        {
            val exprType = checkExpr(branch.expr, scope)
            if (exprType != "bool")
            {
                reportError("check", branch.expr.pos, "expected boolean expression, found '$exprType'")
            }

            checkBlock(branch.block, proto, scope)
        }

        if (stmnt.elseBlock != null)
        {
            checkBlock(stmnt.elseBlock, proto, scope)
        }
    }

    private fun checkAssignStatement(stmnt: AssignStatement, scope: Scope)
    {
        val lhs = scope[stmnt.name] ?:
            reportError("check", stmnt.expr.pos, "Variable '${stmnt.name}' not declared in this scope")

        if (!lhs.mutable)
        {
            reportError("check", stmnt.expr.pos, "Variable '${stmnt.name}' is immutable")
        }

        val rhs = checkExpr(stmnt.expr, scope)

        if (lhs.type != rhs)
        {
            reportError(
                "check",
                stmnt.expr.pos,
                "Cannot assign value of type '$rhs' to variable '${stmnt.name}' of type '$lhs'"
            )
        }
    }

    private fun checkDeclarationStatement(stmnt: DeclareStatement, scope: Scope)
    {
        val rhs = checkExpr(stmnt.expr, scope)

        // A type was specified during declaration
        if (stmnt.type != null)
        {
            if (stmnt.type != rhs)
            {
                reportError("check", stmnt.expr.pos, "Expected type'${stmnt.type}', but found '$rhs'")
            }
        }
        else
        {
            stmnt.type = rhs
        }

        scope[stmnt.name] = Variable(stmnt.type!!, stmnt.mutable)
    }

    private fun checkExprStatement(stmnt: ExprStatement, scope: Scope)
    {
        checkExpr(stmnt.expr, scope)
    }

    private fun checkReturnStatement(stmnt: ReturnStatement, proto: Prototype, scope: Scope)
    {
        val rhs = checkExpr(stmnt.expr, scope)

        if (rhs != proto.returnType)
        {
            reportError("check", stmnt.expr.pos, "Expected a return-type of '${proto.returnType}', but found '$rhs'")
        }
    }

    private fun checkExpr(expr: Expr, scope: Scope): String
    {
        return when (expr)
        {
            is NumberExpr   -> "int"
            is BooleanExpr  -> "bool"
            is CharExpr     -> "char"
            is StringExpr   -> "string"
            is VariableExpr -> checkVariableExpr(expr, scope)
            is CallExpr     -> checkCallExpr(expr, scope)
            is BinaryExpr   -> checkBinaryExpr(expr, scope)
            else            -> throw InternalCompilerException("Unhandled primary")
        }
    }

    private fun checkBinaryExpr(expr: BinaryExpr, scope: Scope): String
    {
        val lhs = checkExpr(expr.left, scope)
        val rhs = checkExpr(expr.right, scope)

        // We don't allow operations on different types yet
        if (lhs != rhs)
        {
            reportError("check", expr.pos, "Cannot perform '${expr.op}' on values of different types")
        }

        val ops = possibleOps[lhs]
        if (ops == null || ops[expr.op] == null)
        {
            reportError("check", expr.pos, "Cannot perform '${expr.op}' on type '$lhs'")
        }

        return ops[expr.op]!!
    }

    private fun checkCallExpr(expr: CallExpr, scope: Scope): String
    {
        val mangled = StringBuilder("_Z${expr.callee}")

        val paramList = StringBuilder()

        for (i in 0..<expr.args.size)
        {
            val exprType = checkExpr(expr.args[i], scope)

            if (i != expr.args.size - 1)
            {
                paramList.append("$exprType, ")
            }
            else
            {
                paramList.append(exprType)
            }

            mangled.append("_$exprType")
        }

        val calleeName = mangled.toString()

        // Externs will not use the mangled name
        var useMangled = false

        val proto = if (root.syms[calleeName] != null)
        {
            useMangled = true;
            root.syms[calleeName]!!
        }
        else if (root.syms[expr.callee] != null)
        {
            root.syms[expr.callee]!!
        }
        else
        {
            reportError(
                "check",
                expr.pos,
                "No matching function '${expr.callee}' found accepting parameters (${paramList.toString()})"
            )
        }

        if (useMangled)
        {
            expr.callee = calleeName
        }

        return proto.returnType
    }

    private fun checkVariableExpr(expr: VariableExpr, scope: Scope): String
    {
        val variable = scope[expr.name]?:
            reportError("check", expr.pos, "Variable '${expr.name}' doesn't exist in the current scope")

        return variable.type
    }

    private val possibleOps = mapOf(
        "int" to mapOf(
            "+"  to "int",
            "-"  to "int",
            "*"  to "int",
            "/"  to "int",
            "==" to "bool",
            "!=" to "bool",
            ">"  to "bool",
            "<"  to "bool",
            ">=" to "bool",
            "<=" to "bool",
        ),

        "bool" to mapOf(
            "==" to "bool",
            "!=" to "bool",
            ">"  to "bool",
            "<"  to "bool",
            ">=" to "bool",
            "<=" to "bool",
        ),

        "char" to mapOf(
            "==" to "bool",
            "!=" to "bool",
            ">"  to "bool",
            "<"  to "bool",
            ">=" to "bool",
            "<=" to "bool",
        ),

        "string" to mapOf(
            "==" to "bool",
            "!=" to "bool",
            ">"  to "bool",
            "<"  to "bool",
            ">=" to "bool",
            "<=" to "bool",
        )
    )
}