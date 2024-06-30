import ast.*
import java.io.File

class Module(val file: File)
{
    var lexer   = Lexer(file.name, file.readText())
    var nodes   = mutableListOf<Node>()
    val funcs   = mutableListOf<Prototype>()
    val types   = TypeTable(builtinTypes)
    var checked = false

    fun handleImports(from: String, others: List<Module>)
    {
        // Cannot remove elements from array while iterating over them
        val toRemove = mutableListOf<Node>()
        val toAdd = mutableListOf<Node>()

        for (import in nodes)
        {
            if (import is Import)
            {
                toRemove += import

                // Check for cyclical dependencies
                if (import.name + ".n" == from)
                {
                    reportError("check", import.pos, "Cyclical dependency on '${import.name}'")
                }

                // Check for dependency on self
                if (import.name + ".n" == file.name)
                {
                    reportError("check", import.pos, "'${import.name}' cannot depend on itself")
                }

                val imported = others.find { it.file.name == import.name + ".n"} ?:
                    reportError("check", import.pos, "No file '${import.name}' found")

                // Recursively check other units. This recursion will go on until a file with no imports is found,
                // which is the bottom of the import tree
                if (!imported.checked)
                {
                    imported.handleImports(file.name, others)
                }

                for (impSym in imported.funcs)
                {
                    if (impSym.hasFlag("export"))
                    {
                        // Do not import the import's imports
                        if (impSym.hasFlag("imported"))
                        {
                            continue
                        }

                        // TODO: Check that not having .copy() isn't an issue
                        val externProto = impSym

                        externProto.flags += Flag("imported")

                        funcs += externProto
                        toAdd += FunctionDef(externProto, import.pos)

                    }
                }
            }
        }

        nodes.removeAll(toRemove)
        nodes += toAdd

        checked = true
    }

    fun registerSymbols()
    {
        for (node in nodes)
        {
            when (node)
            {
                is FunctionDef  -> registerFunction(node)
                is FunctionDecl -> registerFunction(node.def)
                else -> {}
            }
        }
    }

    private fun registerFunction(def: FunctionDef)
    {
        // External defs and 'main' are excluded from name mangling
        if (def.proto.flags.contains(Flag("extern")) || def.proto.name == "main")
        {
            def.proto.cName = def.proto.name
        }
        else
        {
            def.proto.cName = buildString {
                append("_Z${def.proto.name}")

                for (arg in def.proto.params)
                {
                    append("_${arg.type.alternatives.first()}")
                }
            }
        }

        funcs += def.proto
    }
}