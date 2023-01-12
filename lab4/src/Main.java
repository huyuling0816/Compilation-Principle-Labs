import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;
import java.io.IOException;

public class Main {

    public static final BytePointer error = new BytePointer();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        String target = args[1];
//        String source = "/home/huyuling/桌面/Lab/src/test.txt";
//        String target = "/home/huyuling/桌面/Lab/src/test1.txt";
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        ParseTree tree = sysYParser.program();
        MyVisitor visitor = new MyVisitor();
        visitor.visit(tree);
        LLVMModuleRef moudule = visitor.getModule();
        if (LLVMPrintModuleToFile(moudule, target, error) != 0) {    // moudle是你自定义的LLVMModuleRef对象
            LLVMDisposeMessage(error);
        }
    }

    public static Integer convert(String text){
        if(text.startsWith("0x") || text.startsWith("0X")){
            text = String.valueOf(Integer.parseInt(text.substring(2), 16));
        }else if(text.startsWith("0") && text.length()>1){
            text = String.valueOf(Integer.parseInt(text.substring(1),8));
        }
        return Integer.parseInt(text);
    }

    public static class MyVisitor extends SysYParserBaseVisitor<LLVMValueRef>{

        LLVMModuleRef module = LLVMModuleCreateWithName("moudle");
        LLVMBuilderRef builder = LLVMCreateBuilder();
        LLVMTypeRef i32Type = LLVMInt32Type();

        final LLVMValueRef zero = LLVMConstInt(i32Type, 0, /* signExtend */ 0);

        final LLVMValueRef one = LLVMConstInt(i32Type, 1, /* signExtend */ 0);

        public LLVMModuleRef getModule(){
            return this.module;
        }

        public MyVisitor(){
            LLVMInitializeCore(LLVMGetGlobalPassRegistry());
            LLVMLinkInMCJIT();
            LLVMInitializeNativeAsmPrinter();
            LLVMInitializeNativeAsmParser();
            LLVMInitializeNativeTarget();
        }

        @Override
        public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
            LLVMTypeRef returnType = i32Type;
            PointerPointer<Pointer> argumentTypes = new PointerPointer<>(0);
            LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes,/* argumentCount */ 0, /* isVariadic */ 0);
            LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/"main", ft);
            LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, /*blockName:String*/"mainEntry");
            LLVMPositionBuilderAtEnd(builder, block);
            return super.visitFuncDef(ctx);
        }

        @Override
        public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
            SysYParser.ExpContext exp = ctx.exp();
            LLVMValueRef valueRef;
            if(exp instanceof SysYParser.NumContext){
                valueRef = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) exp).number().INTEGR_CONST().getText()), 0);
            }else{
                valueRef = visit(ctx.exp());
            }
            return valueRef;
        }

        @Override
        public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
            SysYParser.ExpContext exp = ctx.exp();
            LLVMValueRef valueRef;
            if(exp instanceof SysYParser.NumContext){
                valueRef = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) exp).number().INTEGR_CONST().getText()), 0);
            }else{
                valueRef = visit(ctx.exp());
            }
            if(ctx.unaryOp().PLUS()!=null){
                return valueRef;
            }else if(ctx.unaryOp().MINUS()!=null){
                return LLVMBuildSub(builder, zero, valueRef, "inverse");
            }else if(ctx.unaryOp().NOT()!=null){
                // 值为0, 表达式为false
                LLVMValueRef tmp_ = LLVMBuildICmp(builder, LLVMIntNE, valueRef, zero, "tmp_");
                tmp_ = LLVMBuildXor(builder, tmp_, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp_");
                return LLVMBuildZExt(builder, tmp_, i32Type, "tmp_");
            }
            return super.visitUnaryOpExp(ctx);
        }

        @Override
        public LLVMValueRef visitMdm(SysYParser.MdmContext ctx) {
            SysYParser.ExpContext left = ctx.exp(0);
            SysYParser.ExpContext right = ctx.exp(1);
            LLVMValueRef zuo;
            LLVMValueRef you;
            if(left instanceof SysYParser.NumContext){
                zuo = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) left).number().INTEGR_CONST().getText()), /* signExtend */ 0);
            }else{
                zuo = visit(ctx.exp(0));
            }
            if(right instanceof SysYParser.NumContext){
                you = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) right).number().INTEGR_CONST().getText()), /* signExtend */ 0);
            }else{
                you = visit(ctx.exp(1));
            }
            if(ctx.MUL()!=null){
                return LLVMBuildMul(builder, zuo, you, "mul");
            }else if(ctx.DIV()!=null){
                return LLVMBuildExactSDiv(builder, zuo, you, "sDiv");
            }else if(ctx.MOD()!=null){
                LLVMValueRef t = LLVMBuildMul(builder, you, LLVMBuildExactSDiv(builder, zuo, you, "sDiv"),"");
                return LLVMBuildSub(builder, zuo, t, "");
            }
            return super.visitMdm(ctx);
        }

        @Override
        public LLVMValueRef visitPm(SysYParser.PmContext ctx) {
            SysYParser.ExpContext left = ctx.exp(0);
            SysYParser.ExpContext right = ctx.exp(1);
            LLVMValueRef zuo;
            LLVMValueRef you;
            if(left instanceof SysYParser.NumContext){
                zuo = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) left).number().INTEGR_CONST().getText()), /* signExtend */ 0);
            }else{
                zuo = visit(ctx.exp(0));
            }
            if(right instanceof SysYParser.NumContext){
                you = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) right).number().INTEGR_CONST().getText()), /* signExtend */ 0);
            }else{
                you = visit(ctx.exp(1));
            }
            if(ctx.PLUS()!=null){
                return LLVMBuildAdd(builder, zuo, you, "add");
            }else if(ctx.MINUS()!=null){
                return LLVMBuildSub(builder, zuo, you, "sub");
            }
            return super.visitPm(ctx);
        }

        /**
         * RETURN指令
         * @param ctx the parse tree
         * @return
         */
        @Override
        public LLVMValueRef visitStmt8(SysYParser.Stmt8Context ctx) {
            if(ctx.exp() instanceof SysYParser.NumContext) {
                LLVMValueRef res = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) ctx.exp()).number().INTEGR_CONST().getText()), /* signExtend */ 0);
                LLVMBuildRet(builder, res);
            }else{
                LLVMValueRef llvmValueRef = visit(ctx.exp());
                LLVMBuildRet(builder, llvmValueRef);
            }
            return super.visitStmt8(ctx);
        }
    }
}