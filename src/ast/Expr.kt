package ast

sealed class Expr(open val pos: FilePos)

data class VariableExpr(
    val name: String,
    override val pos: FilePos
): Expr(pos)

data class NumberExpr(
    val value: Number,
    override val pos: FilePos
): Expr(pos)

data class BooleanExpr(
    val value: Boolean,
    override val pos: FilePos
): Expr(pos)

data class StringExpr(
    val value: String,
    override val pos: FilePos
): Expr(pos)

data class CharExpr(
    val value: Char,
    override val pos: FilePos
): Expr(pos)

data class CallExpr(
    var callee: String, val args: List<Expr>,
    override val pos: FilePos
): Expr(pos)

data class BinaryExpr(
    val left: Expr,
    val op: String,
    val right: Expr,
    override val pos: FilePos
): Expr(pos)