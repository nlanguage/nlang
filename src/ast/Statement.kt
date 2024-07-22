package ast

import util.FilePos

typealias Block = List<Statement>

// Statement inherits Node(), as certain statements can be top level, such as declarations
sealed class Statement(override val pos: FilePos): Node(pos)

data class Branch(
    val expr: Expr,
    val block: Block
)

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

data class DeclarationStatement(
    val name: String,
    var type: String?,
    val modifiers: List<String>,
    val expr: Expr?,
    override val pos: FilePos
): Statement(pos)
