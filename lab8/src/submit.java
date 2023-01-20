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

public class submit {

    public static final BytePointer error = new BytePointer();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
//        String source = args[0];
//        String target = args[1];
        String source = "/home/huyuling/桌面/Lab/src/test.txt";
        String target = "/home/huyuling/桌面/Lab/src/test3.txt";
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
        private Scope globalScope = null;
        private Scope currentScope = null;
        private int localScopeCounter = 0;
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
        public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
            globalScope = new Scope("globalScope", null);
            currentScope = globalScope;
            visitChildren(ctx);
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
            String name = ctx.IDENT().getText();
            LLVMTypeRef returnType = null;
            if(ctx.funcType().INT() != null){
                returnType = i32Type;
            }
            int size = 0;
            if(ctx.funcFParams()!=null){
                size = ctx.funcFParams().funcFParam().size();
            }
            PointerPointer<Pointer> argumentTypes = new PointerPointer<>(size);
            for(int i=0; i<size; i++){
                argumentTypes.put(i ,i32Type);
            }
            LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes,/* argumentCount */ size, /* isVariadic */ 0);
            LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/name, ft);
            LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, /*blockName:String*/name+"Entry");
            LLVMPositionBuilderAtEnd(builder, block);
            FunctionSymbol symbol = new FunctionSymbol(name, currentScope, function, returnType);
            currentScope.define(symbol);
            currentScope = symbol;
            if(ctx.funcFParams()!=null){
                int index = 0;
                for(SysYParser.FuncFParamContext paramContext:ctx.funcFParams().funcFParam()){
                    LLVMValueRef n = LLVMGetParam(function, index);
                    index++;
                    String s = paramContext.IDENT().getText();
                    LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/s);
                    LLVMBuildStore(builder, n, pointer);
                    BaseSymbol baseSymbol = new BaseSymbol(s, pointer);
                    currentScope.define(baseSymbol);
                }
            }
            visitChildren(ctx);
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
            Scope localScope = new Scope("localScope" + localScopeCounter, currentScope);
            localScopeCounter++;
            currentScope = localScope;
            visitChildren(ctx);
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
            String name = ctx.IDENT().getText();
            // IDENT  ( L_BRACKT constExp R_BRACKT )*
            if(ctx.initVal() == null){
                if(ctx.constExp().size() == 0){
                    LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                }
            }// IDENT ( L_BRACKT constExp R_BRACKT )* ASSIGN initVal
            else{
                if(ctx.constExp().size() == 0){
                    LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                    if(ctx.initVal() instanceof SysYParser.InitVal1Context){
                        if(((SysYParser.InitVal1Context) ctx.initVal()).exp() instanceof SysYParser.NumContext){
                            String s = ((SysYParser.NumContext) ((SysYParser.InitVal1Context) ctx.initVal()).exp()).number().INTEGR_CONST().getText();
                            LLVMValueRef value = LLVMConstInt(i32Type, convert(s), /* signExtend */ 0);
                            LLVMBuildStore(builder, value, pointer);
                            BaseSymbol symbol = new BaseSymbol(name, pointer);
                            currentScope.define(symbol);
                        }
                    }
                }// 数组初始化
                else if(ctx.constExp().size() == 1){
                    int size = convert(((SysYParser.NumContext) ctx.constExp(0).exp()).number().INTEGR_CONST().getText());
                    LLVMTypeRef vectorType = LLVMVectorType(i32Type, size);
                    LLVMValueRef vectorPointer = LLVMBuildAlloca(builder, vectorType, name);
                    SysYParser.InitVal2Context context = (SysYParser.InitVal2Context) ctx.initVal();
                    for(int i=0; i<size; i++){
                        SysYParser.InitVal1Context init = (SysYParser.InitVal1Context) context.initVal(i);
                        LLVMValueRef value;
                        if(init.exp() instanceof SysYParser.NumContext){
                            value = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) init.exp()).number().INTEGR_CONST().getText()), 0);
                        }else{
                            value = visit(init.exp());
                        }
                        PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, i, 0)});
                        LLVMValueRef pointer = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "pointer"+i);
                        LLVMBuildStore(builder, value, pointer);
                        BaseSymbol baseSymbol = new BaseSymbol(name, vectorPointer);
                        currentScope.define(baseSymbol);
                    }
                }
            }
            return super.visitVarDef(ctx);
        }

        /**
         * IDENT L_PAREN funcRParams? R_PAREN
         * funcRParams : param (COMMA param)*
         * param : exp
         * @param ctx the parse tree
         * @return
         */
        @Override
        public LLVMValueRef visitCall(SysYParser.CallContext ctx) {
            String funcName = ctx.IDENT().getText();
            FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(funcName);
            LLVMValueRef function = functionSymbol.getLlvmValueRef();
            int size = 0;
            if(ctx.funcRParams()!=null){
                size = ctx.funcRParams().param().size();
            }
            PointerPointer<Pointer> arguments = new PointerPointer<>(size);
            for(int i=0; i<size; i++){
                SysYParser.ExpContext exp = ctx.funcRParams().param(i).exp();
                if(exp instanceof SysYParser.NumContext){
                    LLVMValueRef value = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) exp).number().INTEGR_CONST().getText()), 0);
                    arguments.put(i, value);
                }else{
                    LLVMValueRef value = visit(exp);
                    arguments.put(i, value);
                }
            }
            return LLVMBuildCall(builder, function, arguments, size, "call");
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
         * IDENT (L_BRACKT exp R_BRACKT)*
         * @param ctx the parse tree
         * @return
         */
        @Override
        public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
            String name = ctx.IDENT().getText();
            Symbol symbol = currentScope.resolve(name);
            if(ctx.exp().size() == 0){
                LLVMValueRef pointer = symbol.getLlvmValueRef();
                LLVMValueRef value = LLVMBuildLoad(builder, pointer, /*varName:String*/name);
                return value;
            }else{
                LLVMValueRef vectorPointer = symbol.getLlvmValueRef();
                int index = convert(((SysYParser.NumContext) ctx.exp(0)).number().INTEGR_CONST().getText());
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, index, 0)});
                LLVMValueRef res = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "res");
                LLVMValueRef value = LLVMBuildLoad(builder, res, name);
                return value;
            }
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
            return null;
        }
    }
}
