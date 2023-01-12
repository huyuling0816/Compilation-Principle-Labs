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
        private Scope globalScope = null;
        private Scope currentScope = null;
        private int localScopeCounter = 0;

        private boolean hasReturned = false;
        LLVMModuleRef module = LLVMModuleCreateWithName("moudle");
        LLVMBuilderRef builder = LLVMCreateBuilder();
        LLVMTypeRef i32Type = LLVMInt32Type();

        final LLVMValueRef zero = LLVMConstInt(i32Type, 0, /* signExtend */ 0);

        final LLVMValueRef one = LLVMConstInt(i32Type, 1, /* signExtend */ 0);

        LLVMValueRef function = null;
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
            hasReturned = false;
            String name = ctx.IDENT().getText();
            LLVMTypeRef returnType;
            if(ctx.funcType().INT() != null){
                returnType = i32Type;
            }else{
                returnType = LLVMVoidType();
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
            this.function = function;
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
            if(!hasReturned){
                if(returnType == i32Type){
//                    LLVMBuildRet(builder, zero);
                }else{
                    LLVMBuildRetVoid(builder);
                }
            }
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

        /**
         * lVal ASSIGN exp SEMICOLON
         * IDENT (L_BRACKT exp R_BRACKT)*
         * @param ctx the parse tree
         * @return
         */
        @   Override
        public LLVMValueRef visitStmtAssign(SysYParser.StmtAssignContext ctx) {
            LLVMValueRef left;
            BaseSymbol symbol = (BaseSymbol) currentScope.resolve(ctx.lVal().IDENT().getText());
            if(ctx.lVal().exp().size() == 0){
                left = symbol.getLlvmValueRef();
//                LLVMBuildLoad(builder, left, ctx.lVal().IDENT().getText());
            } else {// 数组
                int index = convert(((SysYParser.NumContext) ctx.lVal().exp(0)).number().INTEGR_CONST().getText());
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, index, 0)});
                // NullPointerException
                left = LLVMBuildGEP(builder, symbol.getLlvmValueRef(), valuePointer, 2, "res");
