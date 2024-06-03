#include <stdio.h>
#include <stdbool.h>

void print_int(int integer)
{
    printf("%d\n", integer);
}

void print_char(char character)
{
    printf("%c\n", character);
}

void print_bool(bool boolean)
{
    if (boolean)
    {
        printf("true\n");
    }
    else
    {
        printf("false\n");
    }
}

void print_string(char* string)
{
    printf("%s\n", string);
}