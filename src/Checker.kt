import ast.*

typealias Scope = HashMap<String, Variable>

class Checker(val m: Module)
{
    fun check()
    {
        for (node in m.nodes)
        {
            if (node is FunctionDecl)
            {
                checkFunction(node)
            }
        }
    }

    private fun checkFunction(func: FunctionDecl)
    {
        val scope = Scope()

        for (arg in func.def.proto.args)
        {
            scope[arg.name] = arg
        }

        checkBlock(func.body, func.def.proto, scope)
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
                is WhenStatement    -> checkWhenStatement(stmnt, proto, scope)
                is LoopStatement    -> checkLoopStatement(stmnt, proto, scope)
                else                -> throw InternalCompilerException("Unchecked statement")
            }
        }
    }

    private fun checkLoopStatement(stmnt: LoopStatement, proto: Prototype, scope: Scope)
    {
        checkExpr(stmnt.expr, TypeName("bool"), scope)

        checkBlock(stmnt.block, proto, scope)
    }

    private fun checkWhenStatement(stmnt: WhenStatement, proto: Prototype, scope: Scope)
    {
        for (branch in stmnt.branches)
        {
            checkExpr(branch.expr, TypeName("bool"), scope)

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

        val rhs = checkExpr(stmnt.expr, lhs.type, scope)
    }

    private fun checkDeclarationStatement(stmnt: DeclareStatement, scope: Scope)
    {
        if (stmnt.expr != null)
        {
            checkExpr(stmnt.expr, stmnt.variable.type, scope)
        }
        else
        {
            if (stmnt.variable.type.value == null)
            {
                reportError("check", stmnt.pos, "Type required for variable '${stmnt.variable.name}'")
            }
        }

        scope[stmnt.variable.name] = stmnt.variable
    }

    private fun checkExprStatement(stmnt: ExprStatement, scope: Scope)
    {
        checkExpr(stmnt.expr, TypeName(null), scope)
    }

    private fun checkReturnStatement(stmnt: ReturnStatement, proto: Prototype, scope: Scope)
    {
        if (checkExpr(stmnt.expr, TypeName(proto.returnType), scope) == false)
        {
            reportError("check", stmnt.expr.pos, "Expected a return-type of '${proto.returnType}'")
        }
    }

    private fun checkExpr(expr: Expr, type: TypeName, scope: Scope): Boolean
    {
        return when (expr)
        {
            is NumberExpr   -> checkNumericExpr(expr, type)
            is VariableExpr -> checkVariableExpr(expr, type, scope)
            is CallExpr     -> checkCallExpr(expr, type, scope)
            is BinaryExpr   -> checkBinaryExpr(expr, type, scope)
            is BooleanExpr  -> assignOrCheck(type, "bool")
            is CharExpr     -> assignOrCheck(type, "char")
            is StringExpr   -> assignOrCheck(type, "string")
        }
    }

    private fun checkBinaryExpr(expr: BinaryExpr, type: TypeName, scope: Scope): Boolean
    {
        val lhs = TypeName(null)
        checkExpr(expr.left, lhs, scope)

        val rhs = TypeName(null)
        checkExpr(expr.right, rhs, scope)

        val op = m.types[lhs.value]!!.ops.find { it.rhs == rhs.value }

        if (op == null)
        {
            reportError("check", expr.pos, "Cannot perform '${expr.op}' on types '${lhs.value}' and '${rhs.value}'")
        }

        return assignOrCheck(type, op.ret!!)
    }

    private fun checkCallExpr(expr: CallExpr, type: TypeName, scope: Scope): Boolean
    {
        val type = type

        findFunc@ for (proto in m.funcs)
        {
            // Find a function matching the name
            if (proto.name == expr.callee)
            {
                // Check each arg
                for (i in 0..<expr.args.size)
                {
                    val protoArg = proto.args.getOrNull(i) ?: continue@findFunc

                    val matched = checkExpr(expr.args[i], protoArg.type, scope)

                    // Reached an arg that doesn't match, try with next function
                    if (!matched)
                    {
                        continue@findFunc
                    }
                }

                expr.cCallee = proto.cName

                // Reaching here means function name and params match
                return assignOrCheck(type, proto.returnType)
            }
        }

        // No Matching function found
        for (func in m.funcs)
        {
            println("$func")
        }

        reportError("check", expr.pos, "No compatible function '${expr.callee}' found")
    }

    private fun checkVariableExpr(expr: VariableExpr, type: TypeName, scope: Scope): Boolean
    {
        val type = type

        val variable = scope[expr.name] ?:
            reportError("check", expr.pos, "Variable '${expr.name}' doesn't exist in the current scope")

        return assignOrCheck(type, variable.type.value!!)
    }

    private fun assignOrCheck(type: TypeName, value: String): Boolean
    {
        if (type.value == null)
        {
            type.value = value
            return true
        }
        else
        {
            return type.value == value
        }
    }

    private fun checkNumericExpr(expr: NumberExpr, type: TypeName): Boolean
    {
        var type = type

        if (type.value == null)
        {
            type.value = "uint"
        }

        val maxValue: Double
        val minValue: Double

        when (type.value)
        {
            "u8" ->
            {
                maxValue = UByte.MAX_VALUE.toDouble()
                minValue = 0.0
            }

            "u16" ->
            {
                maxValue = UShort.MAX_VALUE.toDouble()
                minValue = 0.0
            }

            "u32" ->
            {
                maxValue = UInt.MAX_VALUE.toDouble()
                minValue = 0.0
            }

            "uint",
            "u64" ->
            {
                maxValue = ULong.MAX_VALUE.toDouble()
                minValue = 0.0
            }

            "i8" ->
            {
                maxValue = Byte.MAX_VALUE.toDouble()
                minValue = Byte.MIN_VALUE.toDouble()
            }

            "i16" ->
            {
                maxValue = Short.MAX_VALUE.toDouble()
                minValue = Short.MIN_VALUE.toDouble()
            }

            "i32" ->
            {
                maxValue = Int.MAX_VALUE.toDouble()
                minValue = Int.MIN_VALUE.toDouble()
            }

            "int",
            "64" ->
            {
                maxValue = Long.MAX_VALUE.toDouble()
                minValue = Long.MIN_VALUE.toDouble()
            }

            else -> throw InternalCompilerException("Unhandled number")
        }

        if (expr.value.toDouble() > maxValue || expr.value.toDouble() < minValue)
        {
            reportError("check", expr.pos, "Integer ${expr.value} cannot fit in a '${type.value}'")
        }

        return true
    }
}
