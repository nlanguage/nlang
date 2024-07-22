package symtable

import util.reportError

open class SymTable(
    open val types: HashMap<String, Type>,
    open var membs: HashMap<String, Variable>,
    open var funcs: Set<Function>
)
{
    constructor(syms: SymTable): this(syms.types, syms.membs, syms.funcs)

    fun getStatic(): SymTable
    {
        return SymTable(
            types,
            HashMap(membs.filter { it.value.modifiers.contains("static") }),
            funcs.filter { it.isInstance.not() }.toSet(),
        )
    }

    fun getNonStatic(): SymTable
    {
        return SymTable(
            types,
            HashMap(membs.filter { it.value.modifiers.contains("static").not() }),
            funcs.filter { it.isInstance }.toSet(),
        )
    }

    fun getExports(): SymTable
    {
        // Collect all symbols with the 'export' modifier
        val newTable = SymTable(
            HashMap(types.filter { it.value.modifiers.contains("export") }),
            HashMap(membs.filter { it.value.modifiers.contains("export") }),
            funcs.filter { it.modifiers.contains("export") }.toSet(),
        )

        // Remove the 'export' modifier from the new list
        // This needs to be done otherwise these exports will be exported again later
        // Also add an 'imported' modifier, this is picked up in codegen
        newTable.types.forEach{ it.value.modifiers -= "export" }
        newTable.membs.forEach{ it.value.modifiers -= "export" }
        newTable.funcs.forEach{ it.modifiers -= "export" }


        return newTable
    }

    operator fun plusAssign(syms: SymTable)
    {
        types += syms.types
        membs += syms.membs
        funcs += syms.funcs
    }
}