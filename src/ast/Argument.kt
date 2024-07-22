package ast

// An argument to a function call
sealed class Argument

// Argument in the form 'name = expr'
// This is the default
class NamedArgument(
    val name: String,
    val expr: Expr
): Argument()

// Argument in the form 'expr'
// This is used in functions with the 'anon' modifier
class AnonArgument(
    val expr: Expr
): Argument()