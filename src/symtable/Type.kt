package symtable

data class Type(
    val cName: String,
    var modifiers: List<String>,
    var opers: List<Operator>,
    override val types: HashMap<String, Type>,
    override var membs: HashMap<String, Variable>,
    override var funcs: Set<Function>,
): SymTable(types, membs, funcs)
{
    constructor(
        cName: String,
        modifiers: List<String>,
        opers: List<Operator>,
        symTable: SymTable
    ): this(cName, modifiers,opers, symTable.types, symTable.membs, symTable.funcs)

    constructor(
        cName: String,
        modifiers: List<String>
    ): this(cName, modifiers, listOf(), hashMapOf(), hashMapOf(), setOf())
}