//                LLVMBuildLoad(builder, left, ctx.lVal().IDENT().getText());
            }
            BaseSymbol newSymbol;
            if(ctx.exp() instanceof SysYParser.NumContext) {
                String s = ((SysYParser.NumContext) ctx.exp()).number().INTEGR_CONST().getText();
                LLVMValueRef value = LLVMConstInt(i32Type, convert(s), /* signExtend */ 0);
                LLVMBuildStore(builder, value, left);
                if (ctx.lVal().exp().size() == 0) {
                    newSymbol = new BaseSymbol(symbol.getName(), left);
                }else{
                    newSymbol = new BaseSymbol(symbol.getName(), symbol.getLlvmValueRef());
                }
                currentScope.updateSymbol(symbol, newSymbol);
            }else{
                LLVMValueRef right = visit(ctx.exp());
                LLVMBuildStore(builder, right, left);
                if (ctx.lVal().exp().size() == 0) {
                    newSymbol = new BaseSymbol(symbol.getName(), left);
                }else{
                    newSymbol = new BaseSymbol(symbol.getName(), symbol.getLlvmValueRef());
                }
                currentScope.updateSymbol(symbol, newSymbol);
            }
            return null;
        }

        /**
         * constDef : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal;
         * @param ctx the parse tree
         * @return
         */
        @Override
        public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
            String name = ctx.IDENT().getText();
            if(ctx.constExp().size() == 0){
                LLVMValueRef pointer;
                if(currentScope == globalScope){
                    pointer = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                }else{
                    pointer = LLVMBuildAlloca(builder, i32Type, name);
                }
                if(((SysYParser.ConstInitVal1Context) ctx.constInitVal()).constExp().exp() instanceof SysYParser.NumContext){
                    String s = ((SysYParser.NumContext) ((SysYParser.ConstInitVal1Context) ctx.constInitVal()).constExp().exp()).number().INTEGR_CONST().getText();
                    LLVMValueRef value = LLVMConstInt(i32Type, convert(s), 0);
                    if(currentScope == globalScope){
                        LLVMSetInitializer(pointer, value);
                    }else {
                        LLVMBuildStore(builder, value, pointer);
                    }
                    BaseSymbol symbol = new BaseSymbol(name, pointer);
                    currentScope.define(symbol);
                }else{
                    LLVMValueRef value = visit(((SysYParser.ConstInitVal1Context) ctx.constInitVal()).constExp().exp());
                    if(currentScope == globalScope){
                        LLVMSetInitializer(pointer, value);
                    }else {
                        LLVMBuildStore(builder, value, pointer);
                    }
                    BaseSymbol symbol = new BaseSymbol(name, pointer);
                    currentScope.define(symbol);
                }
            }else if(ctx.constExp().size() == 1){
                int leftSize = convert(((SysYParser.NumContext)ctx.constExp(0).exp()).number().INTEGR_CONST().getText());
                LLVMTypeRef vectorType = LLVMVectorType(i32Type, leftSize);
                LLVMValueRef vectorPointer;
                if(currentScope == globalScope){
                    vectorPointer = LLVMAddGlobal(module, vectorType, /*globalVarName:String*/name);
                }else{
                    vectorPointer = LLVMBuildAlloca(builder, vectorType, name);
                }
                PointerPointer<Pointer> arguments = new PointerPointer<>(leftSize);
                SysYParser.ConstInitVal2Context context = (SysYParser.ConstInitVal2Context) ctx.constInitVal();
                int rightSize = context.constInitVal().size();
                for(int i=0; i<rightSize; i++){
                    SysYParser.ConstInitVal1Context init = (SysYParser.ConstInitVal1Context) context.constInitVal(i);
                    LLVMValueRef value;
                    if(init.constExp().exp() instanceof SysYParser.NumContext){
                        value = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) init.constExp().exp()).number().INTEGR_CONST().getText()),0);
                    }else{
                        value = visit(init.constExp().exp());
                    }
                    arguments.put(i, value);
                    if(currentScope!=globalScope) {
                        PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, i, 0)});
                        LLVMValueRef pointer = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "pointer");
                        LLVMBuildStore(builder, value, pointer);
                    }
                }
                if(rightSize < leftSize){
                    for(int i=rightSize; i<leftSize; i++){
                        arguments.put(i, zero);
                        if(currentScope!=globalScope) {
                            PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, i, 0)});
                            LLVMValueRef pointer = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "pointer");
                            LLVMBuildStore(builder, zero, pointer);
                        }
                    }
                }
                if(currentScope == globalScope){
                    LLVMValueRef valueRef = LLVMConstVector(arguments, leftSize);
                    LLVMSetInitializer(vectorPointer, valueRef);
                }
                BaseSymbol baseSymbol = new BaseSymbol(name, vectorPointer);
                currentScope.define(baseSymbol);
            }
            return null;
        }

        @Override
        public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
            String name = ctx.IDENT().getText();
            // IDENT  ( L_BRACKT constExp R_BRACKT )*
            if(ctx.initVal() == null){
                if(ctx.constExp().size() == 0){
                    LLVMValueRef pointer;
                    if(currentScope == globalScope){
                        pointer = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                    }else{
                        pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                    }
                    BaseSymbol symbol = new BaseSymbol(name, pointer);
                    currentScope.define(symbol);
                }else{
                    int leftSize = convert(((SysYParser.NumContext) ctx.constExp(0).exp()).number().INTEGR_CONST().getText());
                    LLVMTypeRef vectorType = LLVMVectorType(i32Type, leftSize);
                    if(currentScope == globalScope) {
                        PointerPointer<Pointer> arguments = new PointerPointer<>(leftSize);
                        for(int i=0; i<leftSize; i++){
                            arguments.put(i, zero);
                        }
                        LLVMValueRef vectorPointer = LLVMAddGlobal(module, vectorType, name);
                        LLVMValueRef valueRef = LLVMConstVector(arguments, leftSize);
                        LLVMSetInitializer(vectorPointer, valueRef);
                        BaseSymbol symbol = new BaseSymbol(name, vectorPointer);
                        currentScope.define(symbol);
                    }
                }
            }// IDENT ( L_BRACKT constExp R_BRACKT )* ASSIGN initVal
            else{
                if(ctx.constExp().size() == 0){
                    if(currentScope == globalScope){
                        LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                        if (ctx.initVal() instanceof SysYParser.InitVal1Context) {
                            if (((SysYParser.InitVal1Context) ctx.initVal()).exp() instanceof SysYParser.NumContext) {
                                String s = ((SysYParser.NumContext) ((SysYParser.InitVal1Context) ctx.initVal()).exp()).number().INTEGR_CONST().getText();
                                LLVMValueRef value = LLVMConstInt(i32Type, convert(s), /* signExtend */ 0);
                                LLVMSetInitializer(globalVar, value);
                                BaseSymbol symbol = new BaseSymbol(name, globalVar);
                                currentScope.define(symbol);
                            } else {
                                LLVMValueRef value = visit(((SysYParser.InitVal1Context) ctx.initVal()).exp());
                                LLVMSetInitializer(globalVar, value);
                                BaseSymbol symbol = new BaseSymbol(name, globalVar);
                                currentScope.define(symbol);
                            }
                        }
                    }else {
                        LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                        if (ctx.initVal() instanceof SysYParser.InitVal1Context) {
                            if (((SysYParser.InitVal1Context) ctx.initVal()).exp() instanceof SysYParser.NumContext) {
                                String s = ((SysYParser.NumContext) ((SysYParser.InitVal1Context) ctx.initVal()).exp()).number().INTEGR_CONST().getText();
                                LLVMValueRef value = LLVMConstInt(i32Type, convert(s), /* signExtend */ 0);
                                LLVMBuildStore(builder, value, pointer);
                                BaseSymbol symbol = new BaseSymbol(name, pointer);
                                currentScope.define(symbol);
                            } else {
                                LLVMValueRef value = visit(((SysYParser.InitVal1Context) ctx.initVal()).exp());
                                LLVMBuildStore(builder, value, pointer);
                                BaseSymbol symbol = new BaseSymbol(name, pointer);
                                currentScope.define(symbol);
                            }
                        }
                    }
                }// 数组初始化
                else if(ctx.constExp().size() == 1){
                    int leftSize = convert(((SysYParser.NumContext) ctx.constExp(0).exp()).number().INTEGR_CONST().getText());
                    LLVMTypeRef vectorType = LLVMVectorType(i32Type, leftSize);
                    LLVMValueRef vectorPointer;
                    if(currentScope == globalScope){
                        vectorPointer = LLVMAddGlobal(module, vectorType, name);
                    }else{
                        vectorPointer = LLVMBuildAlloca(builder, vectorType, name);
                    }
                    SysYParser.InitVal2Context context = (SysYParser.InitVal2Context) ctx.initVal();
                    int rightSize = context.initVal().size();
                    PointerPointer<Pointer> arguments = new PointerPointer<>(leftSize);
                    for(int i=0; i<rightSize; i++) {
                        SysYParser.InitVal1Context init = (SysYParser.InitVal1Context) context.initVal(i);
                        LLVMValueRef value;
                        // nullPointer
                        if(init.exp() instanceof SysYParser.NumContext){
                            value = LLVMConstInt(i32Type, convert(((SysYParser.NumContext) init.exp()).number().INTEGR_CONST().getText()), 0);
                        }else{
                            value = visit(init.exp());
                        }
                        arguments.put(i, value);
                        if(currentScope!=globalScope) {
                            PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, i, 0)});
                            LLVMValueRef pointer = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "pointer");
                            LLVMBuildStore(builder, value, pointer);
                        }
                    }
                    if(rightSize<leftSize){
                        for(int i=rightSize; i<leftSize; i++){
                            arguments.put(i, zero);
                            if(currentScope!=globalScope) {
                                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, LLVMConstInt(i32Type, i, 0)});
                                LLVMValueRef pointer = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "pointer");
                                LLVMBuildStore(builder, zero, pointer);
                            }
                        }
                    }
                    if(currentScope == globalScope){
                        LLVMValueRef valueRef = LLVMConstVector(arguments, leftSize);
                        LLVMSetInitializer(vectorPointer, valueRef);
                    }
                    BaseSymbol baseSymbol = new BaseSymbol(name, vectorPointer);
                    currentScope.define(baseSymbol);
                }
            }
            return null;
        }

        /**
         * IF L_PAREN cond R_PAREN stmt ( ELSE stmt )?
         * @param ctx the parse tree
         * @return
         */
        @Override
        public LLVMValueRef visitStmt4(SysYParser.Stmt4Context ctx) {
            LLVMValueRef tmp_ = visit(ctx.cond());
            tmp_ = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), tmp_, "tmp_");
            LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(this.function, "true");
            LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(this.function, "false");
            LLVMBasicBlockRef entryBlock = LLVMAppendBasicBlock(this.function, "entry");
            LLVMBuildCondBr(builder,
                    /*condition:LLVMValueRef*/ tmp_,
                    /*ifTrue:LLVMBasicBlockRef*/ trueBlock,
                    /*ifFalse:LLVMBasicBlockRef*/ falseBlock);
            LLVMPositionBuilderAtEnd(builder, trueBlock);
            visit(ctx.stmt(0));
            LLVMBuildBr(builder, entryBlock);
            LLVMPositionBuilderAtEnd(builder, falseBlock);
            if(ctx.stmt().size() == 2){
                visit(ctx.stmt(1));
            }
            LLVMBuildBr(builder, entryBlock);
            LLVMPositionBuilderAtEnd(builder, entryBlock);
            return null;
        }

        @Override
        public LLVMValueRef visitCond(SysYParser.CondContext ctx) {
            if(ctx.exp() != null){
                if(ctx.exp() instanceof SysYParser.NumContext){
                    return LLVMConstInt(i32Type, convert(((SysYParser.NumContext) ctx.exp()).number().INTEGR_CONST().getText()), 0);
                }else {
                    return visit(ctx.exp());
                }
            }
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef right = visit(ctx.cond(1));
            LLVMValueRef tmp_ = null;
            if(ctx.NEQ()!=null){ // !=
                tmp_ = LLVMBuildICmp(builder, LLVMIntNE, left, right, "tmp_");
            }else if(ctx.EQ()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMIntEQ, left, right, "tmp_");
            }else if(ctx.LT()!=null){ // <
                tmp_ = LLVMBuildICmp(builder, LLVMIntULT, left, right, "tmp_");
            }else if(ctx.GT()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMIntUGT, left, right, "tmp_");
            }else if(ctx.LE()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMIntULE, left, right, "tmp_");
            }else if(ctx.GE()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMIntUGE, left, right, "tmp_");
            }else if(ctx.AND()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMAnd, left, right, "tmp_");
            }else if(ctx.OR()!=null){
                tmp_ = LLVMBuildICmp(builder, LLVMOr, left, right, "tmp_");
            }
            tmp_ = LLVMBuildZExt(builder, tmp_, i32Type, "tmp_");
            return tmp_;
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
            if(functionSymbol.returnType == i32Type){
                return LLVMBuildCall(builder, function, arguments, size, "call");
            }else{
                return LLVMBuildCall(builder, function, arguments, size, "");
            }

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
//                LLVMValueRef t = LLVMBuildMul(builder, you, LLVMBuildExactSDiv(builder, zuo, you, "sDiv"),"");
//                return LLVMBuildSub(builder, zuo, t, "");
                return LLVMBuildSRem(builder, zuo, you, "sRem");
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
//            symbol.addUsedTime();
            if(ctx.exp().size() == 0){
                LLVMValueRef pointer = symbol.getLlvmValueRef();
                return LLVMBuildLoad(builder, pointer, /*varName:String*/name);
            }else{
                // Exception in thread "main" java.lang.ClassCastException: class SysYParser$LvalContext cannot be cast to class SysYParser$NumContext (SysYParser$LvalContext and SysYParser$NumContext are in unnamed module of loader 'app') at Main$MyVisitor.visitLVal(Main.java:567) at Main$MyVisitor.visitLVal(Main.java:46)
                LLVMValueRef vectorPointer = symbol.getLlvmValueRef();
                LLVMValueRef value;
                if(ctx.exp(0) instanceof SysYParser.NumContext){
                    int index;
                    index = convert(((SysYParser.NumContext) ctx.exp(0)).number().INTEGR_CONST().getText());
                    value = LLVMConstInt(i32Type, index, 0);
                }else{
                    value = visit(ctx.exp(0));
                }
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero, value});
                LLVMValueRef res = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, "res");
                return LLVMBuildLoad(builder, res, name);
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
                this.hasReturned = true;
            }else{
                if(ctx.exp()!=null) {
                    LLVMValueRef llvmValueRef = visit(ctx.exp());
                    LLVMBuildRet(builder, llvmValueRef);
                    this.hasReturned = true;
                }
            }
            return null;
        }

    }
}