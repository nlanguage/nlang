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

data class Prototype(
    var name: String,
    var cName: String,
    val args: List<Variable>,
    val returnType: String,
    var flags: List<Flag>,
)

sealed class Argument(open val pos: FilePos)

