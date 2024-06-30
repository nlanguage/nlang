package ast

import TypeId

data class FilePos(
    val file: String,
    val line: Int,
    val column: Int
)

data class Variable(
    val name: String,
    var type: TypeId,
    val mutable: Boolean,
    val pos: FilePos
)

data class Block(
    val statements: List<Statement>
)

data class Branch(
    val expr: Expr,
    val block: Block
)

data class Flag(
    val value: String
)

class Prototype(
    var name: String,
    var cName: String,
    val params: List<Variable>,
    val returnType: String,
    var flags: List<Flag>,
)
{
    fun hasFlag(flag: String): Boolean
    {
        return flags.contains(Flag(flag))
    }
}

sealed class Argument

class NamedArgument(
    val name: String,
    val expr: Expr,
): Argument()

class AnonArgument(
    val expr: Expr,
): Argument()


