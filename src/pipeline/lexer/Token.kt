package pipeline.lexer

import util.FilePos

data class Token(
    val type: TokenType,
    val text: String,
    val pos: FilePos
)