import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main
{
    static boolean isWrong = false;
    static String[] symbolicNames;
    static String[] ruleNames;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];

        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        sysYParser.removeErrorListeners();
        ErrorListener errorListener = new ErrorListener();
        sysYParser.addErrorListener(errorListener);
        isWrong = false;
        ruleNames = sysYParser.getRuleNames();
        symbolicNames = sysYLexer.getRuleNames();
        ParseTree tree = sysYParser.program();
        Visitor visitor = new Visitor();
        visitor.visit(tree);

    }

    public static class Visitor extends SysYParserBaseVisitor{

        public Void visitChildren(RuleNode node) {
            if(isWrong) return null;
            int no = node.getRuleContext().getRuleIndex();
            String ruleName = ruleNames[no];
            ruleName = String.valueOf(ruleName.charAt(0)).toUpperCase() + ruleName.substring(1);
            for(int i=0; i<(node.getRuleContext().depth()-1)*2; i++){
                System.err.print(" ");
            }
            System.err.println(ruleName);

            // 遍历子节点
            for(int i=0; i<node.getChildCount(); i++){
                ParseTree child = node.getChild(i);
                Visitor visitor = new Visitor();
                visitor.visit(child);
            }

            return null;
        }

        public Void visitTerminal(TerminalNode node) {
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

    public static class ErrorListener extends BaseErrorListener {
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            System.err.println("Error type B at Line "+ line);
            isWrong = true;
        }
    }

}