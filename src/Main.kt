import java.io.File

fun main(args: Array<String>)
{
    if (args.size < 2)
    {
        println("Usage: nc <project> <main file> <other files>")
        return
    }

    val projFiles = mutableListOf<File>()
    for (i in 1..args.count() - 1)
    {
        projFiles += File(args[i])
    }

    val project = Project(args[0], projFiles)

    project.build()

    println("\nRunning compiled code:")

    val userProcess = ProcessBuilder("./" + args[0])
        .inheritIO()
        .start()

    userProcess.waitFor()

    println("User process finished with exit code ${userProcess.exitValue()}")
}