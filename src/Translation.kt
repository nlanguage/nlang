import java.io.File

class Translation(val name: String, val source: String)
{
    var nodes  = listOf<AstNode>()
    val syms   = SymbolTable()
    var irFile = File(System.getProperty("java.io.tmpdir"), name.substring(0, name.length - 2) + ".c")

    fun compile()
    {
        val lexer = Lexer(source);

        lexer.prime()

        nodes = Parser(lexer).parseProgram()

        Checker(nodes, syms).check()

        val ir = Generator(nodes, syms).generate()

        irFile.writeText(ir)
    }
}