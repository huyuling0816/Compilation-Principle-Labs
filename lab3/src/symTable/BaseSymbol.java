package symTable;

import java.util.ArrayList;

public class BaseSymbol implements Symbol{

    final String name;

    final Type type;

    public ArrayList<ArrayList<Integer>> usedPosition = new ArrayList<>();

    public BaseSymbol(String name, Type type){
        this.name = name;
        this.type = type;
    }

    public String getName(){
        return name;
    }

    public Type getType(){
        return type;
    }

}
