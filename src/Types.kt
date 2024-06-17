import ast.Prototype
import java.util.Dictionary

data class Operator(
    val name: String,
    val rhs: String,
    val ret: String?,
    val func: String?
)

typealias TypeTable = HashMap<String, List<Operator>>

val builtinTypes = hashMapOf(
    "u8"   to genArithOps("u8")   + genCompOps("u8")   + genAssignOps("u8"),
    "u16"  to genArithOps("u16")  + genCompOps("u16")  + genAssignOps("u16"),
    "u32"  to genArithOps("u32")  + genCompOps("u32")  + genAssignOps("u32"),
    "u64"  to genArithOps("u64")  + genCompOps("u64")  + genAssignOps("u64"),
    "uint" to genArithOps("uint") + genCompOps("uint") + genAssignOps("uint"),

    "i8"   to genArithOps("i8")   + genCompOps("i8")   + genAssignOps("i8"),
    "i16"  to genArithOps("i16")  + genCompOps("i16")  + genAssignOps("i16"),
    "i32"  to genArithOps("i32")  + genCompOps("i32")  + genAssignOps("i32"),
    "i64"  to genArithOps("i64")  + genCompOps("i64")  + genAssignOps("i64"),
    "int"  to genArithOps("int")  + genCompOps("int")  + genAssignOps("int"),

    "bool"   to genCompOps("bool")   + Operator("=", "bool", null, null),
    "string" to genCompOps("string") + Operator("=", "string", null, null),
    "char"   to genCompOps("char")   + Operator("=", "char", null, null),
)

// Helper methods to reduce the code-size of the builtin type table

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