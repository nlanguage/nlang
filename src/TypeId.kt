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
            val newAlts = this.alternatives.intersect(other.alternatives)

            if (newAlts.isNotEmpty())
            {
                this.alternatives = newAlts
                return true
            }
            else
            {
                return false
            }
        }
        else
        {
            this.alternatives = other.alternatives
            return true
        }
    }
}