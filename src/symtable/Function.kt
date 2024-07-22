package symtable

class Function(
    var name: String,
    var cName: String,
    val isInstance: Boolean,
    val params: HashMap<String, String>,
    var modifiers: List<String>,
    val ret: String
)