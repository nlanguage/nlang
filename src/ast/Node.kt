package ast

sealed class Node(open val pos: FilePos)

data class FunctionDef(
    val proto: Prototype,
    override val pos: FilePos
): Node(pos)

data class FunctionDecl(
    val proto: Prototype,
    val body: Block,
    override val pos: FilePos
): Node(pos)


data class Import(
    val name: String,
    override val pos: FilePos
): Node(pos)
