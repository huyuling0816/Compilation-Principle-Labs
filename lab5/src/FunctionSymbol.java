import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class FunctionSymbol extends Scope implements Symbol{
    final LLVMTypeRef returnType;
    final LLVMValueRef llvmValueRef;

    int usedTime = 0;

    public FunctionSymbol(String name, Scope scope, LLVMValueRef llvmValueRef, LLVMTypeRef returnType) {
        super(name, scope);
        this.llvmValueRef = llvmValueRef;
        this.returnType = returnType;
    }

    public LLVMValueRef getLlvmValueRef(){
        return llvmValueRef;
    }

    public LLVMTypeRef getReturnType(){
        return returnType;
    }

    public void addUsedTime(){
        this.usedTime++;
    }

    public int getUsedTime(){
        return this.usedTime;
    }

}
