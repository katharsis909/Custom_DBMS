package SEMANTIC.AST_NODES.LEAF_NODES;

public class Identifier
/*
Purpose: Resolves to a column or table name.
Execution need: It doesn’t evaluate directly — rather, its value is used as a key for lookup.
 */
{
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //define evaluate() here?
}
