import org.antlr.v4.runtime.*;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Main
{
    static boolean isWrong = false;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        sysYLexer.removeErrorListeners();
        ErrorListener myErrorListener = new ErrorListener();
        sysYLexer.addErrorListener(myErrorListener);
        isWrong = false;
        List<? extends Token> tokens = sysYLexer.getAllTokens();
        String[] ruleNames = sysYLexer.getRuleNames();
        if(!isWrong) {
            for (Token token : tokens) {
                String text = token.getText();
                String line = String.valueOf(token.getLine());
                String type = ruleNames[token.getType() - 1];
                if (Objects.equals(type, "INTEGR_CONST")){
                    if(text.startsWith("0x") || text.startsWith("0X")){
                        text = String.valueOf(Integer.parseInt(text.substring(2), 16));
                    }else if(text.startsWith("0") && text.length()>1){
                        text = String.valueOf(Integer.parseInt(text.substring(1),8));
                    }
                }
                System.err.println(type + " " + text + " " + "at Line " + line + ".");
            }
        }
    }

    public static class ErrorListener extends BaseErrorListener {
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            int begin = msg.indexOf("'");
            int end = msg.lastIndexOf("'");
            System.err.println("Error type A at Line "+ line +":"+msg.substring(begin+1, end));
            isWrong = true;
        }
    }

}