package symtable

import ast.Expr
import util.FilePos

data class Variable(
    val type: String,
    var modifiers: List<String>,
    val expr: Expr?,
    var inferable: Boolean,
    var pos: FilePos,
)