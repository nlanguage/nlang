package ast

sealed class Node(open val pos: FilePos)

data class FunctionDef(
    val proto: Prototype,
    override val pos: FilePos
): Node(pos)

data class FunctionDecl(
    val def: FunctionDef,
    val body: Block,
): Node(def.pos)

data class Class(
    val name: String,
    val members: HashMap<String, VarData>,
    //val funcs: MutableList<FunctionDef>,
    override val pos: FilePos
): Node(pos)

data class Import(
    val name: String,
    override val pos: FilePos
): Node(pos)
