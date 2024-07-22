package pipeline.analysis

import ast.Import
import symtable.Module
import ast.ModuleDef
import symtable.SymTable
import util.reportError

class Analyser(var defs: List<ModuleDef>, var modules: MutableList<Module>)
{
    fun analyseModule(import: Import): SymTable
    {
        // Check if we have already analysed this module
        var module = modules.find { it.name == import.name }

        if (module != null)
        {
            // Return the module's exported symbols
            return module.getExports()
        }

        // We have not analysed the module, analyse it
        var def = defs.find { it.name == import.name }

        // No module with that name exists
        if (def == null)
        {
            reportError("import", import.pos, "No module found with name ${import.name}")
        }

        module = TypeChecker(this).checkModule(def)

        // Add the module to the list of checked Modules
        modules += module

        return module.getExports()
    }
}
