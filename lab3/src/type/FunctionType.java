package type;

import symTable.Type;

import java.util.ArrayList;

public class FunctionType implements Type {
    /**
     * 返回类型
     */
    Type retTy;
    /**
     * 每个参数的类型
     */
    ArrayList<Type> paramsType;

    ArrayList<String> paramsNames;

    public Type getRetTy() {
        return retTy;
    }

    public  ArrayList<Type> getParamsType() {
        return paramsType;
    }

    public  ArrayList<String> getParamsNames() {
        return paramsNames;
    }

    public FunctionType(Type retTy, ArrayList<Type> types, ArrayList<String> names){
        this.retTy = retTy;
        this.paramsType = types;
        this.paramsNames = names;
    }

}
