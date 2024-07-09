# The N Programming Language

This is the main repository for the N programming language and its primary compiler, ``nlang``. N is a new programming
language designed for general and systems programming. N aims to be a reasonable language, one that simply gets the job
done, and stays out of the user's way. It's not overly innovative, but it is consistent, pragmatic and capable, it is
designed for productive software development. N takes inspiration from many languages, including c, c++, kotlin and rust

# Sample
Below is a basic sample of the language, **in its current state**, the language is still early in development and is
subject to change.
```kotlin
import io

// Classes Are how we store data
class date
(
    val year: uint
    val month: uint
    val day: uint
)

// Functions have named params by default
fun date_from_string(input: string) -> date @anon
{
    ...
}

class person
(
    var name: string
    val birthday: date
)

fun create_person(name: string, bday: string) -> person
{
    val p: person

    p.name = name

    p.birthday = date_from_string(bday)

    return p
}


fun main() -> i32
{
    val p = create_person("John", "25/02/2002")

    print(p.name)

    return 0
}
```

# Roadmap
[X] Lexing
[X] Parsing
[X] Typechecker with inference
[X] Basic Control flow
[X] Basic compiler infrastructure
[X] Classes
[X] Modules
[ ] Class member functions
[ ] Namespaces
[ ] Pointers and Arrays
[ ] Control flow analysis
[ ] Pattern Matching
[ ] Generics
[ ] World Domination

# Contributions
Any and all contributions are welcome!