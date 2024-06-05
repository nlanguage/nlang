class Generator(val nodes: List<AstNode>, val syms: SymbolTable)
{
    private val output = StringBuilder()

    fun generate(): String
    {
        // Includes
        output.append("#include<stdbool.h>\n\n")

        // Generate forward declarations
        for (sym in syms)
        {
            genPrototype(sym.value)
            output.append(";\n")
        }

        output.append("\n")

        for (node in nodes)
        {
            when (node)
            {
                is FunctionDecl -> genFunctionDecl(node)
                is FunctionDef  -> genFunctionDef(node)
                else            -> throw InternalCompilerException("Unhandled top-level node")
            }
        }

        return output.toString()
    }

    private fun genFunctionDecl(func: FunctionDecl)
    {
        genPrototype(func.proto)
        genBlock(func.body)
    }

    private fun genFunctionDef(def: FunctionDef)
    {
        // Handle externs
        if (def.proto.flags.contains(Flag("extern")))
        {
            output.append("extern ")
        }

        genPrototype(def.proto)
        output.append(";\n\n")
    }

    private fun genPrototype(proto: Prototype)
    {
        val returnType = cTypes[proto.returnType] ?:
            throw InternalCompilerException("Unable tp find c-type for ${proto.returnType}")

        output.append("$returnType ${proto.name}(")
        output.append(proto.args.joinToString(", ")
        {
            val type = cTypes[it.type] ?:
                throw InternalCompilerException("Unable tp find c-type for ${it.type}")

            "$type ${it.name}"
        })
        output.append(")")
    }

    private fun genBlock(block: Block)
    {
        output.append("\n{\n")

        for (statement in block.statements)
        {
            when (statement)
            {
                is ReturnStatement  -> genReturnStatement(statement)
                is ExprStatement    -> genExprStatement(statement)
                is DeclareStatement -> genDeclarationStatement(statement)
                is AssignStatement  -> genAssignStatement(statement)
                is IfStatement      -> genIfStatement(statement)
                else                -> throw InternalCompilerException("Unhandled statement")
            }

            output.append(";\n")
        }

        output.append("}\n\n")
    }

    private fun genIfStatement(stmnt: IfStatement)
    {
        // Generate primary branch
        output.append("if (")
        genExpr(stmnt.branches[0].expr)
        output.append(")")
        genBlock(stmnt.branches[0].block)

        // Generate alternative branches
        for (i in 1..<stmnt.branches.size)
        {
            output.append("else if (")
            genExpr(stmnt.branches[i].expr)
            output.append(")")
            genBlock(stmnt.branches[i].block)
        }

        // Generate final branch
        if (stmnt.elseBlock != null)
        {
            output.append("else")
            genBlock(stmnt.elseBlock)
        }
    }

    private fun genAssignStatement(stmnt: AssignStatement)
    {
        output.append("${stmnt.name} =")
        genExpr(stmnt.expr)
    }

    private fun genDeclarationStatement(stmnt: DeclareStatement)
    {
        val type = cTypes[stmnt.type] ?:
            throw InternalCompilerException("Unable tp find c-type for ${stmnt.type}")

        output.append("$type ${stmnt.name} =")
        genExpr(stmnt.expr)
    }

    private fun genExprStatement(stmnt: ExprStatement)
    {
        genExpr(stmnt.expr)
    }

    private fun genReturnStatement(stmnt: ReturnStatement)
    {
        output.append("return ")
        genExpr(stmnt.expr)
    }

    private fun genExpr(expr: Expr)
    {
        when (expr)
        {
            is BinaryExpr   -> genBinaryExpr(expr)
            is NumberExpr   -> genNumberExpr(expr)
            is VariableExpr -> genVariableExpr(expr)
            is CallExpr     -> genCallExpr(expr)
            is BooleanExpr  -> genBooleanExpr(expr)
            is CharExpr     -> genCharExpr(expr)
            is StringExpr   -> genStringExpr(expr)
            else            -> throw InternalCompilerException("Unimplemented codegen")
        }
    }

    private fun genBinaryExpr(expr: BinaryExpr)
    {
        output.append("(")
        genExpr(expr.left)
        output.append(expr.op)
        genExpr(expr.right)
        output.append(")")
    }

    private fun genCallExpr(expr: CallExpr)
    {
        output.append("${expr.callee} (")

        for (arg in expr.args)
        {
            genExpr(arg)

            if (arg !== expr.args.last())
                output.append(",")
        }

        output.append(")")
    }

    private fun genStringExpr(expr: StringExpr)
    {
        output.append("\"${expr.value}\"")
    }


    private fun genCharExpr(expr: CharExpr)
    {
        output.append("'${expr.value}'")
    }

    private fun genBooleanExpr(expr: BooleanExpr)
    {
        output.append(expr.value.toString())
    }

    private fun genNumberExpr(expr: NumberExpr)
    {
        output.append(expr.value)
    }

    private fun genVariableExpr(expr: VariableExpr)
    {
        output.append(expr.name)
    }

    private val cTypes = mapOf(
        "void"   to "void",
        "char"   to "char",
        "bool"   to "bool",
        "int"    to "int",
        "string" to "char*",
    )
}