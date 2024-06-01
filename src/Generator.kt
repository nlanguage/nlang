

class Generator(private val root: Program)
{
    private val output = StringBuilder()

    fun generate(): String
    {
        for (node in root.nodes)
        {
            when (node)
            {
                is Function  -> visitFunction(node)
                is Prototype -> visitExtern(node)
                else         -> throw InternalCompilerException("Unhandled top-level node")
            }
        }

        return output.toString()
    }

    private fun visitFunction(func: Function)
    {
        val decl = visitPrototype(func.proto)

        visitBlock(func.body)

        // Insert function declaration at the top. This is needed as C needs forward declarations
        output.insert(0, decl + ";")
    }

    private fun visitExtern(proto: Prototype)
    {
        output.append("extern ")
        visitPrototype(proto)
        output.append(";\n\n")
    }

    // Prototype is returned for optional use,
    // this is needed because of forward-decls in c
    private fun visitPrototype(proto: Prototype): String
    {
        val builtProto = StringBuilder()

        builtProto.append("int ${proto.name}(")
        builtProto.append(proto.args.joinToString(",") { "int $it" })
        builtProto.append(")")

        output.append(builtProto)

        return builtProto.toString();
    }

    private fun visitBlock(block: Block)
    {
        output.append("\n{\n")

        for (statement in block.statements)
        {
            when (statement)
            {
                is ReturnStatement  -> visitReturnStatement(statement)
                is ExprStatement    -> visitExprStatement(statement)
                is DeclareStatement -> visitDeclarationStatement(statement)
                is AssignStatement  -> visitAssignStatement(statement)
                else                -> throw InternalCompilerException("Unhandled statement")
            }

            output.append(";\n")
        }

        output.append("\n}\n")
    }

    private fun visitAssignStatement(statement: AssignStatement)
    {
        output.append(statement.name)
        output.append('=')
        visitExpr(statement.expr)
    }

    private fun visitDeclarationStatement(statement: DeclareStatement)
    {
        output.append("int ")
        output.append(statement.name)
        output.append(" = ")
        visitExpr(statement.expr)
    }


    private fun visitExprStatement(statement: ExprStatement)
    {
        visitExpr(statement.expr)
    }

    private fun visitReturnStatement(statement: ReturnStatement)
    {
        output.append("return ")
        visitExpr(statement.expr)
    }

    private fun visitExpr(expr: Expr)
    {
        when (expr)
        {
            is BinaryExpr   -> visitBinaryExpr(expr)
            is NumberExpr   -> visitNumberExpr(expr)
            is VariableExpr -> visitVariableExpr(expr)
            is CallExpr     -> visitCallExpr(expr)
        }
    }

    private fun visitBinaryExpr(expr: BinaryExpr)
    {
        output.append("(")
        visitExpr(expr.left)
        output.append(expr.op)
        visitExpr(expr.right)
        output.append(")")
    }

    private fun visitCallExpr(expr: CallExpr)
    {
        output.append(expr.callee)

        output.append("(")

        for (arg in expr.args)
        {
            visitExpr(arg)

            if (arg !== expr.args.last())
            output.append(",")
        }

        output.append(")")
    }

    private fun visitNumberExpr(expr: NumberExpr)
    {
        output.append(expr.value)
    }

    private fun visitVariableExpr(expr: VariableExpr)
    {
        output.append(expr.name)
    }
}