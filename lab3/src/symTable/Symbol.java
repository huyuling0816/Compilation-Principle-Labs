package symTable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public interface Symbol {
    public String getName();

    public ArrayList<ArrayList<Integer>> usedPosition = new ArrayList<>();

}