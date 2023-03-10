import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import symTable.*;
import type.*;
import java.io.IOException;
import java.util.*;

public class Main
{
    static boolean isWrong = false;
    static String[] symbolicNames;
    static String[] ruleNames;
    static int visitTime = 0;
    static String substitute;
    static int lineNo;
    static int column;

    /**
     * lineNo -> wrongCount
     */
    static HashMap<Integer, Integer> wrongMap = new HashMap<>();
    static HashMap<String, Scope> map = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        for(int i=1; i<1000; i++){
            wrongMap.put(i, 0);
        }
        String source = args[0];
        lineNo = Integer.parseInt(args[1]);
        column = Integer.parseInt(args[2]);
        substitute = args[3];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
//        sysYParser.removeErrorListeners();
//        ErrorListener errorListener = new ErrorListener();
//        sysYParser.addErrorListener(errorListener);
        isWrong = false;
        ruleNames = sysYParser.getRuleNames();
        symbolicNames = sysYLexer.getRuleNames();
        ParseTree tree = sysYParser.program();
        MyVisitor visitor = new MyVisitor();
        visitor.visit(tree);
        visitTime+=1;
        visitor.visit(tree);
    }

    public static class MyVisitor extends SysYParserBaseVisitor{

        private GlobalScope globalScope = null;

        private Scope currentScope = null;

        private int localScopeCounter = 0;

        private int counter = 0;

        public MyVisitor(){};

        public MyVisitor(GlobalScope globalScope, Scope scope, int localScopeCounter){
            this.globalScope = globalScope;
            this.currentScope = scope;
            this.localScopeCounter = localScopeCounter;
        }

        @Override
        public Object visitProgram(SysYParser.ProgramContext ctx) {
            if(visitTime == 1){
                currentScope = globalScope;
                visitChildren(ctx);
                currentScope = currentScope.getEnclosingScope();
                return null;
            }
            // ????????????scope
            globalScope = new GlobalScope(null);
            currentScope = globalScope;
            // ???????????????
            visitChildren(ctx);
            // ??????scope
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public Object visitFuncDef(SysYParser.FuncDefContext ctx) {
            if(visitTime == 1){
                currentScope = map.get(ctx.IDENT().getText());
                visitChildren(ctx);
                currentScope = currentScope.getEnclosingScope();
                return null;
            }
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            // ????????????scope
            String typeName = ctx.funcType().getText();
            globalScope.resolve(typeName);
            String fucName = ctx.IDENT().getText();
            FunctionSymbol fun = new FunctionSymbol(fucName, currentScope);
            // ??????func??????
            String s = ctx.funcType().getText();
            Type retType = (Type) globalScope.resolve(s);

            if(ctx.funcFParams() == null){ // func????????????
                fun.functionType = new FunctionType(retType, null, null);
            }else { // func?????????
                ArrayList<Type> types = new ArrayList<>();
                ArrayList<String> names = new ArrayList<>();
                for (SysYParser.FuncFParamContext funcFParamContext : ctx.funcFParams().funcFParam()) {
                    if(names.contains(funcFParamContext.IDENT().getText())){
                        isWrong = true;
                        System.err.println("Error type 3 at Line " + lineNo +": ??????????????????");
                    }else {
                        Type bType = (Type) globalScope.resolve(funcFParamContext.bType().getText());
                        List<TerminalNode> terminalNode = funcFParamContext.L_BRACKT();
                        if (terminalNode.size() == 0) {
                            types.add(bType);
                        } else {
                            types.add(new ArrayType(bType, terminalNode.size()));
                        }
                        names.add(funcFParamContext.IDENT().getText());
                    }
                }
                fun.functionType = new FunctionType(retType, types, names);
            }
            // ??????????????????????????????
            if(currentScope.getSymbols().containsKey(fucName)){
                isWrong = true;
                System.err.println("Error type 4 at Line " + lineNo +": ??????????????????");
                return null;
            }
            fun.usedPosition.add(new ArrayList<>(){{add(lineNo);add(ctx.IDENT().getSymbol().getCharPositionInLine());}});
            currentScope.define(fun);
            currentScope = fun;
            map.put(currentScope.getName(), currentScope);
            // ???????????????
            visitChildren(ctx);
            // ??????scope
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public Object visitBlock(SysYParser.BlockContext ctx) {
            // ???????????????
            if(visitTime == 1){
                currentScope = map.get("LocalScope"+counter);
                counter++;
                visitChildren(ctx);
                currentScope = currentScope.getEnclosingScope();
                return null;
            }
            // ???????????????
            // ????????????scope
            LocalScope localScope = new LocalScope(currentScope);
            String localScopeName = localScope.getName() + localScopeCounter;
            localScope.setName(localScopeName);
            localScopeCounter++;
            currentScope = localScope;
            map.put(currentScope.getName(), currentScope);
            // ???????????????
            visitChildren(ctx);
            // ??????scope
            currentScope = currentScope.getEnclosingScope();
            return null;
        }

        @Override
        public Object visitConstDecl(SysYParser.ConstDeclContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            String typeName = ctx.bType().getText();
            Type type = (Type) globalScope.resolve(typeName);
            List<SysYParser.ConstDefContext> conNames = ctx.constDef();
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            // ???????????????????????????????????????, ?????????????????????????????????
            Scope temp = currentScope;
            ArrayList<String> fParams = new ArrayList<>();
            while (!temp.getClass().equals(FunctionSymbol.class)){
                temp = temp.getEnclosingScope();
                if(temp == null) break;
            }
            if(temp!=null){
                fParams = ((FunctionSymbol) temp).functionType.getParamsNames();
            }

            for(SysYParser.ConstDefContext conName:conNames){
                String name = conName.IDENT().getText();
                // ??????????????????
                if(currentScope.getSymbols().containsKey(name) || (fParams!=null && fParams.contains(name))){
                    isWrong = true;
                    System.err.println("Error type 3 at Line " + lineNo +": ??????????????????");
                }
                // ?????????????????????
                ConstantSymbol constantSymbol;
                if(conName.constExp().size() == 0){
                    constantSymbol = new ConstantSymbol(name, type);
                }else{
                    ArrayType arrayType = new ArrayType(type, conName.constExp().size());
                    constantSymbol = new ConstantSymbol(name, arrayType);
                }
                constantSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(conName.IDENT().getSymbol().getCharPositionInLine());}});
                currentScope.define(constantSymbol);

            }
            return null;
        }

        @Override
        public Object visitVarDecl(SysYParser.VarDeclContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            String typeName = ctx.bType().getText();
            Type type = (Type) globalScope.resolve(typeName);
            List<SysYParser.VarDefContext> varNames = ctx.varDef();
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            // ???????????????????????????????????????, ?????????????????????????????????
            Scope temp = currentScope;
            ArrayList<String> fParams = new ArrayList<>();
            while (!temp.getClass().equals(FunctionSymbol.class)){
                temp = temp.getEnclosingScope();
                if(temp == null) break;
            }
            if(temp!=null){
                fParams = ((FunctionSymbol) temp).functionType.getParamsNames();
            }

            for(SysYParser.VarDefContext varName:varNames){
                String name = varName.IDENT().getText();
                // ??????????????????
                if(currentScope.getSymbols().containsKey(name) || (fParams!=null && fParams.contains(name))){
                    isWrong = true;
                    System.err.println("Error type 3 at Line " + lineNo +": ??????????????????");
                }
                VariableSymbol variableSymbol;
                if(varName.constExp().size() == 0){
                    variableSymbol = new VariableSymbol(name, type);
                }else{
                    ArrayType arrayType = new ArrayType(type, varName.constExp().size());
                    variableSymbol = new VariableSymbol(name, arrayType);
                }
                variableSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(varName.IDENT().getSymbol().getCharPositionInLine());}});
                currentScope.define(variableSymbol);
            }
            return null;
        }

        @Override
        public Object visitFuncFParam(SysYParser.FuncFParamContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            String typeName = ctx.bType().getText();
            Type type = (Type) globalScope.resolve(typeName);
            String varName = ctx.IDENT().getText();
            VariableSymbol variableSymbol;
            if(ctx.L_BRACKT().size() == 0){
                variableSymbol = new VariableSymbol(varName, type);
            }else{
                ArrayType arrayType = new ArrayType(type, ctx.L_BRACKT().size());
                variableSymbol = new VariableSymbol(varName,arrayType);
            }
            variableSymbol.usedPosition.add(new ArrayList<>(){{add(ctx.getStart().getLine());add(ctx.IDENT().getSymbol().getCharPositionInLine());}});
            currentScope.define(variableSymbol);
            return null;
        }

        @Override
        public Object visitCall(SysYParser.CallContext ctx) {
            int count = 0;
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            String varName = ctx.IDENT().getText();
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            if(currentScope.resolve(varName) == null){
                isWrong = true;
                System.err.println("Error type 2 at Line "+lineNo+": ???????????????");
                return null;
            }else if(!currentScope.resolve(varName).getClass().equals(FunctionSymbol.class) && !currentScope.getEnclosingScope().resolve(varName).getClass().equals(FunctionSymbol.class)){
                isWrong = true;
                System.err.println("Error type 10 at Line "+lineNo+": ???????????????????????????");
                return null;
            }else{
                // ??????????????????????????????
                Symbol symbol = currentScope.resolve(varName);
                if(!symbol.getClass().equals(FunctionSymbol.class)) symbol = currentScope.getEnclosingScope().resolve(varName);
                ((FunctionSymbol)symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(ctx.IDENT().getSymbol().getCharPositionInLine());}});
                if(symbol.getClass().equals(FunctionSymbol.class)){
                    List<Type> fParamTypes = ((FunctionSymbol) symbol).functionType.getParamsType();
                    // ?????????
                    if(fParamTypes==null){
                        if(ctx.funcRParams()!=null){
                            isWrong = true;

                             System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                            return null;
                        }
                    }else {
                        if (ctx.funcRParams() == null || ctx.funcRParams().param().size()!=fParamTypes.size()){
                            isWrong = true;

                            System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                            return null;
                        }else{
                            List<SysYParser.ParamContext> rParams = ctx.funcRParams().param();
                            for(int i=0; i<fParamTypes.size(); i++){
                                Type fParaType = fParamTypes.get(i);
                                SysYParser.ParamContext rParam = rParams.get(i);
//                                if(rParam.exp().getClass().equals(SysYParser.LvalContext.class)){
//                                    if(currentScope.resolve(((SysYParser.LvalContext)rParam.exp()).lVal().IDENT().getText())==null){
//                                        return null;
//                                    }
//                                }
                                int dimension1 = -1;
                                int dimension2 = -1;
                                if(fParaType.getClass().equals(BasicTypeSymbol.class)){
                                    dimension1 = 0;
                                }else if(fParaType.getClass().equals(ArrayType.class)){
                                    dimension1 = 1;
                                }
                                if (rParam.exp().getClass().equals(SysYParser.NumContext.class)){
                                    if(!fParaType.getClass().equals(BasicTypeSymbol.class)){
                                        isWrong = true;

                                        System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                                        return null;
                                    }
                                }else if(rParam.exp().getClass().equals(SysYParser.CallContext.class)){
                                    String name = ((SysYParser.CallContext) rParam.exp()).IDENT().getText();
                                    Symbol symbol1 = currentScope.resolve(name);
                                    if(!symbol1.getClass().equals(FunctionSymbol.class)) symbol1 = currentScope.getEnclosingScope().resolve(name);
                                    if(((BasicTypeSymbol)((FunctionSymbol)symbol1).functionType.getRetTy()).getName().equals("void") || dimension1 == 1){
                                        isWrong = true;

                                        System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                                        return null;
                                    }
                                }else if(rParam.exp().getClass().equals(SysYParser.LvalContext.class)){
                                    String name = ((SysYParser.LvalContext)rParam.exp()).lVal().IDENT().getText();
                                    // lass symTable.FunctionSymbol cannot be cast to class symTable.BaseSymbol
                                    Symbol rParamSymbol = currentScope.resolve(name);
                                    if(rParamSymbol!=null && rParamSymbol.getClass().equals(FunctionSymbol.class)){
                                        isWrong = true;
                                        System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                                        return null;
                                    }else if(rParamSymbol!=null){
                                        if(((BaseSymbol) rParamSymbol).getType().getClass().equals(BasicTypeSymbol.class)){
                                            dimension2 = 0;
                                        }else if(((BaseSymbol) rParamSymbol).getType().getClass().equals(ArrayType.class)){
                                            dimension2 = ((ArrayType)((BaseSymbol) rParamSymbol).getType()).getNum() - ((SysYParser.LvalContext)rParam.exp()).lVal().exp().size();
                                        }
                                    }
                                    if(dimension1!=dimension2) {
                                        isWrong = true;
                                        System.err.println("Error type 8 at Line "+lineNo+": ?????????????????????");
                                        return null;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public Object visitLVal(SysYParser.LValContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            String varName = ctx.IDENT().getText();
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            Symbol symbol = currentScope.resolve(varName);
            if(symbol == null){
                isWrong = true;
                System.err.println("Error type 1 at Line "+lineNo+": ???????????????");
                return null;
            }
            if(symbol.getClass().equals(FunctionSymbol.class)){
                if (ctx.exp().size() > 0) {
                    isWrong = true;
                    System.err.println("Error type 9 at Line " + lineNo + ": ?????????????????????????????????");
                }
            }else {
                ((BaseSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(ctx.IDENT().getSymbol().getCharPositionInLine());}});
                if (!((BaseSymbol) symbol).getType().getClass().equals(ArrayType.class)) {
                    if (ctx.exp().size() > 0) {
                        isWrong = true;
                        System.err.println("Error type 9 at Line " + lineNo + ": ?????????????????????????????????");
                    }
                }else{
                    /*
                     * int main(){
                     *     int p[9];
                     *     p[0][0] = 1;
                     * }
                     */
                    int d = ((ArrayType)((BaseSymbol) symbol).getType()).getNum();
                    if(d<ctx.exp().size()){
                        isWrong = true;
                        System.err.println("Error type 9 at Line " + lineNo + ": ?????????????????????????????????");
                    }
                }
            }
            return null;
        }

        @Override
        public Object visitStmtAssign(SysYParser.StmtAssignContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            SysYParser.LValContext lValContext = ctx.lVal();
            SysYParser.ExpContext expContext = ctx.exp();
            int leftDimension=0;
            int rightDimension=0;
            Symbol left = currentScope.resolve(lValContext.IDENT().getText());

            if(left!=null) {
                if(left.getClass().equals(BaseSymbol.class)){
                    ((BaseSymbol)left).usedPosition.add(new ArrayList<>(){{add(lineNo);add(lValContext.IDENT().getSymbol().getCharPositionInLine());}});
                }
                if(left.getClass().equals(FunctionSymbol.class)){
                    isWrong = true;
                    System.err.println("Error type 11 at Line " + lineNo + ": ???????????????????????????????????????");
                    return null;
                }
                // ??????
                if (left.getClass().equals(BasicTypeSymbol.class)) {
                    leftDimension = 0;
                } else if (((BaseSymbol) left).getType().getClass().equals(ArrayType.class)) {
                    ArrayType arrayType = (ArrayType) ((BaseSymbol) left).getType();
                    int d = lValContext.exp().size();
                    leftDimension = arrayType.getNum() - d;
                }

                // ??????
                if (expContext.getClass().equals(SysYParser.NumContext.class)) {
                    rightDimension = 0;
                } else if (expContext.getClass().equals(SysYParser.LvalContext.class)) {
                    Symbol right = currentScope.resolve(((SysYParser.LvalContext) expContext).lVal().IDENT().getText());
                    if(right!=null) {
                        if(right.getClass().equals(BaseSymbol.class)){
                            ((BaseSymbol)right).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.LvalContext) expContext).lVal().IDENT().getSymbol().getCharPositionInLine());}});
                        }
                        if(right.getClass().equals(FunctionSymbol.class)){
                            isWrong = true;
                            System.err.println("Error type 5 at Line " + lineNo + ": ??????????????????????????????");
                            return null;
                        }
                        if (right.getClass().equals(BasicTypeSymbol.class)) {
                            rightDimension = 0;
                        } else if (((BaseSymbol)right).getType().getClass().equals(ArrayType.class)) {
                            ArrayType arrayType = (ArrayType) ((BaseSymbol)right).getType();
                            int d = ((SysYParser.LvalContext) expContext).lVal().exp().size();
                            rightDimension = arrayType.getNum() - d;
                        }
                    }else{
                        return null;
                    }
                }else if (expContext.getClass().equals(SysYParser.CallContext.class)){
                    FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(((SysYParser.CallContext) expContext).IDENT().getText());
                    functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext)expContext).IDENT().getSymbol().getCharPositionInLine());}});
                    Type type = functionSymbol.functionType.getRetTy();
                    if(((BasicTypeSymbol)type).getName().equals("int")){
                        rightDimension = 0;
                    }
                }
                if (leftDimension != rightDimension) {
                    isWrong = true;
                    System.err.println("Error type 5 at Line " + lineNo + ": ??????????????????????????????");
                }
            }
            return null;
        }

        @Override
        public Object visitMdm(SysYParser.MdmContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ??????????????????????????????int
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            for(SysYParser.ExpContext expContext : ctx.exp()){
                if(expContext.getClass().equals(SysYParser.LvalContext.class)){
                    SysYParser.LValContext lvalContext = ((SysYParser.LvalContext) expContext).lVal();
                    Symbol symbol = currentScope.resolve(lvalContext.IDENT().getText());
                    if(symbol == null) return null;
                    if (symbol.getClass().equals(FunctionSymbol.class)){
                        isWrong = true;
                        System.err.println("Error type 6 at Line "+lineNo+": ???????????????????????????int?????????array???function???");
                        break;
                    }else{
                        ((BaseSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.LvalContext) expContext).lVal().IDENT().getSymbol().getCharPositionInLine());}});
                        if(((BaseSymbol) symbol).getType().getClass().equals(ArrayType.class)){
                            int d = ((ArrayType)((BaseSymbol) symbol).getType()).getNum();
                            if( d > ((SysYParser.LvalContext) expContext).lVal().exp().size()) {
                                isWrong = true;
                                System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                                break;
                            }
                        }
                    }
                }else if(expContext.getClass().equals(SysYParser.CallContext.class)){
                    FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(((SysYParser.CallContext) expContext).IDENT().getText());
                    functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext) expContext).IDENT().getSymbol().getCharPositionInLine());}});
                    if(((BasicTypeSymbol)functionSymbol.functionType.getRetTy()).getName().equals("void")){
                        isWrong = true;
                        System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                        break;
                    }
                }
            }
            return null;
        }

        @Override
        public Object visitPm(SysYParser.PmContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ??????????????????????????????int
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            for(SysYParser.ExpContext expContext : ctx.exp()){
                if(expContext.getClass().equals(SysYParser.LvalContext.class)){
                    SysYParser.LValContext lvalContext = ((SysYParser.LvalContext) expContext).lVal();
                    Symbol symbol = currentScope.resolve(lvalContext.IDENT().getText());
                    if(symbol == null) return null;
                    if (symbol.getClass().equals(FunctionSymbol.class)){
                        isWrong = true;
                        System.err.println("Error type 6 at Line "+lineNo+": ???????????????????????????int?????????array???function???");
                        break;
                    }else{
                        ((BaseSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(lvalContext.IDENT().getSymbol().getCharPositionInLine());}});
                        if(((BaseSymbol) symbol).getType().getClass().equals(ArrayType.class)){
                            int d = ((ArrayType)((BaseSymbol) symbol).getType()).getNum();
                            if( d > ((SysYParser.LvalContext) expContext).lVal().exp().size()) {
                                isWrong = true;
                                System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                                break;
                            }
                        }
                    }
                }else if(expContext.getClass().equals(SysYParser.CallContext.class)){
                    FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(((SysYParser.CallContext) expContext).IDENT().getText());
                    functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext) expContext).IDENT().getSymbol().getCharPositionInLine());}});
                    if(((BasicTypeSymbol)functionSymbol.functionType.getRetTy()).getName().equals("void")){
                        isWrong = true;
                        System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                        break;
                    }
                }
            }
            return null;
        }

        @Override
        public Object visitConstDef(SysYParser.ConstDefContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????(????????????????????????????????????????????????????????????????????????; ??????????????????int???????????????????????????????????????)
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            int leftDimension = 0;
            int rightDimension = 0;
            if (ctx.constExp().size() <= 1){
                SysYParser.ConstInitValContext constInitValContext = ctx.constInitVal();
                // ??????????????????????????????????????????
                if(constInitValContext.getClass().equals(SysYParser.ConstInitVal1Context.class)) {
                    leftDimension = ctx.constExp().size();
                    if (((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp().getClass().equals(SysYParser.NumContext.class)) {
                        rightDimension = 0;
                    } else if (((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp().getClass().equals(SysYParser.LvalContext.class)) {
                        BaseSymbol right = (BaseSymbol) currentScope.resolve(((SysYParser.LvalContext) ((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp()).lVal().IDENT().getText());
                        if(right!=null) {
                            right.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.LvalContext) ((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp()).lVal().IDENT().getSymbol().getCharPositionInLine());}});
                            if (right.getType().getClass().equals(BasicTypeSymbol.class)) {
                                rightDimension = 0;
                            } else if (right.getType().getClass().equals(ArrayType.class)) {
                                ArrayType arrayType = (ArrayType) right.getType();
                                int d = ((SysYParser.LvalContext) ((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp()).lVal().exp().size();
                                rightDimension = arrayType.getNum() - d;
                            }
                        }else{
                            return null;
                        }
                    } else if(((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp().getClass().equals(SysYParser.MdmContext.class) || ((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp().getClass().equals(SysYParser.PmContext.class)){
                        if(ctx.constExp().size() == 1){
                            isWrong = true;
                            System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
                        }
                        return null;
                    }
                    else if(((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp().getClass().equals(SysYParser.CallContext.class)){
                        String funcName = ((SysYParser.CallContext)((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp()).IDENT().getText();
                        FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(funcName);
                        functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext)((SysYParser.ConstInitVal1Context) constInitValContext).constExp().exp()).IDENT().getSymbol().getCharPositionInLine());}});
                        if(functionSymbol!=null){
                            if(functionSymbol.functionType.getRetTy().getClass().equals(BasicTypeSymbol.class)){
                                rightDimension = 0;
                            }else if(functionSymbol.functionType.getRetTy().getClass().equals(ArrayType.class)){
                                rightDimension = ((ArrayType)functionSymbol.functionType.getRetTy()).getNum();
                            }
                        }
                    }
                    // constInitVal??????constInitVal?????????constExp
                }else if (constInitValContext.getClass().equals(SysYParser.ConstInitVal2Context.class) && ctx.constExp().size() == 1){
                    if (!((SysYParser.ConstInitVal2Context) constInitValContext).constInitVal(0).getClass().equals(SysYParser.ConstInitVal1Context.class)){
                        isWrong = true;
                        System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
                    }
                }
            }
            if(leftDimension != rightDimension){
                isWrong = true;
                System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
            }
            return null;
        }

        @Override
        public Object visitVarDef(SysYParser.VarDefContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ????????????
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            int leftDimension = 0;
            int rightDimension = 0;
            if(ctx.initVal()!=null){ // ???????????????varDef2?????????
                if(ctx.constExp().size()<=1){
                    SysYParser.InitValContext initValContext = ctx.initVal();
                    // ?????????????????????????????????????????????
                    if(initValContext.getClass().equals(SysYParser.InitVal1Context.class)){
                        leftDimension = ctx.constExp().size();
                        if(((SysYParser.InitVal1Context) initValContext).exp().getClass().equals(SysYParser.NumContext.class)){
                            rightDimension = 0;
                        }else if(((SysYParser.InitVal1Context) initValContext).exp().getClass().equals(SysYParser.LvalContext.class)){
                            Symbol right = currentScope.resolve(((SysYParser.LvalContext) ((SysYParser.InitVal1Context) initValContext).exp()).lVal().IDENT().getText());
                            if (right!=null) {
                                if(right.getClass().equals(FunctionSymbol.class)){
                                    isWrong = true;
                                    System.err.println("Error type 5 at Line " + lineNo + ": ??????????????????????????????");
                                    return null;
                                }
                                ((BaseSymbol) right).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.LvalContext) ((SysYParser.InitVal1Context) initValContext).exp()).lVal().IDENT().getSymbol().getCharPositionInLine());}});
                                if (((BaseSymbol)right).getType().getClass().equals(BasicTypeSymbol.class)) {
                                    rightDimension = 0;
                                } else if (((BaseSymbol)right).getType().getClass().equals(ArrayType.class)) {
                                    ArrayType arrayType = (ArrayType) ((BaseSymbol)right).getType();
                                    int d = ((SysYParser.LvalContext) ((SysYParser.InitVal1Context) initValContext).exp()).lVal().exp().size();
                                    rightDimension = arrayType.getNum() - d;
                                }
                            }else{
                                return null;
                            }
                        }// ?????????????????????, ???????????????????????????
                        else if(((SysYParser.InitVal1Context) initValContext).exp().getClass().equals(SysYParser.MdmContext.class) || ((SysYParser.InitVal1Context) initValContext).exp().getClass().equals(SysYParser.PmContext.class)){
                            if(ctx.constExp().size()!=0){
                                isWrong=true;
                                System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
                            }
                            return null;
                        }// ??????????????????????????????
                        else if(((SysYParser.InitVal1Context) initValContext).exp().getClass().equals(SysYParser.CallContext.class)){
                            String funcName =  ((SysYParser.CallContext)((SysYParser.InitVal1Context) initValContext).exp()).IDENT().getText();
                            FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(funcName);
                            functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext)((SysYParser.InitVal1Context) initValContext).exp()).IDENT().getSymbol().getCharPositionInLine());}});
                            if(functionSymbol!=null){
                                if(functionSymbol.functionType.getRetTy().getClass().equals(BasicTypeSymbol.class)){
                                    rightDimension = 0;
                                } else if (functionSymbol.functionType.getRetTy().getClass().equals(ArrayType.class)) {
                                    rightDimension = ((ArrayType)functionSymbol.functionType.getRetTy()).getNum();
                                }
                            }
                        }
                        // ?????????????????????
                    }else if (initValContext.getClass().equals(SysYParser.InitVal2Context.class) && ctx.constExp().size() == 1){
                        if(!((SysYParser.InitVal2Context) initValContext).initVal(0).getClass().equals(SysYParser.InitVal1Context.class)){
                            isWrong=true;
                            System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
                        }
                    }
                }
                if(leftDimension != rightDimension){
                    isWrong = true;
                    System.err.println("Error type 5 at Line "+lineNo+": ??????????????????????????????");
                }
            }
            return null;
        }

        /**
         * RETURN??????
         * @param ctx the parse tree
         */
        @Override
        public Object visitStmt8(SysYParser.Stmt8Context ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ???????????????scope, ???????????????
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            Scope temp = currentScope;
            while (!temp.getClass().equals(FunctionSymbol.class)){
                temp = temp.getEnclosingScope();
            }
            FunctionSymbol functionSymbol = (FunctionSymbol) temp;
            Type retType = functionSymbol.functionType.getRetTy();
            // void??????
            if(retType.getClass().equals(BasicTypeSymbol.class) && (((BasicTypeSymbol) retType).getName().equals("void"))){
                if(ctx.exp()!= null && ctx.exp().getClass().equals(SysYParser.CallContext.class)){
                    Symbol symbol = currentScope.resolve(((SysYParser.CallContext) ctx.exp()).IDENT().getText());
                    if(symbol == null){
                        return null;
                    }else{
                        if(symbol.getClass().equals(FunctionSymbol.class)){
                            if(((BasicTypeSymbol)((FunctionSymbol) symbol).functionType.getRetTy()).getName().equals("int")){
                                isWrong = true;
                                System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                            }
                        }else{
                            ((FunctionSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext) ctx.exp()).IDENT().getSymbol().getCharPositionInLine());}});
                        }
                    }
                }else if(ctx.exp()!=null){
                    isWrong = true;
                    System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                }
            }// int??????
            else if(retType.getClass().equals(BasicTypeSymbol.class) && (((BasicTypeSymbol) retType).getName().equals("int"))){
                if(ctx.exp() == null){
                    isWrong = true;
                    System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                }else if(ctx.exp().getClass().equals(SysYParser.LvalContext.class)){
                    SysYParser.LValContext lvalContext = ((SysYParser.LvalContext) ctx.exp()).lVal();
                    String name = lvalContext.IDENT().getText();
                    Symbol symbol = currentScope.resolve(name);
                    if(symbol.getClass().equals(FunctionSymbol.class)){
                        isWrong = true;
                        System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                    }else{
                        ((BaseSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(lvalContext.IDENT().getSymbol().getCharPositionInLine());}});
                        if(((BaseSymbol) symbol).getType().getClass().equals(ArrayType.class)){
                            int dimension = ((ArrayType)((BaseSymbol) symbol).getType()).getNum() - lvalContext.exp().size();
                            if(dimension!=0){
                                isWrong = true;
                                System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                            }
                        }
                    }
                }else if(ctx.exp().getClass().equals(SysYParser.CallContext.class)){
                    Symbol symbol = currentScope.resolve(((SysYParser.CallContext) ctx.exp()).IDENT().getText());
                    if(symbol == null){
                        return null;
                    }else{
                        if(symbol.getClass().equals(FunctionSymbol.class)){
                            if(((BasicTypeSymbol)((FunctionSymbol) symbol).functionType.getRetTy()).getName().equals("void")){
                                isWrong = true;
                                System.err.println("Error type 7 at Line "+lineNo+": ??????????????????????????????????????????????????????");
                            }else{
                                ((FunctionSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext) ctx.exp()).IDENT().getSymbol().getCharPositionInLine());}});
                            }
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public Object visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            visitChildren(ctx);
            // ??????????????????????????????int
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            SysYParser.ExpContext expContext = ctx.exp();
                if(expContext.getClass().equals(SysYParser.LvalContext.class)){
                    SysYParser.LValContext lvalContext = ((SysYParser.LvalContext) expContext).lVal();
                    Symbol symbol = currentScope.resolve(lvalContext.IDENT().getText());
                    if(symbol == null) return null;
                    if (symbol.getClass().equals(FunctionSymbol.class)){
                        isWrong = true;
                        System.err.println("Error type 6 at Line "+lineNo+": ???????????????????????????int?????????array???function???");
                    }else{
                        ((BaseSymbol) symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(lvalContext.IDENT().getSymbol().getCharPositionInLine());}});
                        if(((BaseSymbol) symbol).getType().getClass().equals(ArrayType.class)){
                            int d = ((ArrayType)((BaseSymbol) symbol).getType()).getNum();
                            if( d > ((SysYParser.LvalContext) expContext).lVal().exp().size()) {
                                isWrong = true;
                                System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                            }
                        }
                    }
                }else if(expContext.getClass().equals(SysYParser.CallContext.class)){
                    FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(((SysYParser.CallContext) expContext).IDENT().getText());
                    functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext) expContext).IDENT().getSymbol().getCharPositionInLine());}});
                    if(((BasicTypeSymbol)functionSymbol.functionType.getRetTy()).getName().equals("void")){
                        isWrong = true;
                        System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                    }
                }
            return null;
        }

        @Override
        public Object visitCond(SysYParser.CondContext ctx) {
            if(visitTime == 1){
                visitChildren(ctx);
                return null;
            }
            // ???????????????
            Token t = ctx.getStart();
            int lineNo = t.getLine();
            visitChildren(ctx);
            if(ctx.exp()!=null){
                if(ctx.exp().getClass().equals(SysYParser.LvalContext.class)){
                    Symbol symbol = currentScope.resolve(((SysYParser.LvalContext)ctx.exp()).lVal().IDENT().getText());
                    if(symbol == null) return null;
                    if(symbol.getClass().equals(FunctionSymbol.class)){
                        isWrong = true;
                        System.err.println("Error type 6 at Line "+lineNo+": ???????????????????????????int?????????array???function???");
                    }else {
                        ((BaseSymbol)symbol).usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.LvalContext)ctx.exp()).lVal().IDENT().getSymbol().getCharPositionInLine());}});
                        Type type = ((BaseSymbol) symbol).getType();
                        if (type.getClass().equals(ArrayType.class)) {
                            int d = ((ArrayType) type).getNum();
                            if (d != ((SysYParser.LvalContext) ctx.exp()).lVal().exp().size()) {
                                isWrong = true;
                                System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                            }
                        }
                    }
                }else if(ctx.exp().getClass().equals(SysYParser.CallContext.class)){
                    FunctionSymbol functionSymbol = (FunctionSymbol) currentScope.resolve(((SysYParser.CallContext)ctx.exp()).IDENT().getText());
                    if(functionSymbol!=null){
                        functionSymbol.usedPosition.add(new ArrayList<>(){{add(lineNo);add(((SysYParser.CallContext)ctx.exp()).IDENT().getSymbol().getCharPositionInLine());}});
                        Type type = functionSymbol.functionType.getRetTy();
                        if(((BasicTypeSymbol)type).getName().equals("void")){
                            isWrong = true;
                            System.err.println("Error type 6 at Line " + lineNo + ": ???????????????????????????int?????????array???function???");
                        }
                    }
                }
            }
            return null;
        }

        public Void visitChildren(RuleNode node) {
            if(isWrong && visitTime == 1) return null;
            int no = node.getRuleContext().getRuleIndex();
            String ruleName = ruleNames[no];
            ruleName = String.valueOf(ruleName.charAt(0)).toUpperCase() + ruleName.substring(1);
            if(visitTime == 1) {
                for(int i=0; i<(node.getRuleContext().depth()-1)*2; i++){
                    System.err.print(" ");
                }
                System.err.println(ruleName);
            }
            // ???????????????
            if(node.getChildCount()>0) super.visitChildren(node);
//            for(int i=0; i<node.getChildCount(); i++){
//                ParseTree child = node.getChild(i);
//                MyVisitor visitor = new MyVisitor(this.globalScope, this.currentScope, this.localScopeCounter);
//                visitor.visit(child);
//            }
            return null;
        }

        public Void visitTerminal(TerminalNode node) {
            if(visitTime == 0){
                return null;
            }
            List<String> strings = Arrays.asList(",", "{", "}", "[", "]", ";", "(", ")", "<EOF>");
            if(strings.contains(node.getText()) || isWrong){
                return null;
            }
            RuleNode father = (RuleNode) node.getParent();
            for(int i=0; i<(father.getRuleContext().depth())*2; i++){
                System.err.print(" ");
            }
            if(Objects.equals(symbolicNames[node.getSymbol().getType() - 1], "INTEGR_CONST")){
                String text = node.getText();
                if(text.startsWith("0x") || text.startsWith("0X")){
                    text = String.valueOf(Integer.parseInt(text.substring(2), 16));
                }else if(text.startsWith("0") && text.length()>1){
                    text = String.valueOf(Integer.parseInt(text.substring(1),8));
                }
                System.err.print(text+" "+symbolicNames[node.getSymbol().getType() - 1] );
            }else if(Objects.equals(symbolicNames[node.getSymbol().getType() - 1], "IDENT")){
                String text = node.getText();
                Symbol symbol = currentScope.resolve(node.getText());
//                ArrayList<Integer> position = new ArrayList<>(){{add(node.getSymbol().getLine());add(node.getSymbol().getCharPositionInLine());}};
                ArrayList<ArrayList<Integer>> lists;
                if(symbol.getClass().equals(FunctionSymbol.class)){
                    lists = ((FunctionSymbol) symbol).usedPosition;
                }else{
                    lists = ((BaseSymbol) symbol).usedPosition;
                }
                for(ArrayList<Integer> list : lists){
                    if (list.get(0) == lineNo && list.get(1) == column) {
                        text = substitute;
                        break;
                    }
                }
                System.err.print(text+" "+symbolicNames[node.getSymbol().getType() - 1]);
            }else{
                System.err.print(node.getText()+" "+symbolicNames[node.getSymbol().getType() - 1]);
            }
            String color = "";
            if((0<=node.getSymbol().getType()-1)&&(node.getSymbol().getType()-1)<=8){
                color = "orange";
            }else if((9<=node.getSymbol().getType()-1)&&(node.getSymbol().getType()-1<=23)){
                color = "blue";
            }else if(Objects.equals(symbolicNames[node.getSymbol().getType() - 1], "IDENT")){
                color = "red";
            }else if(Objects.equals(symbolicNames[node.getSymbol().getType() - 1], "INTEGR_CONST")){
                color = "green";
            }
            System.err.println("["+color+"]");
            return null;
        }
    }

//    public static class ErrorListener extends BaseErrorListener {
//        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
//            System.err.println("Error type B at Line "+ line);
//            isWrong = true;
//        }
//    }

}