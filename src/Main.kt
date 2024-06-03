import java.io.File

fun main(args: Array<String>)
{
    if (args.size != 2)
    {
        println("Usage: kc <input file path> <output file path>")
        return
    }

    println("\n")

    val source = File(args[0]).readText()

    reportCompilation(args[0])

    val lexer  = Lexer(source);

    lexer.prime()

    // Parse and generate c code
    val parser = Parser(lexer);
    val program = parser.parseProgram()

    Checker(program).check()

    val generator = Generator(program)
    val clangInput = generator.generate()

    // Write generated c code to a file to pass to clang
    val tempDir = System.getProperty("java.io.tmpdir")
    val clangInputFile = File(tempDir, args[0].substring(0, args[0].length - 2) + ".c")

    clangInputFile.writeText(clangInput)

    val clangProcess = ProcessBuilder("clang", "-Wno-unused-value", "-Wno-parentheses-equality", "-o", args[1], clangInputFile.path, "stdlib.c")
        .inheritIO()
        .start()

    clangProcess.waitFor()

    println("\nRunning compiled code:")

    val userProcess = ProcessBuilder("./" + args[1])
        .inheritIO()
        .start()

    userProcess.waitFor()

    println("User process finished with exit code ${userProcess.exitValue()}")
}