package symTable;

import type.FunctionType;

import java.util.ArrayList;

public class FunctionSymbol extends BaseScope implements Symbol{

    public ArrayList<ArrayList<Integer>> usedPosition = new ArrayList<>();

    public FunctionType functionType;
    public FunctionSymbol(String name, Scope enclosingScope) {
        super(name, enclosingScope);
    }

}
