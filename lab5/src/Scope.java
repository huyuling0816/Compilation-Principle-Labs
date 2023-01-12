import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {

    private final Scope enclosingScope;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private String name;

    public Scope(String name, Scope scope){
        this.name = name;
        this.enclosingScope = scope;
    }

    public String getName(){
        return this.name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Scope getEnclosingScope() {
        return this.enclosingScope;
    }

    public void updateSymbol(Symbol oldSymbol, Symbol newSymbol){
        String name = oldSymbol.getName();
        if(symbols.containsKey(name)){
            symbols.put(name, newSymbol);
        }else if(enclosingScope!=null){
            enclosingScope.updateSymbol(oldSymbol, newSymbol);
        }
    }

    public Map<String, Symbol> getSymbols() {
        return this.symbols;
    }

    public void define(Symbol symbol) {
        symbols.put(symbol.getName(), symbol);
    }

    public Symbol resolve(String name){
        Symbol symbol = symbols.get(name);
        if(symbol!=null){
            return symbol;
        }
        if(enclosingScope!=null){
            return enclosingScope.resolve(name);
        }
        return null;
    }

}
