package pipeline.analysis

import ast.*
import symtable.*
import symtable.Function
import util.CompileError
import util.FilePos
import util.InternalCompilerException
import util.reportError

class TypeChecker(val analyser: Analyser)
{
    fun checkModule(def: ModuleDef): Module
    {
        val module = Module(def.name)

        // Handle imports
        for (import in def.imports)
        {
            val syms = analyser.analyseModule(import)

            if (module.types.keys.intersect(syms.types.keys).isNotEmpty())
            {
                reportError("import", import.pos, "Import has conflicting symbols")
            }

            if (module.membs.keys.intersect(syms.membs.keys).isNotEmpty())
            {
                reportError("import", import.pos, "Import has conflicting symbols")
            }

            if (module.funcs.intersect(syms.funcs).isNotEmpty())
            {
                reportError("import", import.pos, "Import has conflicting symbols")
            }

            module += syms
        }

        for (node in def.nodes)
        {
            checkNode(node, module)
        }

        return module
    }

    private fun checkClass(def: ClassDef, syms: SymTable)
    {
        val type = Type(def.cName, def.modifiers, listOf(), syms)

        syms.types[def.name] = type

        for (node in def.nodes)
        {
            checkNode(node, type)
        }
    }

    fun checkNode(node: Node, parentScope: SymTable)
    {
        when (node)
        {
            is ClassDef             -> checkClass(node, parentScope)
            is FunctionDef          -> checkFunction(node, parentScope)
            is DeclarationStatement -> checkDeclarationStatement(node, parentScope)
            else                    -> throw InternalCompilerException("Unhandled node $node")
        }
    }

    private fun checkFunction(def: FunctionDef, parentSyms: SymTable)
    {
        val syms = SymTable(parentSyms)

        if (def.isInstance)
        {
            if (def.parent  == null)
            {
                reportError("check", def.pos, "'this' parameter only allowed in a class")
            }

            syms.membs["this"] = Variable(def.parent, listOf(), null, false, def.pos)
        }

        for (arg in def.params)
        {
            syms.membs[arg.key] = Variable(arg.value, listOf(), null, false, def.pos)
        }

        val func = Function(def.name, def.cName, def.isInstance, def.params, def.modifiers, def.ret)

        parentSyms.funcs += func

        if (def.block != null)
        {
            checkBlock(def.block, func, syms)
        }
    }

    private fun checkBlock(block: Block, proto: Function, parentSyms: SymTable)
    {
        val syms = SymTable(parentSyms)

        for (stmnt in block)
        {
            when (stmnt)
            {
                is DeclarationStatement -> checkDeclarationStatement(stmnt, syms)
                is ReturnStatement -> checkReturnStatement(stmnt, proto, syms)
                is ExprStatement -> checkExprStatement(stmnt, syms)
                is WhenStatement -> checkWhenStatement(stmnt, proto, syms)
                is LoopStatement -> checkLoopStatement(stmnt, proto, syms)
                else                -> throw InternalCompilerException("Unchecked statement")
            }
        }
    }

    private fun checkLoopStatement(stmnt: LoopStatement, proto: Function, syms: SymTable)
    {
        checkExpr(stmnt.expr, "bool", syms).onFailure { reportError(it as CompileError) }

        checkBlock(stmnt.block, proto, syms)
    }

    private fun checkWhenStatement(stmnt: WhenStatement, proto: Function, syms: SymTable)
    {
        for (branch in stmnt.branches)
        {
            checkExpr(branch.expr, "bool", syms).onFailure { reportError(it as CompileError) }

            checkBlock(branch.block, proto, syms)
        }

        if (stmnt.elseBlock != null)
        {
            checkBlock(stmnt.elseBlock, proto, syms)
        }
    }

    private fun checkDeclarationStatement(stmnt: DeclarationStatement, syms: SymTable)
    {
        // If the type is inferred here, then it is marked inferable, this means that it can then be retyped later, if
        // more information becomes available
        if (stmnt.expr != null)
        {
            val rhs = checkExpr(stmnt.expr, stmnt.type, syms).getOrElse { reportError(it as CompileError) }

            if (stmnt.type == null)
            {
                syms.membs[stmnt.name] = Variable(rhs, stmnt.modifiers, stmnt.expr, true, stmnt.pos)
                stmnt.type = rhs
            }
            else
            {
                if (syms.types[stmnt.type] == null)
                {
                    reportError("check", stmnt.pos, "Unknown type '${stmnt.type}'")
                }

                if (rhs != stmnt.type)
                {
                    reportError("check", stmnt.pos, "Declaration is of '${stmnt.type}', but found '$rhs'")
                }

                syms.membs[stmnt.name] = Variable(rhs, stmnt.modifiers, stmnt.expr, false, stmnt.pos)
            }
        }
        else
        {
            if (stmnt.type == null)
            {
                reportError("check", stmnt.pos, "Cannot infer type for '${stmnt.name}'")
            }

            if (syms.types[stmnt.type] == null)
            {
                reportError("check", stmnt.pos, "Unknown type '${stmnt.type}'")
            }

            syms.membs[stmnt.name] = Variable(stmnt.type!!, stmnt.modifiers, null, false, stmnt.pos)
        }
    }

