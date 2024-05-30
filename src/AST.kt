sealed class Expr
data class VariableExpr(val name: String): Expr()
data class NumberExpr(val value: Number): Expr()
data class CallExpr(val callee: String, val args: List<Expr>): Expr()
data class BinaryExpr(val left: Expr, val op: String, val right: Expr): Expr()

sealed class Statement
data class ReturnStatement(val expr: Expr): Statement()

data class Block(val statements: List<Statement>)

sealed class AstNode
data class Prototype(val name: String, val args: List<String>): AstNode()
data class Function(val proto: Prototype, val body: Block): AstNode()

data class Program(val nodes: List<AstNode>)