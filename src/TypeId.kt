class TypeId
{
    var alternatives: Set<String> = setOf()

    constructor()

    constructor(alternatives: Set<String>)
    {
        this.alternatives = alternatives
    }

    constructor(primary: String)
    {
        this.alternatives = setOf(primary)
    }

    fun checkOrAssign(other: TypeId): Boolean
    {
        if (this.alternatives.isNotEmpty())
        {
            this.alternatives = this.alternatives.intersect(other.alternatives)
        }
        else
        {
            this.alternatives = other.alternatives
        }

        return this.alternatives.isNotEmpty()
    }
}