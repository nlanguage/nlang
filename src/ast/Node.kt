package ast

import util.FilePos

sealed class Node(open val pos: FilePos)

data class Import(
    val name: String,
    val pos: FilePos
)

data class ModuleDef(
    val name: String,
    val imports: List<Import>,
    val nodes: MutableList<Node>,
)

class ClassDef(
    val name: String,
    var modifiers: List<String>,
    val nodes: MutableList<Node>,
    override val pos: FilePos
): Node(pos)
{
    val cName: String = "_Z$name"
}

data class FunctionDef(
    var name: String,
    val parent: String?,
    val isInstance: Boolean,
    val params: HashMap<String, String>,
    var modifiers: List<String>,
    val ret: String,
    val block: Block?,
    override val pos: FilePos
): Node(pos)
{
    val cName: String

    init
    {
        cName = if (modifiers.contains("extern") || name == "main")
        {
            name
        } else
        {
            buildString {
                append("_Z${name}")

                for (arg in params)
                {
                    append("_${arg.value}")
                }
            }
        }
    }
}