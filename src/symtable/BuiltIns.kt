package symtable

val builtinTypes = hashMapOf(
    genArithType("u8", "uint8_t"),
    genArithType("u16", "uint16_t"),
    genArithType("u32", "uint32_t"),
    genArithType("u64", "uint64_t"),
    genArithType("uint", "size_t"),

    genArithType("i8", "int8_t"),
    genArithType("i16", "int16_t"),
    genArithType("i32", "int32_t"),
    genArithType("i64", "int64_t"),
    genArithType("int", "ptrdiff_t"),

    // Only booleans can have AND and OR operations
    run {
        val boolType = genType("bool", "bool")

        boolType.second.opers += listOf(
            Operator("&&", "bool", "bool", null),
            Operator("||", "bool", "bool", null)
        )

        boolType
    },

    genType("char", "char"),
    genType("string", "char*"),

    "void" to Type("void", listOf(), listOf(), hashMapOf(), hashMapOf(), setOf())
)

// Helper methods to reduce the code-size of the builtin type table

fun genArithType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        cName,
        listOf(),
        genArithOps(name) + genFullCompOps(name) + genFullAssignOps(name),
        hashMapOf(),
        hashMapOf(),
        setOf(),
    )
}

fun genType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        cName,
        listOf(),
        genBaseCompOps(name)  + genBaseAssignOps(name),
        hashMapOf(),
        hashMapOf(),
        setOf(),
    )
}

fun genArithOps(type: String): List<Operator>
{
    return listOf(
        Operator("+", type, type, null),
        Operator("-", type, type, null),
        Operator("*", type, type, null),
        Operator("/", type, type, null),
    )
}

fun genFullCompOps(type: String): List<Operator>
{
    return genBaseCompOps(type) + listOf(
        Operator(">=", type, "bool", null),
        Operator("<=", type, "bool", null),
        Operator(">",  type, "bool", null),
        Operator("<",  type, "bool", null),
    )
}

fun genBaseCompOps(type: String): List<Operator>
{
    return listOf(
        Operator("==", type, "bool", null),
        Operator("!=", type, "bool", null),
    )
}

fun genFullAssignOps(type: String): List<Operator>
{
    return genBaseAssignOps(type) + listOf(
        Operator("+=", type, null, null),
        Operator("-=", type, null, null),
        Operator("*=", type, null, null),
        Operator("/=", type, null, null),
    )
}

fun genBaseAssignOps(type: String): List<Operator>
{
    return listOf(
        Operator("=",  type, null, null),
    )
}