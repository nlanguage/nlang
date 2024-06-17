package ast

data class FilePos(
    val file: String,
    val line: Int,
    val column: Int
)

data class Variable(
    val name: String,
    var type: String?,
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

data class Prototype(
    var name: String,
    val args: List<Variable>,
    val returnType: String,
    var flags: List<Flag>,
)