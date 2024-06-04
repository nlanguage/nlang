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

            reportCompilation(file.name)

            translation.compile()

            units += translation

            irFiles += translation.irFile.path
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