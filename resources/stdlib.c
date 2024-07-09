#include <stdio.h>
#include <stdbool.h>

void print_i(int integer)
{
    printf("%d", integer);
}

void print_c(char character)
{
    printf("%c", character);
}

void print_b(bool boolean)
{
    if (boolean)
    {
        printf("true");
    }
    else
    {
        printf("false");
    }
}

void print_s(char* string)
{
    printf("%s", string);
}