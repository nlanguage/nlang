class Generator(private val root: Program)
{
    private val output = StringBuilder()

    fun generate(): String
    {
        for (node in root.nodes)
        {
            when (node)
            {
                is Function  -> genFunction(node)
                is Extern    -> genExtern(node)
                else         -> throw InternalCompilerException("Unhandled top-level node")
            }
        }

        // Includes
        output.insert(0, "#include<stdbool.h>\n\n")

        return output.toString()
    }

    private fun genFunction(func: Function)
    {
        val decl = genPrototype(func.proto)

        genBlock(func.body)

        // Insert function declaration at the top. This is needed as C needs forward declarations
        output.insert(0, decl + ";")
    }

    private fun genExtern(extern: Extern)
    {
        output.append("extern ")
        genPrototype(extern.proto)
        output.append(";\n\n")
    }

    // Prototype is returned for optional use,
    // this is needed because of forward-decls in c
    private fun genPrototype(proto: Prototype): String
    {
        val builtProto = StringBuilder()

        builtProto.append("int ${proto.name}(")
        builtProto.append(proto.args.joinToString(", ")
        {
            val type = cTypes[it.type] ?:
                throw InternalCompilerException("Unable tp find c-type for ${it.type}")

            "$type ${it.name}"
        })
        builtProto.append(")")

        output.append(builtProto)

        return builtProto.toString();
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
                else                -> throw InternalCompilerException("Unhandled statement")
            }

            output.append(";\n")
        }

        output.append("}\n\n")
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
        "char"   to "char",
        "bool"   to "bool",
        "int"    to "int",
        "string" to "char*",
    )
}