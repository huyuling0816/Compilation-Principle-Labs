lexer grammar SysYLexer;
// 保留字
CONST : 'const';

INT : 'int';

VOID : 'void';

IF : 'if';

ELSE : 'else';

WHILE : 'while';

BREAK : 'break';

CONTINUE : 'continue';

RETURN : 'return';

//运算符
PLUS : '+';

MINUS : '-';

MUL : '*';

DIV : '/';

MOD : '%';

ASSIGN : '=';

EQ : '==';

NEQ : '!=';

LT : '<';

GT : '>';

LE : '<=';

GE : '>=';

NOT : '!';

AND : '&&';

OR : '||';


L_PAREN : '(';

R_PAREN : ')';

L_BRACE : '{';

R_BRACE : '}';

L_BRACKT : '[';

R_BRACKT : ']';

COMMA : ',';

SEMICOLON : ';';

IDENT : IDETIFIER_NONDIGIT (IDETIFIER_NONDIGIT | DIGIT)* ;

INTEGR_CONST : ('0' | (NONZERO_DIGIT DIGIT*)) | ('0' OCTAL_DIGIT*) | (HEXADECIMAL_PREFIX HEXADECIMAL_DIGIT*) ;

//DECIMAL_CONST : '0' | (NONZERO_DIGIT DIGIT*);
//
//OCTAL_CONST : '0' OCTAL_DIGIT*;
//
//HEXADECIMA_LCONST : HEXADECIMAL_PREFIX HEXADECIMAL_DIGIT*;

// 以下划线或字母开头，仅包含下划线、英文字母大小写、阿拉伯数字
//IDENT : ('_' | LETTER) ('_' | LETTER | DIGIT)*;

// 数字常量，包含十进制数，0开头的八进制数，0x或0X开头的十六进制数
//INTEGR_CONST : ('0' | ([1-9] DIGIT*)) | ('0' [0-7]*) | (('0x' | '0X') (DIGIT | [a-f] | [A-F])*);

WS : [ \r\n\t]+ -> skip;

LINE_COMMENT : '//' .*? '\n' -> skip;

MULTILINE_COMMENT : '/*' .*? '*/' -> skip;

fragment IDETIFIER_NONDIGIT : [a-zA-Z] | '_';

fragment HEXADECIMAL_PREFIX : '0x' | '0X';

fragment NONZERO_DIGIT : [1-9];

fragment OCTAL_DIGIT : [0-7];

fragment HEXADECIMAL_DIGIT : [0-9A-Fa-f];

fragment LETTER : [a-zA-Z] ;

fragment DIGIT : [0-9] ;