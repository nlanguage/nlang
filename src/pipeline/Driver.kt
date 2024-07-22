package pipeline

import ast.Import
import ast.ModuleDef
import pipeline.analysis.Analyser
import symtable.Module
import util.FilePos
import util.reportError
import java.io.File
import java.io.IOException
import java.nio.file.Paths

fun buildProject(name: String, filePaths: List<String>)
{
    val modDefs = mutableListOf<ModuleDef>()
    val modules = mutableListOf<Module>()

    // Phase 1: lex and parse
    for (path in filePaths)
    {
        val def = Parser(path).parseModule()
        modDefs += def
    }

    // Phase 2: analysis
    Analyser(modDefs, modules).analyseModule(Import(modDefs.first().name, FilePos("<program root>", 0, 0)))

    val irFilePaths = mutableListOf<String>()

    // Phase 3: C code generation

    // Create a dir in the system's temp dir
    val irDir = File(System.getProperty("java.io.tmpdir"), name)

    if (irDir.exists().not() && irDir.mkdir().not())
    {
        throw IOException("Failed to create directory $irDir")
    }

    for ((mod, def) in modules.zip(modDefs))
    {
        irFilePaths += Generator(mod, def, irDir).generateModule()
    }

    // Phase 3: Clang compilation
    val stdlib = Paths.get(object {}.javaClass.getResource("../stdlib.c")!!.toURI()).toAbsolutePath().toString()

    val clangArgs = mutableListOf(
        "clang",
        "-Wno-unused-value",
        "-Wno-parentheses-equality",
        "-Wno-string-compare",
        "-o",
        name,
        stdlib,
    )

    clangArgs += irFilePaths

    val clangProcess = ProcessBuilder(clangArgs)
        .inheritIO()
        .start()

    clangProcess.waitFor()
}

// Run a built executable
fun runProject(name: String)
{
    val userProcess = ProcessBuilder("./" + name)
        .inheritIO()
        .start()

    userProcess.waitFor()

    println("User process finished with exit code ${userProcess.exitValue()}")
}
