package pipeline

import ast.*
import symtable.Module
import symtable.SymTable
import util.InternalCompilerException
import java.io.File

class Generator(val mod: Module, val def: ModuleDef, val irDir: File)
{
    val source = StringBuilder()
    val header = StringBuilder()

    fun generateModule(): String
    {
        // C Includes
        source.append("#include <stdbool.h>\n#include <stdint.h>\n#include <stddef.h>\n\n")

        // Include this module's header
        source.append("#include \"${def.name}.h\"\n")

        for (import in def.imports)
        {
            source.append("#include \"${import.name}.h\"\n")
        }

        source.append("\n")

        for (node in def.nodes)
        {
            when (node)
            {
                is FunctionDef -> genFunctionDef(node, mod)
                is ClassDef    -> genClassDef(node, mod)
                else            -> throw InternalCompilerException("Unhandled top-level node $node")
            }

            source.append("\n")
        }

        // Write source file to disk
        var sourceFile = File(
            irDir,
            def.name.substring(0, def.name.length) + ".c"
        )

        sourceFile.writeText(this.source.toString())

        // Write header file to disk
        File(
            irDir,
            def.name.substring(0, def.name.length) + ".h"
        ).writeText(this.header.toString())

        // Only the source file is passed to clang
        return sourceFile.path
    }

    private fun genClassDef(def: ClassDef, syms: SymTable)
    {
        val classType = syms.types[def.name]!!

        header.append("typedef struct ${classType.cName}\n{\n")

        for (node in def.nodes)
        {
            if (node is DeclarationStatement)
            {
                val type = syms.types[node.type]!!.cName

                header.append("$type ${node.name};\n")
            }
        }

        header.append("} ${def.cName};\n")

        for (node in def.nodes)
        {
            when (node)
            {
                is FunctionDef ->
                {
                    genFunctionDef(node, syms)
                }

                is ClassDef -> genClassDef(node, syms)
                else -> {}
            }
        }
    }

    private fun genFunctionDef(def: FunctionDef, syms: SymTable)
    {
        // 'externs' aren't added to the header
        if (def.modifiers.contains("extern").not())
        {
            genFunctionSignature(def, syms, header)
            header.append(";")
        }

        genFunctionSignature(def, syms, source)

        if (def.block != null)
        {
            genBlock(def.block, syms)
        }
        else
        {
            source.append(";")
        }
    }

    private fun genFunctionSignature(def: FunctionDef, syms: SymTable, dst: StringBuilder)
    {
        if (def.modifiers.contains("extern"))
        {
            source.append("extern ")
        }

        val returnType = syms.types[def.ret]!!.cName

        dst.append("$returnType ${def.cName}(")

        if (def.isInstance)
        {
            val parentCType = syms.types[def.parent]!!.cName

            dst.append("$parentCType this")
        }

        dst.append(def.params.toList().joinToString(", ")
        {
            val type = syms.types[it.second]!!.cName

            "$type ${it.first}"
        })
        dst.append(")")
    }

    private fun genBlock(block: Block, syms: SymTable)
    {
        source.append("\n{\n")

        for (stmnt in block)
        {
            when (stmnt)
            {
                is ReturnStatement  -> genReturnStatement(stmnt)
                is ExprStatement    -> genExprStatement(stmnt)
                is DeclarationStatement -> genDeclarationStatement(stmnt, syms)
                is WhenStatement    -> genWhenStatement(stmnt, syms)
                is LoopStatement    -> genLoopStatement(stmnt, syms)
                else                -> throw InternalCompilerException("Unhandled statement")
            }

            source.append(";\n")
        }

        source.append("}\n\n")
    }

    private fun genLoopStatement(stmnt: LoopStatement, syms: SymTable)
    {
        source.append("while  (")
        genExpr(stmnt.expr)
        source.append(")")
        genBlock(stmnt.block, syms)
    }

    private fun genWhenStatement(stmnt: WhenStatement, syms: SymTable)
    {
        // Generate primary branch
        source.append("if (")
        genExpr(stmnt.branches[0].expr)
        source.append(")")
        genBlock(stmnt.branches[0].block, syms)

        // Generate alternative branches
        for (i in 1..<stmnt.branches.size)
        {
            source.append("else if (")
            genExpr(stmnt.branches[i].expr)
            source.append(")")
            genBlock(stmnt.branches[i].block, syms)
        }

        // Generate final branch
        if (stmnt.elseBlock != null)
        {
            source.append("else")
            genBlock(stmnt.elseBlock, syms)
        }
    }

    private fun genDeclarationStatement(stmnt: DeclarationStatement, syms: SymTable)
    {
        val type = syms.types[stmnt.type]!!.cName

        source.append("$type ${stmnt.name}")

        if (stmnt.expr != null)
        {
            source.append(" = ")
            genExpr(stmnt.expr)
        }
    }

    private fun genExprStatement(stmnt: ExprStatement)
    {
        genExpr(stmnt.expr)
    }

    private fun genReturnStatement(stmnt: ReturnStatement)
    {
        source.append("return ")
        genExpr(stmnt.expr)
    }

    private fun genExpr(expr: Expr)
    {
        when (expr)
        {
            is BinaryExpr  -> genBinaryExpr(expr)
            is NumberExpr  -> genNumberExpr(expr)
            is IdentExpr   -> genVariableExpr(expr)
            is CallExpr    -> genCallExpr(expr)
            is BooleanExpr -> genBooleanExpr(expr)
            is CharExpr    -> genCharExpr(expr)
            is StringExpr  -> genStringExpr(expr)
            else           -> throw InternalCompilerException("Unimplemented codegen")
        }
    }

    private fun genBinaryExpr(expr: BinaryExpr)
    {
        source.append("(")

        if (expr.op == ".")
        {
            if (expr.right is CallExpr)
            {
                val callExpr = expr.right

                callExpr.args.addFirst(AnonArgument(expr.left))

                genCallExpr(callExpr)

                source.append(")")
                return
            }
        }

        if (expr.op != "::")
        {
            genExpr(expr.left)
            source.append(expr.op)
        }

        genExpr(expr.right)
        source.append(")")
    }

    private fun genCallExpr(expr: CallExpr)
    {
        source.append("${expr.cName}(")

        for (arg in expr.args)
        {
            genExpr((arg as AnonArgument).expr)

            if (arg !== expr.args.last())
                source.append(",")
        }

        source.append(")")
    }

    private fun genStringExpr(expr: StringExpr)
    {
        source.append("\"${expr.value}\"")
    }


    private fun genCharExpr(expr: CharExpr)
    {
        source.append("'${expr.value}'")
    }

    private fun genBooleanExpr(expr: BooleanExpr)
    {
        source.append(expr.value.toString())
    }

    private fun genNumberExpr(expr: NumberExpr)
    {
        source.append(expr.value)
    }

    private fun genVariableExpr(expr: IdentExpr)
    {
        source.append(expr.value)
    }
}