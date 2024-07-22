import pipeline.buildProject
import pipeline.runProject

fun main(args: Array<String>)
{
    if (args.size < 2)
    {
        println("Usage: nlang <project> <main file> <other files>")
        return
    }

    buildProject(args[0], args.slice(1..<args.size))
    runProject(args[0])
}