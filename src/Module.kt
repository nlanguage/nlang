import java.io.File

import ast.Node
import ast.Prototype

typealias FuncTable = HashMap<String, Prototype>

class Module(val file: File)
{
    var lexer   = Lexer(file.name, file.readText())
    var nodes   = mutableListOf<Node>()
    val funcs   = FuncTable()
    val types   = TypeTable(builtinTypes)
    var checked = false
}