import ast.Prototype

data class Operator(
    val name: String,
    val rhs: String,
    val ret: String?,
    val func: String?
)

data class Type(
    val name: String,
    val cName: String,
    val ops: List<Operator>,
    val funcs: List<Prototype>
)

typealias TypeTable = HashMap<String, Type>

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

    genType("bool", "bool"),
    genType("char", "char"),
    genType("string", "char*"),

    "void" to Type("void", "void", listOf(), listOf())
)

// Helper methods to reduce the code-size of the builtin type table

fun genArithType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        name,
        cName,
        genArithOps(name) + genCompOps(name) + genAssignOps(name),
        listOf()
    )
}

fun genType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        name,
        cName,
        genCompOps(name)  + Operator("=", name, null, null),
        listOf()
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

fun genCompOps(type: String): List<Operator>
{
    return listOf(
        Operator("==", type, "bool", null),
        Operator("!=", type, "bool", null),
        Operator(">=", type, "bool", null),
        Operator("<=", type, "bool", null),
        Operator(">",  type, "bool", null),
        Operator("<",  type, "bool", null),
    )
}

fun genAssignOps(type: String): List<Operator>
{
    return listOf(
        Operator("=",  type, null, null),
        Operator("+=", type, null, null),
        Operator("-=", type, null, null),
        Operator("*=", type, null, null),
        Operator("/=", type, null, null),
    )
}