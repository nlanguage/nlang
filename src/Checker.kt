import ast.*
import java.io.File

typealias Scope = HashMap<String, Variable>

class Checker(val m: Module, val from: String, val others: List<Module>)
{
    fun check()
    {
        val defs = mutableListOf<FunctionDef>()

        // Handle all imports first, other nodes may depend on them
        // Cannot remove elements from array while iterating over them
        val toRemove = mutableListOf<Node>()
        for (node in m.nodes)
        {
            if (node is Import)
            {
                defs += handleImport(node)
                toRemove += node
            }
        }

        m.nodes.removeAll(toRemove)

        // Register all symbols beforehand, so that order of declarations doesn't matter
        for (node in m.nodes)
        {
            when (node)
            {
                is FunctionDecl -> registerFunction(node.proto, node.pos)
                is FunctionDef  -> registerFunction(node.proto, node.pos)
                is Class        -> registerclass(node)
                else            -> throw InternalCompilerException("Unhandled top-level node")
            }
        }

        for (node in m.nodes)
        {
            when (node)
            {
                is FunctionDef  -> {}
                is FunctionDecl -> checkFunction(node)
                is Class        -> checkClass(node)
                else            -> throw InternalCompilerException("Unhandled top-level node $node")
            }
        }

        m.nodes += defs

        m.checked = true
    }

    private fun handleImport(import: Import): List<FunctionDef>
    {
        // Check for cyclical dependencies
        if (import.name + ".n" == from)
        {
            reportError("check", import.pos, "Cyclical dependency on '${import.name}'")
        }

        // Check for dependency on self
        if (import.name + ".n" == m.file.name)
        {
            reportError("check", import.pos, "'${import.name}' cannot depend on itself")
        }

        val defs = mutableListOf<FunctionDef>()

        val imported = others.find { it.file.name == import.name + ".n"} ?:
        reportError("check", import.pos, "No file '${import.name}' found")

        // Recursively check other units. This recursion will go on until a file with no imports is found,
        // which is the bottom of the import tree
        if (!imported.checked)
        {
            Checker(imported, m.file.name, others).check()
        }

        for (impSym in imported.funcs)
        {
            if (impSym.value.flags.contains(Flag("export")))
            {
                if (impSym.value.flags.contains(Flag("imported")))
                {
                    continue
                }

                // Make sure imports do not conflict with our symbols
                if (m.funcs[impSym.value.name] != null)
                {
                    reportError(
                        "check",
                        import.pos,
                        "Import has conflicting definitions of '${impSym.value.name}'"
                    )
                }

                // Mark the symbol as an import. This ensures codegen generates it properly
                // and ensures imports don't travel up the import tree
                // Make sure to copy() the externProto, otherwise the original prototype is modified
                val externProto = impSym.value.copy()
                externProto.flags += Flag("imported")

                m.funcs[impSym.value.name] = externProto

                // Defintions can only be added at the end, otherwise they are duplicated by registerFunction()
                defs += FunctionDef(externProto, import.pos)

            }
        }

        return defs
    }

    private fun registerclass(node: Class)
    {
        m.types[node.name] = listOf()
    }

    // TODO: Fix this function. Figure out how to extract proto from both decl and def
    private fun registerFunction(proto: Prototype, pos: FilePos)
    {
        if (m.funcs[proto.name] != null)
        {
            reportError(
                "check",
                pos,
                "Multiple definitions of '${proto.name}' with the same parameters is not allowed"
            )
        }

        if (proto.flags.contains(Flag("extern")))
        {
            m.funcs[proto.name] = proto
            return
        }

        val protoName = if (proto.name == "main")
        {
            "main"
        }
        else
        {
            buildString{
                append("_Z${proto.name}")

                for (arg in proto.args)
                {
                    append("_${arg.type}")
                }
            }
        }

        if (m.funcs[protoName] != null)
        {
            reportError(
                "check",
                pos,
                "Multiple definitions of '${proto.name}' with the same parameters is not allowed"
            )
        }

        proto.name = protoName
        m.funcs[protoName] = proto
    }

    private fun checkClass(node: Class)
    {
        for (memb in node.members)
        {
            checkType(memb.type!!, memb.pos)
        }
    }

