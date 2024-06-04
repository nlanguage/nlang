#include <stdio.h>
#include <stdbool.h>

void print_i(int integer)
{
    printf("%d\n", integer);
}

void print_c(char character)
{
    printf("%c\n", character);
}

void print_b(bool boolean)
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

void print_s(char* string)
{
    printf("%s\n", string);
}