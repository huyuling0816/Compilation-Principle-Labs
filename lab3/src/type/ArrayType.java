package type;

import symTable.Type;

public class ArrayType implements Type {
    /**
     * element类型, 可以为Array或者基本类型
     */
    Type subType;
    /**
     * element数量
     */
    int num;

    public ArrayType(Type type, int num){
        this.subType = type;
        this.num = num;
    }

    public Type getSubType() {
        return subType;
    }

    public int getNum() {
        return num;
    }
}
