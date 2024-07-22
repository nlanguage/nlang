package symtable

data class Operator(
    val name: String,
    val rhs: String,
    val ret: String?,
    val func: String?
)