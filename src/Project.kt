import java.io.File

class Project(val name: String, val files: List<File>)
{
    val units: MutableList<Translation> = mutableListOf()

    fun build()
    {
        val irFiles = mutableListOf<String>()
        for (file in files)
        {
            val translation = Translation(file.name, file.readText())

            println("${GREEN}Parsing file${RESET}: ${file.name}")
            translation.parse()

            units += translation
            irFiles += translation.irFile.path
        }

        // Unit 0 is the main translation unit (main file)
        // Checking is done recursively, according to imports
        units[0].check(units)

        units.forEach {
            println("${GREEN}Generating IR for file${RESET}: ${it.name}")
            it.generate()
        }

        println("${GREEN}Compilation done${RESET}. Passing files to clang")

        val clangArgs = mutableListOf(
            "clang",
            "-Wno-unused-value",
            "-Wno-parentheses-equality",
            "-o",
            name,
            "stdlib.c",
        )

        clangArgs += irFiles

        val clangProcess = ProcessBuilder(clangArgs)
            .inheritIO()
            .start()

        clangProcess.waitFor()
    }
}