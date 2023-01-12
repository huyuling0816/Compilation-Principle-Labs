import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class BaseSymbol implements Symbol{
    final String name;

    final LLVMValueRef llvmValueRef;

    int usedTime = 0;

    public BaseSymbol(String name, LLVMValueRef llvmValueRef) {
        this.name = name;
        this.llvmValueRef = llvmValueRef;
    }

    public void addUsedTime(){
        this.usedTime++;
    }

    public int getUsedTime(){
        return this.usedTime;
    }

    public String getName(){
        return this.name;
    }

    public LLVMValueRef getLlvmValueRef(){
        return llvmValueRef;
    }

}
