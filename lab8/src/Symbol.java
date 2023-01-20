import org.bytedeco.llvm.LLVM.LLVMValueRef;

public interface Symbol {
    public String getName();

    public LLVMValueRef getLlvmValueRef();

}
