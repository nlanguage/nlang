import java.io.File

fun buildProject(name: String, filePaths: List<String>)
{
    val modules = mutableListOf<Module>()

    for (path in filePaths)
    {
        val module = Module(File(path))

        Parser(module).parse()

        modules += module
    }

    // Checking in recursive, starting from the main module
    Checker(modules[0], "", modules).check()

    val irFilePaths = mutableListOf<String>()
    for (module in modules)
    {
        irFilePaths += Generator(module).generate()
    }

    val clangArgs = mutableListOf(
        "clang",
        "-Wno-unused-value",
        "-Wno-parentheses-equality",
        "-o",
        name,
        "stdlib.c",
    )

    clangArgs += irFilePaths

    val clangProcess = ProcessBuilder(clangArgs)
        .inheritIO()
        .start()

    clangProcess.waitFor()
}

fun runProject(name: String)
{
    val userProcess = ProcessBuilder("./" + name)
        .inheritIO()
        .start()

    userProcess.waitFor()

    println("User process finished with exit code ${userProcess.exitValue()}")
}
