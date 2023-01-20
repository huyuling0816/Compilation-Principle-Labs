import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class BaseSymbol implements Symbol{
    final String name;

    final LLVMValueRef llvmValueRef;

    boolean isFParam = false;

    boolean isArray = false;

    public BaseSymbol(String name, LLVMValueRef llvmValueRef) {
        this.name = name;
        this.llvmValueRef = llvmValueRef;
    }

    public String getName(){
        return this.name;
    }

    public LLVMValueRef getLlvmValueRef(){
        return llvmValueRef;
    }

    public void setFParam(boolean FParam) {
        isFParam = FParam;
    }

    public void setArray(boolean array){
        isArray = array;
    }

    public boolean getFParam(){
        return this.isFParam;
    }

    public boolean getArray(){
        return this.isArray;
    }

}
