import ast.*

data class Scope(var vars: HashMap<String, VarData>, val funcs: List<Prototype>)

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
            else if (node is Class)
            {
                checkClass(node)
            }
        }
    }

    private fun checkClass(clas: Class)
    {
        for (memb in clas.members)
        {
            if (m.types[memb.value.type] == null)
            {
                reportError("check", memb.value.pos, "Unknown type '${memb.value.type}'")
            }
        }

        for (func in clas.funcs)
        {
            checkFunction(func)
        }
    }

    private fun checkFunction(func: FunctionDecl)
    {
        val scope = Scope(hashMapOf(), m.funcs)

        for (arg in func.def.proto.params)
        {
            scope.vars[arg.key] = VarData(arg.value, false, null, false, func.pos)
        }

        checkBlock(func.body, func.def.proto, scope)
    }

    private fun checkBlock(block: Block, proto: Prototype, parentScope: Scope)
    {
        val scope = Scope(parentScope.vars, parentScope.funcs)

        for (stmnt in block.statements)
        {
            when (stmnt)
            {
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
        checkExpr(stmnt.expr, "bool", scope).onFailure { reportError(it as CompileError) }

        checkBlock(stmnt.block, proto, scope)
    }

    private fun checkWhenStatement(stmnt: WhenStatement, proto: Prototype, scope: Scope)
    {
        for (branch in stmnt.branches)
        {
            checkExpr(branch.expr, "bool", scope).onFailure { reportError(it as CompileError) }

            checkBlock(branch.block, proto, scope)
        }

        if (stmnt.elseBlock != null)
        {
            checkBlock(stmnt.elseBlock, proto, scope)
        }
    }

    private fun checkDeclarationStatement(stmnt: DeclareStatement, scope: Scope)
    {
        // If type is inferred, inferable=true, otherwise normal
        if (stmnt.expr != null)
        {
            val rhs = checkExpr(stmnt.expr, stmnt.type, scope).getOrElse { reportError(it as CompileError) }

            if (stmnt.type == null)
            {
                scope.vars[stmnt.name] = VarData(rhs, stmnt.mutable, stmnt.expr, true, stmnt.pos)
                stmnt.type = rhs
            }
            else
            {
                if (m.types[stmnt.type] == null)
                {
                    reportError("check", stmnt.pos, "Unknown type '${stmnt.type}'")
                }

                if (rhs != stmnt.type)
                {
                    reportError("check", stmnt.pos, "Declaration is of '${stmnt.type}', but found '$rhs'")
                }

                scope.vars[stmnt.name] = VarData(rhs, stmnt.mutable, stmnt.expr, false, stmnt.pos)
            }
        }
        else
        {
            if (stmnt.type == null)
            {
                reportError("check", stmnt.pos, "Cannot infer type for '${stmnt.name}'")
            }

            if (m.types[stmnt.type] == null)
            {
                reportError("check", stmnt.pos, "Unknown type '${stmnt.type}'")
            }

            scope.vars[stmnt.name] = VarData(stmnt.type!!, stmnt.mutable, null, false, stmnt.pos)
        }
    }

    private fun checkExprStatement(stmnt: ExprStatement, scope: Scope)
    {
        checkExpr(stmnt.expr, null, scope).onFailure { reportError(it as CompileError) }
    }

    private fun checkReturnStatement(stmnt: ReturnStatement, proto: Prototype, scope: Scope)
    {
        checkExpr(stmnt.expr, proto.ret, scope).onFailure { reportError(it as CompileError) }
    }

    private fun checkExpr(expr: Expr, hint: String?, localScope: Scope, typeScope: Scope? = null): Result<String>
    {
        // 'typeScope' is the scope created by a type after a member access.
        // E.g. 'type' '.' <scope is now type's members and functions
        val scope = typeScope ?: localScope

        return when (expr)
        {
            is VariableExpr -> checkVariableExpr(expr, hint, scope)

            is NumberExpr  -> checkNumberExpr(expr, hint)
            is BooleanExpr -> checkType("bool", hint, expr.pos, "Expected '$hint', found boolean expression")
            is StringExpr  -> checkType("string", hint, expr.pos, "Expected '$hint', found string literal")
            is CharExpr    -> checkType("char", hint, expr.pos, "Expected '$hint', found character literal")

            is CallExpr ->
            {
                val result = checkCallExpr(expr, hint, scope, localScope)

                checkNotVoid(result, expr.pos)

                result
            }

            is BinaryExpr ->
            {
                val result = checkBinaryExpr(expr, hint, scope)

                checkNotVoid(result, expr.pos)

                result
            }

            else -> throw InternalCompilerException("Unhandled expression")
        }
    }

    private fun checkNotVoid(result: Result<String>, pos: FilePos): Result<String>
    {
        val type = result.getOrNull()

        if (type != null && type == "void")
        {
            return Result.failure(CompileError("check", pos, "Expression has no type"))
        }

        return result
    }

    private fun checkBinaryExpr(expr: BinaryExpr, hint: String?, scope: Scope): Result<String>
    {
        if (expr.op == "::")
        {
            if (expr.left !is VariableExpr)
            {
                return Result.failure(
                    CompileError("check", expr.left.pos, "Expected type name")
                )
            }

            val type = m.types[expr.left.name] ?:
                return Result.failure(CompileError("check", expr.left.pos, "Unknown type '${expr.left.name}'"))

            return checkExpr(expr.right, null, scope, Scope(type.staticMembs, type.staticFuncs))
        }

        val lhs = if (expr.op == ".")
        {
            checkExpr(expr.left, null, scope).getOrElse { return Result.failure(it) }
        }
        else
        {
            checkExpr(expr.left, hint, scope).getOrElse { return Result.failure(it) }
        }

        // Type should always exist
        val type = m.types[lhs]!!

        if (expr.op == ".")
        {
            return checkExpr(expr.right, null, scope, Scope(type.membs, type.funcs))
        }

        // Check mutability
        if (expr.op in setOf("=", "+=", "-=", "*=", "/="))
        {
            //TODO()
        }

        val ops = type.ops.filter {it.name == expr.op}

        for (op in ops)
        {
            if (checkExpr(expr.right, op.rhs, scope).isSuccess)
            {
                val ret = op.ret ?: "void"

                return checkType(ret, hint, expr.pos, "expected '$hint' but binary expression is of type '$ret'")
            }
        }

        val rhs = checkExpr(expr.right, null, scope).getOrNull() ?: throw InternalCompilerException("RHS eval failed")

        return Result.failure(
            CompileError("check", expr.pos, "Cannot perform operand '${expr.op}' on '$lhs' and '$rhs'")
        )
    }

    private fun checkCallExpr(expr: CallExpr, hint: String?, scope: Scope, localScope: Scope): Result<String>
    {
        findFunc@ for (proto in scope.funcs)
        {
            // Find a function with a matching name
            if (proto.name == expr.callee)
            {
                // Anon functions have positional arguments
                if (proto.hasFlag("anon"))
                {
                    // Check each arg
                    for (i in 0 ..<expr.args.size)
                    {
                        if (expr.args[i] !is AnonArgument)
                        {
                            return Result.failure(
                                CompileError("check", expr.pos, "Expected anonymous parameter, found named instead")
                            )
                        }

                        val param = proto.params.toList().getOrNull(i) ?: continue@findFunc

                        val arg = (expr.args[i] as AnonArgument).expr

                        // Reached an arg that doesn't match, try with next function
                        if (checkExpr(arg, param.second, localScope, null).isFailure)
                        {
                            continue@findFunc
                        }
                    }
                }
                else
                {
                    for (arg in expr.args)
                    {
                        if (arg !is NamedArgument)
                        {
                            return Result.failure(
                                CompileError("check", expr.pos, "Expected named parameter, found anonymous instead")
                            )
                        }

                        val paramType = proto.params[arg.name] ?: return Result.failure(
                            CompileError("check", expr.pos, "Expected named parameter, found anonymous instead")
                        )

                        if (checkExpr(arg.expr, paramType, localScope).isFailure)
                        {
                            continue@findFunc
                        }
                    }

                    // Convert the named call to a positional one
                    val args = mutableListOf<AnonArgument>()

                    for (arg in proto.params)
                    {
                        val calleeArg = expr.args.find { it is NamedArgument && it.name == arg.key } as NamedArgument

                        args += AnonArgument(calleeArg.expr)
                    }

                    expr.args = args
                }

                expr.cCallee = proto.cName

                // Reaching here means function name and params match
                return checkType(proto.ret, hint, expr.pos, "Expected '$hint', but function returns '${proto.ret}'")
            }
        }

        // No Matching function found
        return Result.failure(CompileError("check", expr.pos, "No compatible function '${expr.callee}()' found"))
    }


    private fun checkVariableExpr(expr: VariableExpr, hint: String?, scope: Scope): Result<String>
    {
        val varData = scope.vars[expr.name] ?:
            return Result.failure(CompileError("check", expr.pos, "Unknown variable '${expr.name}'"))

        if (hint == null)
        {
            return Result.success(varData.type)
        }

        if (varData.type != hint)
        {
            if (varData.inferable)
            {
                val ret = checkExpr(varData.expr!!, hint, scope)
                varData.inferable = false
                return ret
            }
            else
            {
                return Result.failure(CompileError("check", expr.pos, "Expected '$hint', found '${varData.type}'"))
            }
        }

        return Result.success(varData.type)
    }

    private fun checkNumberExpr(expr: NumberExpr, hint: String?): Result<String>
    {
        // Default integer type is uint
        val type = hint ?: "uint"

        val maxValue: Double
        val minValue: Double

        when (type)
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
            "i64" ->
            {
                maxValue = Long.MAX_VALUE.toDouble()
                minValue = Long.MIN_VALUE.toDouble()
            }

            else -> throw InternalCompilerException("Unhandled numeric type")
        }

        if (expr.value.toDouble() > maxValue || expr.value.toDouble() < minValue)
        {
            return Result.failure(CompileError("check", expr.pos, "Integer literal '${expr.value}' cannot fit in '$type'"))
        }

        return Result.success(type)
    }

}

private fun checkType(type: String, hint: String?, pos: FilePos, msg: String): Result<String>
{
    if (hint == null)
    {
        return Result.success(type)
    }

    if (hint != type)
    {
        return Result.failure(CompileError("check", pos, msg))
    }

    return Result.success(type)
}
