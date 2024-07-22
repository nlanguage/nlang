package util

import kotlin.system.exitProcess

const val RESET = "\u001B[0m"
const val RED = "\u001B[31m"
const val GREEN = "\u001B[32m"

data class CompileError(val stage: String, val pos: FilePos, val msg: String): Exception(msg)

fun reportError(stage: String, pos: FilePos, msg: String): Nothing
{
    println("${RED}Error[$stage] (${pos.file} ${pos.line}:${pos.column})${RESET}: $msg")
    exitProcess(0)
}

fun reportError(e: CompileError): Nothing
{
    println("${RED}Error[${e.stage}] (${e.pos.file} ${e.pos.line}:${e.pos.column})${RESET}: ${e.msg}")
    exitProcess(0)
}

class InternalCompilerException(message: String) : Exception(message)
