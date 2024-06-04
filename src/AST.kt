data class Block(val statements: List<Statement>)
data class Pair(val name: String, val type: String)
data class Prototype(val name: String, val args: List<Pair>, val returnType: String)

sealed class Expr(open val pos: FilePos)
data class VariableExpr(val name: String, override val pos: FilePos): Expr(pos)
data class NumberExpr(val value: Number, override val pos: FilePos): Expr(pos)
data class BooleanExpr(val value: Boolean, override val pos: FilePos): Expr(pos)
data class StringExpr(val value: String, override val pos: FilePos): Expr(pos)
data class CharExpr(val value: Char, override val pos: FilePos): Expr(pos)
data class CallExpr(val callee: String, val args: List<Expr>, override val pos: FilePos): Expr(pos)
data class BinaryExpr(val left: Expr, val op: String, val right: Expr, override val pos: FilePos): Expr(pos)

data class Branch(val expr: Expr, val block: Block)

sealed class Statement
data class IfStatement(val branches: List<Branch>, val elseBlock: Block?): Statement()
data class ReturnStatement(val expr: Expr): Statement()
data class ExprStatement(val expr: Expr): Statement()
data class DeclareStatement(val mutable: Boolean, val name: String, var type: String?, val expr: Expr): Statement()
data class AssignStatement(val name: String, val expr: Expr): Statement()

sealed class AstNode
data class Extern(val proto: Prototype): AstNode()
data class Function(val proto: Prototype, val body: Block): AstNode()

typealias SymbolTable = HashMap<String, Prototype>

data class Program(val nodes: List<AstNode>, val syms: SymbolTable)