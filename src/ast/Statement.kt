package ast

sealed class Statement

data class LoopStatement(
    val expr: Expr,
    val block: Block
): Statement()

data class WhenStatement(
    val branches: List<Branch>,
    val elseBlock: Block?
): Statement()

data class ReturnStatement(
    val expr: Expr
): Statement()

data class ExprStatement(
    val expr: Expr
): Statement()

data class DeclareStatement(
    val variable: Variable,
    val expr: Expr
): Statement()

data class AssignStatement(
    val name: String,
    val expr: Expr
): Statement()
