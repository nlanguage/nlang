package symtable

class Module(
    val name: String,
    override val types: HashMap<String, Type>,
    override var membs: HashMap<String, Variable>,
    override var funcs: Set<Function>
): SymTable(types, membs, funcs)
{
    constructor(name: String): this(name, builtinTypes, hashMapOf(), setOf())
    constructor(name: String, symTable: SymTable) : this(name, symTable.types, symTable.membs, symTable.funcs)
}