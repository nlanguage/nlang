import java.io.File

import ast.Node
import ast.Prototype

typealias SymbolTable = HashMap<String, Prototype>

class Module(val file: File)
{
    var lexer   = Lexer(file.name, file.readText())
    var nodes   = mutableListOf<Node>()
    val syms    = SymbolTable()
    var checked = false
}