    private fun checkFunction(func: FunctionDecl)
    {
        val scope = Scope()

        for (arg in func.proto.args)
        {
            scope[arg.name] = arg
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
                is WhenStatement    -> checkWhenStatement(stmnt, proto, scope)
                is LoopStatement    -> checkLoopStatement(stmnt, proto, scope)
                else                -> throw InternalCompilerException("Unchecked statement")
            }
        }
    }

    private fun checkLoopStatement(stmnt: LoopStatement, proto: Prototype, scope: Scope)
    {
        val exprType = checkExpr(stmnt.expr, scope)

        if (exprType != "bool")
        {
            reportError("check", stmnt.pos, "expected boolean expression, found '$exprType'")
        }

        checkBlock(stmnt.block, proto, scope)
    }

    private fun checkWhenStatement(stmnt: WhenStatement, proto: Prototype, scope: Scope)
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
                "Cannot assign value of type '$rhs' to variable '${stmnt.name}' of type '${lhs.type}'"
            )
        }
    }

    private fun checkDeclarationStatement(stmnt: DeclareStatement, scope: Scope)
    {
        if (stmnt.variable.type == null)
        {
            if (stmnt.expr != null)
            {
                stmnt.variable.type = checkExpr(stmnt.expr, scope)
            }
            else
            {
                reportError(
                    "check",
                    stmnt.pos,
                    "Cannot infer type for '${stmnt.variable.name}'"
                )
            }
        }
        else if (stmnt.expr != null)
        {
            val rhs = checkExpr(stmnt.expr, scope)
            if (rhs != stmnt.variable.type)
            {
                reportError("check", stmnt.pos, "Expected type'${stmnt.variable.type}', but found '$rhs'")
            }
        }

        checkType(stmnt.variable.type!!, stmnt.pos)

        scope[stmnt.variable.name] = stmnt.variable
    }

    private fun checkType(type: String, pos: FilePos)
    {
        if (m.types[type] == null)
        {
            reportError("check", pos, "Unknown type '$type'")
        }
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
            reportError("check", stmnt.pos, "Expected a return-type of '${proto.returnType}', but found '$rhs'")
        }
    }

    private fun checkExpr(expr: Expr, scope: Scope): String
    {
        return when (expr)
        {
            is NumberExpr   -> "uint"
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

        if (!m.types.contains(lhs))
        {
            reportError("check", expr.pos, "Unknown type $lhs")
        }

        if (!m.types.contains(rhs))
        {
            reportError("check", expr.pos, "Unknown type $rhs")
        }

        val op = m.types[lhs]!!.find { it.name == expr.op && it.rhs == rhs } ?:
                 reportError("check", expr.pos, "Cannot perform '${expr.op}' on types '$lhs' and '$rhs'")

        // If the op has an associated prototype, then we need to replace this binary expression with a call
        // TODO: This code will not be checked till we implement op overloading
        if (op.func != null)
        {
            var pos = expr.pos
            var callExpr = expr as Expr
            callExpr = CallExpr(op.func, listOf(expr.left, expr.right), pos)
        }

        // Return type will always exist in a binary expression
        return op.ret!!
    }

    private fun checkCallExpr(expr: CallExpr, scope: Scope): String
    {
        val mangled = buildString {
            append("_Z${expr.callee}")
            expr.args.forEach { arg ->
                val exprType = checkExpr(arg, scope)
                append("_$exprType")
            }
        }

        val paramList = expr.args.joinToString(", ") { checkExpr(it, scope) }

        val proto = m.funcs[mangled] ?: m.funcs[expr.callee] ?:
            reportError(
                "check",
                expr.pos,
                "No matching function '${expr.callee}' found accepting parameters ($paramList)"
            )

        if (m.funcs.containsKey(mangled))
        {
            expr.callee = mangled
        }

        return proto.returnType
    }

    private fun checkVariableExpr(expr: VariableExpr, scope: Scope): String
    {
        val variable = scope[expr.name]?:
            reportError("check", expr.pos, "Variable '${expr.name}' doesn't exist in the current scope")

        return variable.type!!
    }


}
