package pipeline.lexer

// Token type is used for general comparison, use text for specifics
enum class TokenType
{
    IDENTIFIER,
    NUMERIC,
    BOOLEAN,
    CHARACTER,
    STRING,
    OPERATOR,
    DELIMITER,
    KEYWORD,
    EOF
}