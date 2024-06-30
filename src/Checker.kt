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
            else if (node is Class)
            {
                checkClass(node)
            }
        }
    }

    private fun checkClass(clas: Class)
    {
        val cName = "_Z${clas.name}"

        m.types[clas.name] = Type(clas.name, cName, listOf(), listOf())

        for (memb in clas.members)
        {
            if (m.types[memb.type.alternatives.first()] == null)
            {
                reportError("check", memb.pos, "Unknown type '${memb.type.alternatives.first()}'")
            }
        }
    }

    private fun checkFunction(func: FunctionDecl)
    {
        val scope = Scope()

        for (arg in func.def.proto.params)
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
        checkExpr(stmnt.expr, TypeId("bool"), scope)

        checkBlock(stmnt.block, proto, scope)
    }

    private fun checkWhenStatement(stmnt: WhenStatement, proto: Prototype, scope: Scope)
    {
        for (branch in stmnt.branches)
        {
            checkExpr(branch.expr, TypeId("bool"), scope)

            checkBlock(branch.block, proto, scope)
        }

        if (stmnt.elseBlock != null)
        {
            checkBlock(stmnt.elseBlock, proto, scope)
        }
    }

    private fun checkDeclarationStatement(stmnt: DeclareStatement, scope: Scope)
    {
        if (stmnt.expr != null)
        {
            checkExpr(stmnt.expr, stmnt.variable.type, scope)
        }
        else
        {
            if (stmnt.variable.type.alternatives.isEmpty())
            {
                reportError("check", stmnt.pos, "Type required for variable '${stmnt.variable.name}'")
            }
        }

        scope[stmnt.variable.name] = stmnt.variable
    }

    private fun checkExprStatement(stmnt: ExprStatement, scope: Scope)
    {
        checkExpr(stmnt.expr, TypeId(), scope)
    }

    private fun checkReturnStatement(stmnt: ReturnStatement, proto: Prototype, scope: Scope)
    {
        if (checkExpr(stmnt.expr, TypeId(proto.returnType), scope) == false)
        {
            reportError("check", stmnt.expr.pos, "Expected a return-type of '${proto.returnType}'")
        }
    }

    private fun checkExpr(expr: Expr, type: TypeId, scope: Scope): Boolean
    {
        return when (expr)
        {
            is NumberExpr   -> checkNumericExpr(expr, type)
            is VariableExpr -> checkVariableExpr(expr, type, scope)
            is CallExpr     -> checkCallExpr(expr, type, scope)
            is BinaryExpr   -> checkBinaryExpr(expr, type, scope)
            is BooleanExpr  -> type.checkOrAssign(TypeId("bool"))
            is CharExpr     -> type.checkOrAssign(TypeId("char"))
            is StringExpr   -> type.checkOrAssign(TypeId("string"))
        }
    }

    private fun checkBinaryExpr(expr: BinaryExpr, type: TypeId, scope: Scope): Boolean
    {
        val lhsAlts = TypeId()
        checkExpr(expr.left, lhsAlts, scope)

        val rhsAlts = TypeId()
        checkExpr(expr.right, rhsAlts, scope)

        val alternatives = mutableSetOf<String>()

        for (lhs in lhsAlts.alternatives)
        {
            val lhsType = m.types[lhs] ?:
                reportError("check", expr.pos, "Unknown type $type")

            for (rhs in rhsAlts.alternatives)
            {
                val rhsType = m.types[lhs] ?:
                    reportError("check", expr.pos, "Unknown type $type")

                val op = lhsType.ops.find { it.name == expr.op && it.rhs == rhsType.name }

                if (op == null)
                {
                    continue
                }

                if (op.func != null)
                {
                    TODO()
                }

                if (op.ret != null)
                {
                    alternatives += op.ret
                }
            }
        }

        return type.checkOrAssign(TypeId(alternatives))
    }

    private fun checkCallExpr(expr: CallExpr, type: TypeId, scope: Scope): Boolean
    {
        val type = type

        findFunc@ for (proto in m.funcs)
        {
            // Find a function matching the name
            if (proto.name == expr.callee)
            {
                if (proto.hasFlag("anon"))
                {
                    // Check each arg
                    for (i in 0..<expr.args.size)
                    {
                        if (expr.args[i] !is AnonArgument)
                        {
                            reportError("check", expr.pos, "Expected anonymous parameter, found named instead")
                        }

                        val param = proto.params.getOrNull(i) ?: continue@findFunc

                        val arg = (expr.args[i] as AnonArgument).expr

                        val matched  = checkExpr(arg, param.type, scope)

                        // Reached an arg that doesn't match, try with next function
                        if (!matched)
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
                            reportError("check", expr.pos, "Expected named parameter, found anonymous instead")
                        }

                        val protoArg = proto.params.find {it.name == arg.name}

                        val matched = protoArg != null && checkExpr(arg.expr, protoArg.type, scope)

                        if (!matched)
                        {
                            continue@findFunc
                        }
                    }

                    // Convert the named call to a positional one
                    val args = mutableListOf<AnonArgument>()

                    for (arg in proto.params)
                    {
                        val calleeArg = expr.args.find { it is NamedArgument && it.name == arg.name } as NamedArgument

                        args += AnonArgument(calleeArg.expr)
                    }

                    expr.args = args
                }

                expr.cCallee = proto.cName

                // Reaching here means function name and params match
                return type.checkOrAssign(TypeId(proto.returnType))
            }
        }

        // No Matching function found
        reportError("check", expr.pos, "No compatible function '${expr.callee}' found")
    }

    private fun checkVariableExpr(expr: VariableExpr, type: TypeId, scope: Scope): Boolean
    {
        val type = type

        val variable = scope[expr.name] ?:
            reportError("check", expr.pos, "Variable '${expr.name}' doesn't exist in the current scope")

        return type.checkOrAssign(variable.type)
    }

    private fun checkNumericExpr(expr: NumberExpr, type: TypeId): Boolean
    {
        var type = type

        val intTypes = hashMapOf(
            "u8"   to Pair(0.0, UByte.MAX_VALUE.toDouble()),
            "u16"  to Pair(0.0, UShort.MAX_VALUE.toDouble()),
            "u32"  to Pair(0.0, UInt.MAX_VALUE.toDouble()),
            "u64"  to Pair(0.0, ULong.MAX_VALUE.toDouble()),
            "uint" to Pair(0.0, ULong.MAX_VALUE.toDouble()),

            "i8"  to Pair(Byte.MIN_VALUE.toDouble(), Byte.MAX_VALUE.toDouble()),
            "i16" to Pair(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()),
            "i32" to Pair(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()),
            "i64" to Pair(Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble()),
            "int" to Pair(Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble()),
        )

        val alternatives = mutableSetOf<String>()

        for (intType in intTypes)
        {
            val number = expr.value.toDouble()

            if (number >= intType.value.first && number <= intType.value.second)
            {
                alternatives += intType.key
            }
        }

        return type.checkOrAssign(TypeId(alternatives))
    }
}
