data class Variable(val type: String, val mutable: Boolean)

typealias Scope = HashMap<String, Variable>

typealias SymbolTable = HashMap<String, Prototype>

class Checker(private val root: Program)
{
    private val syms: SymbolTable = hashMapOf()

    fun check()
    {
        for (node in root.nodes)
        {
            when (node)
            {
                is Function  -> checkFunction(node)
                is Extern    -> registerPrototype(node.proto)
                else         -> throw InternalCompilerException("Unhandled top-level node")
            }
        }
    }

    private fun registerPrototype(proto: Prototype)
    {
        syms[proto.name] = proto
    }

    private fun checkFunction(func: Function)
    {
        val scope = Scope()

        for (arg in func.proto.args)
        {
            scope[arg.name] = Variable(arg.type, false)
        }

        registerPrototype(func.proto)
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
                else                -> throw InternalCompilerException("Unchecked statement")
            }
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

        return lhs
    }

    private fun checkCallExpr(expr: CallExpr, scope: Scope): String
    {
        val func = syms[expr.callee] ?:
            reportError("check", expr.pos, "Function '${expr.callee}' doesn't exist in the current scope")

        for (i in 0..<expr.args.size)
        {
            if (func.args[i].type != checkExpr(expr.args[i], scope))
            {
                reportError("check", expr.args[i].pos, "Function '${expr.callee}' doesn't exist in the current scope")
            }
        }

        return func.returnType
    }

    private fun checkVariableExpr(expr: VariableExpr, scope: Scope): String
    {
        val variable = scope[expr.name]?:
            reportError("check", expr.pos, "Variable '${expr.name}' doesn't exist in the current scope")

        return variable.type
    }

    val possibleOps = mapOf(
        "int" to mapOf(
            "+" to true,
            "-" to true,
            "*" to true,
            "/" to true,
        ),

        "bool" to mapOf(
            "+" to false,
            "-" to false,
            "*" to false,
            "/" to false,
        ),

        "char" to mapOf(
            "+" to false,
            "-" to false,
            "*" to false,
            "/" to false,
        )
    )
}