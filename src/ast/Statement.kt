package ast

sealed class Statement(open val pos: FilePos)

data class LoopStatement(
    val expr: Expr,
    val block: Block,
    override val pos: FilePos
): Statement(pos)

data class WhenStatement(
    val branches: List<Branch>,
    val elseBlock: Block?,
    override val pos: FilePos
): Statement(pos)


data class ReturnStatement(
    val expr: Expr,
    override val pos: FilePos
): Statement(pos)


data class ExprStatement(
    val expr: Expr,
    override val pos: FilePos
): Statement(pos)

data class DeclareStatement(
    val variable: Variable,
    val expr: Expr?,
    override val pos: FilePos
): Statement(pos)

data class AssignStatement(
    val name: String,
    val expr: Expr,
    override val pos: FilePos
): Statement(pos)
