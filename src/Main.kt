import java.io.File

fun main(args: Array<String>)
{
    if (args.size != 2)
    {
        println("Usage: kc <input file path> <output file path>")
        return
    }

    val source = File(args[0]).readText()

    val lexer  = Lexer(source);

    // Prime the lexer
    lexer.eat()

    // Parse and generate c code
    val parser = Parser(lexer);
    val generator = Generator(parser.parseProgram())
    val clangInput = generator.generate()

    // Write generated c code to a file to pass to clang
    val tempDir = System.getProperty("java.io.tmpdir")
    val clangInputFile = File(tempDir, args[0].substring(0, args[0].length - 2) + ".c")

    clangInputFile.writeText(clangInput)

    val clangProcess = ProcessBuilder("clang", "-Wno-unused-value", "-o", args[1], clangInputFile.path, "stdlib.c")
        .inheritIO()
        .start()

    clangProcess.waitFor()

    println("Compilation complete. Running program >>>")

    val userProcess = ProcessBuilder("./" + args[1])
        .inheritIO()
        .start()

    userProcess.waitFor()

    println("User process finished with exit code ${userProcess.exitValue()}")
}