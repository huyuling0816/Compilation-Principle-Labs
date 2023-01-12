parser grammar SysYParser;
//  *表示出现0次或以上
//  ?表示出现0次或1次
//  +表示出现1次或以上
options {
    tokenVocab = SysYLexer;
}

program
   : compUnit
   ;

compUnit
   : (funcDef | decl)+ EOF
   ;

decl : constDecl | varDecl;

constDecl : CONST bType constDef (COMMA constDef)* SEMICOLON;

bType : INT;

constDef : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal;

constInitVal : constExp # ConstInitVal1
               | L_BRACE ( constInitVal (COMMA constInitVal)* )? R_BRACE # ConstInitVal2 ;

varDecl : bType varDef (COMMA varDef)* SEMICOLON;

varDef : IDENT  ( L_BRACKT constExp R_BRACKT )*
       | IDENT ( L_BRACKT constExp R_BRACKT )* ASSIGN initVal ;

initVal : exp # InitVal1| L_BRACE (initVal ( COMMA initVal )* )? R_BRACE # InitVal2;

funcDef : funcType IDENT L_PAREN (funcFParams)? R_PAREN block;

funcType : VOID | INT;

funcFParams : funcFParam  (COMMA funcFParam)* ;

funcFParam : bType IDENT (L_BRACKT R_BRACKT ( L_BRACKT exp R_BRACKT )*)?;

block : L_BRACE ( blockItem )* R_BRACE;

blockItem : decl | stmt;

stmt : lVal ASSIGN exp SEMICOLON # StmtAssign | (exp)? SEMICOLON # Stmt2| block # Stmt3
       | IF L_PAREN cond R_PAREN stmt ( ELSE stmt )? # Stmt4
       | WHILE L_PAREN cond R_PAREN stmt # Stmt5
       | BREAK SEMICOLON # Stmt6 | CONTINUE SEMICOLON # Stmt7
       | RETURN (exp)? SEMICOLON # Stmt8;

exp : L_PAREN exp R_PAREN # ParenExp
   | lVal # Lval
   | number # Num
   | IDENT L_PAREN funcRParams? R_PAREN # Call // function call
   | unaryOp exp # UnaryOpExp
   | exp (MUL | DIV | MOD) exp # Mdm
   | exp (PLUS | MINUS) exp # Pm
   ;

cond
   : exp
   | cond (LT | GT | LE | GE) cond
   | cond (EQ | NEQ) cond
   | cond AND cond
   | cond OR cond ;

lVal : IDENT (L_BRACKT exp R_BRACKT)*;

// 用不到
primaryExp : L_PAREN exp R_PAREN | lVal | number;

number : INTEGR_CONST;

// 用不到
unaryExp : primaryExp | IDENT L_PAREN (funcRParams)? R_PAREN
           | unaryOp unaryExp;

unaryOp : PLUS | MINUS | NOT;

funcRParams : param (COMMA param)*;

param : exp;

// 用不到
mulExp : unaryExp | mulExp (MUL | DIV | MOD) unaryExp;

// 用不到
addExp : mulExp | addExp (PLUS | MINUS) mulExp;

// 用不到
relExp : addExp | relExp (LT | GT | LE | GE) addExp;

// 用不到
eqExp : relExp | eqExp (EQ | NEQ) relExp;

// 用不到
lAndExp : eqExp | lAndExp AND eqExp;

// 用不到
lOrExp : lAndExp | lOrExp OR lAndExp;

constExp : exp;