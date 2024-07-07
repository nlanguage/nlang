package ast

data class FilePos(
    val file: String,
    val line: Int,
    val column: Int
)

data class VarData(val type: String, val mutable: Boolean, val expr: Expr?, var inferable: Boolean, var pos: FilePos)

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
    val params: HashMap<String, String>,
    val ret: String,
    var flags: List<Flag>
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
    val expr: Expr
): Argument()

class AnonArgument(
    val expr: Expr
): Argument()