    private fun checkExprStatement(stmnt: ExprStatement, syms: SymTable)
    {
        checkExpr(stmnt.expr, null, syms).onFailure { reportError(it as CompileError) }
    }

    private fun checkReturnStatement(stmnt: ReturnStatement, proto: Function, syms: SymTable)
    {
        checkExpr(stmnt.expr, proto.ret, syms).onFailure { reportError(it as CompileError) }
    }

    private fun checkExpr(expr: Expr, hint: String?, localsyms: SymTable, typesyms: SymTable? = null): Result<String>
    {
        // 'typesyms' is the syms created by a type after a member access.
        // E.g. 'type' '.' <syms is now type's members and functions
        val syms = typesyms ?: localsyms

        return when (expr)
        {
            is IdentExpr -> checkVariableExpr(expr, hint, syms)

            is NumberExpr -> checkNumberExpr(expr, hint)
            is BooleanExpr -> checkType("bool", hint, expr.pos, "Expected '$hint', found boolean expression")
            is StringExpr -> checkType("string", hint, expr.pos, "Expected '$hint', found string literal")
            is CharExpr -> checkType("char", hint, expr.pos, "Expected '$hint', found character literal")

            is CallExpr ->
            {
                val result = checkCallExpr(expr, hint, syms, localsyms)

                checkNotVoid(result, expr.pos)

                result
            }

            is BinaryExpr ->
            {
                val result = checkBinaryExpr(expr, hint, syms)

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

    private fun checkBinaryExpr(expr: BinaryExpr, hint: String?, syms: SymTable): Result<String>
    {
        if (expr.op == "::")
        {
            if (expr.left !is IdentExpr)
            {
                return Result.failure(
                    CompileError("check", expr.left.pos, "Expected type name")
                )
            }

            val type = syms.types[expr.left.value] ?:
            return Result.failure(CompileError("check", expr.left.pos, "Unknown type '${expr.left.value}'"))

            return checkExpr(expr.right, null, syms, type.getStatic())
        }

        val lhs = if (expr.op == ".")
        {
            checkExpr(expr.left, null, syms).getOrElse { return Result.failure(it) }
        }
        else
        {
            checkExpr(expr.left, hint, syms).getOrElse { return Result.failure(it) }
        }

        // Type should always exist
        val type = syms.types[lhs]!!

        if (expr.op == ".")
        {
            return checkExpr(expr.right, hint, syms, type.getNonStatic())
        }

        val ops = type.opers.filter {it.name == expr.op}

        for (op in ops)
        {
            if (checkExpr(expr.right, op.rhs, syms).isSuccess)
            {
                val ret = op.ret ?: "void"

                return checkType(ret, hint, expr.pos, "expected '$hint' but binary expression is of type '$ret'")
            }
        }

        val rhs = checkExpr(expr.right, null, syms).getOrNull() ?: throw InternalCompilerException("RHS eval failed")

        return Result.failure(
            CompileError("check", expr.pos, "Cannot perform operand '${expr.op}' on '$lhs' and '$rhs'")
        )
    }

    private fun checkCallExpr(expr: CallExpr, hint: String?, syms: SymTable, localsyms: SymTable): Result<String>
    {
        findFunc@ for (func in syms.funcs)
        {
            // Find a function with a matching name
            if (func.name == expr.name)
            {
                // Anon functions have positional arguments
                if (func.modifiers.contains("anon"))
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

                        val param = func.params.toList().getOrNull(i) ?: continue@findFunc

                        val arg = (expr.args[i] as AnonArgument).expr

                        // Reached an arg that doesn't match, try with next function
                        if (checkExpr(arg, param.second, localsyms, null).isFailure)
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

                        val paramType = func.params[arg.name] ?: return Result.failure(
                            CompileError("check", expr.pos, "Expected named parameter, found anonymous instead")
                        )

                        if (checkExpr(arg.expr, paramType, localsyms).isFailure)
                        {
                            continue@findFunc
                        }
                    }

                    // Convert the named call to a positional one
                    val args = mutableListOf<Argument>()

                    for (arg in func.params)
                    {
                        val calleeArg = expr.args.find { it is NamedArgument && it.name == arg.key } as NamedArgument

                        args += AnonArgument(calleeArg.expr)
                    }

                    expr.args = args
                }

                expr.cName = func.cName

                // Reaching here means function name and params match
                return checkType(func.ret, hint, expr.pos, "Expected '$hint', but function returns '${func.ret}'")
            }
        }

        // No Matching function found
        return Result.failure(CompileError("check", expr.pos, "No compatible function '${expr.name}()' found"))
    }


    private fun checkVariableExpr(expr: IdentExpr, hint: String?, syms: SymTable): Result<String>
    {
        val variable = syms.membs[expr.value] ?:
            return Result.failure(CompileError("check", expr.pos, "Unknown variable '${expr.value}'"))

        if (hint == null)
        {
            return Result.success(variable.type)
        }

        if (variable.type != hint)
        {
            if (variable.inferable)
            {
                val ret = checkExpr(variable.expr!!, hint, syms)
                variable.inferable = false
                return ret
            }
            else
            {
                return Result.failure(CompileError("check", expr.pos, "Expected '$hint', found '${variable.type}'"))
            }
        }

        return Result.success(variable.type)
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
}