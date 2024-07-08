import ast.Expr
import ast.Prototype
import ast.VarData

data class Operator(
    val name: String,
    val rhs: String,
    val ret: String?,
    val func: String?
)

data class Type(
    val name: String,
    val cName: String,
    var ops: List<Operator>,
    val funcs: MutableList<Prototype>,
    val membs: HashMap<String, VarData>
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

    run {
        val boolType = genType("bool", "bool")

        boolType.second.ops += listOf(
            Operator("&&", "bool", "bool", null),
            Operator("||", "bool", "bool", null)
        )

        boolType
    },

    genType("char", "char"),
    genType("string", "char*"),

    "void" to Type("void", "void", listOf(), mutableListOf(), hashMapOf())
)

// Helper methods to reduce the code-size of the builtin type table

fun genArithType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        name,
        cName,
        genArithOps(name) + genFullCompOps(name) + genFullAssignOps(name),
        mutableListOf(),
        hashMapOf()
    )
}

fun genType(name: String, cName: String): Pair<String, Type>
{
    return name to Type(
        name,
        cName,
        genBaseCompOps(name)  + genBaseAssignOps(name),
        mutableListOf(),
        hashMapOf()
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