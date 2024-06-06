import java.io.File

class Translation(val name: String, val source: String)
{
    var nodes   = listOf<AstNode>()
    val syms    = SymbolTable()
    var irFile  = File(System.getProperty("java.io.tmpdir"), name.substring(0, name.length - 2) + ".c")
    var checked = false

    fun parse()
    {
        val lexer = Lexer(source);

        lexer.prime()

        nodes = Parser(lexer).parseProgram()
    }

    fun check(otherUnits: List<Translation>)
    {
        println("${GREEN}Checking file${RESET}: ${name}")
        Checker(nodes, syms, otherUnits).check()
        checked = true
    }

    fun generate()
    {
        val ir = Generator(nodes, syms).generate()

        irFile.writeText(ir)
    }
}