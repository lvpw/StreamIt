/*
 * StreamItParser.g: A grammar for StreamIt
 * $Id: StreamItParser.g,v 1.9 2002-08-12 20:54:42 dmaze Exp $
 */

header {
	package streamit.frontend;
}

options {
	mangleLiteralPrefix = "TK_";
	//language="Cpp";
}

class StreamItParser extends Parser;
options {
	importVocab=StreamItLex;	// use vocab generated by lexer
	buildAST = true;
	k = 4;
}

tokens {
  PRE_INCR; PRE_DECR; POST_INCR; POST_DECR;
}

program	:	(stream_decl)*
		EOF
	;

stream_decl
	:	struct_decl
	|	(stream_type_decl TK_filter) => filter_decl
	|	struct_stream_decl
	;

filter_decl
	: 	stream_type_decl
		TK_filter^
		id:ID
		(param_decl_list)?
		LCURLY
		(stream_inside_decl | work_decl)*
		RCURLY!
	;

stream_type_decl
	:	data_type ARROW^ data_type
	;

struct_stream_decl
	:	stream_type_decl
		(TK_pipeline^ | TK_splitjoin^ | TK_feedbackloop^)
		id:ID
		(param_decl_list)?
		block
	;

inline_stream_decl
	:	inline_filter_decl
	|	inline_struct_stream_decl
	;

inline_filter_decl
	:	TK_filter^
		LCURLY!
		(stream_inside_decl | work_decl)*
		RCURLY!
	;

inline_struct_stream_decl
	:	(TK_pipeline^ | TK_splitjoin^ | TK_feedbackloop^)
		LCURLY!
		(stream_inside_decl)*
		RCURLY!
	;

stream_inside_decl
	:	init_decl
	|	(function_decl) => function_decl
	|	variable_decl SEMI!
	;

work_decl
	:	TK_work^ (rate_decl)* block
	;

rate_decl
	:	TK_push^ right_expr
	|	TK_pop^ right_expr
	|	TK_peek^ right_expr
	;

init_decl
	:	TK_init^  block
	;

push_statement
	:	TK_push^ LPAREN! right_expr RPAREN!
	;

statement
	:	streamit_statement
	;

streamit_statement
	:	minic_statement
	|	add_statement
	|	body_statement
	| 	loop_statement
	|	split_statement SEMI!
	|	join_statement SEMI!
	|	enqueue_statement SEMI!
	|	push_statement SEMI!
	|	print_statement SEMI!
	;

add_statement
	: TK_add^ stream_or_inline
	;

body_statement
	: TK_body^ stream_or_inline
	;

loop_statement
	: TK_loop^ stream_or_inline
	;

stream_or_inline
	: (stream_type_decl TK_filter) => stream_type_decl TK_filter^ block
	| (TK_pipeline) => TK_pipeline^ block
	| (TK_splitjoin) => TK_splitjoin^ block
	| (TK_feedbackloop) => TK_feedbackloop^ block
	| ID (LESS_THAN data_type MORE_THAN!)? func_call_params SEMI!
	;

split_statement
	: TK_split^ splitter_or_joiner
	;

join_statement
	: TK_join^ splitter_or_joiner
	;

splitter_or_joiner
	: TK_roundrobin func_call_params // Not quite right, but...
	| TK_duplicate
	;

enqueue_statement
	: TK_enqueue^ right_expr
	;

print_statement
	:	TK_print^
		right_expr
	;

data_type
	:	(primitive_type LSQUARE) =>
			primitive_type LSQUARE^ right_expr RSQUARE!
				(LSQUARE! right_expr RSQUARE!)*
	|	primitive_type
	|	TK_void
	;

primitive_type
	:	TK_int
	|	TK_float
	|	TK_double
	|	TK_complex
	|	id:ID
	;

global_declaration
	:	(function_decl) => function_decl
	|	variable_decl SEMI!
	|	struct_decl SEMI!
	;

variable_decl
	:	data_type id:ID^ (variable_init)?
	;

array_modifiers
	:	LSQUARE^ right_expr RSQUARE!  
		(LSQUARE! right_expr RSQUARE!)*
	;

variable_init
	:	ASSIGN^ right_expr
	;

variable_list
	:	LPAREN^ (variable_decl (COMMA! variable_decl)* )? RPAREN!
	;

function_decl
	:	data_type id:ID^
		variable_list
		block
	;

param_decl_list
	:	LPAREN^ (param_decl (COMMA! param_decl)* )? RPAREN!
	;

param_decl
	:	data_type id:ID^
	;

block
	:	LCURLY^ ( statement )* RCURLY!
	;

minic_statement
	:	block
	|	(variable_decl) => variable_decl SEMI!
	|	(expr_statement) => expr_statement SEMI!
	|	if_else_statement
	|	TK_while^ LPAREN! right_expr RPAREN! statement
	|	TK_for^ LPAREN! for_init_statement SEMI! right_expr SEMI!
		for_incr_statement RPAREN! statement
	;

for_init_statement
	:	(variable_decl) => variable_decl
	|	(expr_statement) => expr_statement
	;

for_incr_statement
	:	expr_statement
	;

if_else_statement
	:	TK_if^ LPAREN! right_expr RPAREN! statement
		((TK_else) => (TK_else! statement))?
	;

expr_statement
	:	(incOrDec) => incOrDec
	|	(assign_expr) => assign_expr
	|	streamit_value_expr
	;

assign_expr
	:	left_expr ((ASSIGN^ | PLUS_EQUALS^ | MINUS_EQUALS^) right_expr)?
	;

func_call_params
	: LPAREN^ (right_expr (COMMA! right_expr)* )? RPAREN!
	;

left_expr
	:	value
	;

right_expr
	:	ternaryExpr
	;

ternaryExpr
	:	logicOrExpr (QUESTION^ ternaryExpr COLON! ternaryExpr)?
	;

logicOrExpr
	:	logicAndExpr ( (LOGIC_OR^ | LOGIC_XOR^) logicOrExpr)?
	;

logicAndExpr
	:	equalExpr ( LOGIC_AND^ logicAndExpr)?
	;

equalExpr
	:	compareExpr ( (EQUAL^ | NOT_EQUAL^) equalExpr)?
	;

compareExpr
	:	addExpr
		((LESS_THAN^ | LESS_EQUAL^ | MORE_THAN^ | MORE_EQUAL^) compareExpr)?
	;

addExpr
	:	multExpr ( (PLUS^ | MINUS^) addExpr)?
	;

multExpr
	:	inc_dec_expr ( (STAR^ | DIV^ | MOD^) multExpr)?
	;

inc_dec_expr
	:	(incOrDec) => incOrDec
	|	value_expr
	;

incOrDec!
	:	exp1:left_expr
		(	INCREMENT { #incOrDec = #([POST_INCR], exp1); }
		|	DECREMENT { #incOrDec = #([POST_DECR], exp1); }
		)
	|	INCREMENT exp2:left_expr { #incOrDec = #([PRE_INCR], exp2); }
	|	DECREMENT exp3:left_expr { #incOrDec = #([PRE_DECR], exp3); }
	;

value_expr
	:	minic_value_expr
	|	streamit_value_expr
	;

streamit_value_expr
	:	TK_pop^ LPAREN! RPAREN!
	|	TK_peek^ LPAREN! right_expr RPAREN!
	;

minic_value_expr
	:	LPAREN! right_expr RPAREN!
	|	value
	|	constantExpr
	;

value
!:
  field1:field_ref { #value = #field1; }
  (
    func_params1:func_call_params
    { #value = #([LPAREN, "$call"], #value, #func_params1); }
  )?
  (
    array_mod1:array_modifiers
    { #value = #([LSQUARE, "$array"], #value, #array_mod1); }
  )?
  (
    (
      DOT field2:field_ref
      { #value = #(DOT, #value, #field2); }
      
    )
    (
      func_params2:func_call_params
      { #value = #([LPAREN, "$call"], #value, #func_params2); }
    )?
    (
      array_mod2:array_modifiers
      { #value = #([LSQUARE, "$array"], #value, #array_mod2); }
    )?
  )*
;

field_ref
	:	varName:ID 
	;

constantExpr
	:	NUMBER
	|	CHAR_LITERAL
	|	STRING_LITERAL
	|!	TK_pi { #constantExpr = #([NUMBER, Double.toString(Math.PI)]); }
	;

struct_decl
	:	TK_struct^ id:ID
		LCURLY!
		(variable_decl SEMI!)*
		RCURLY!
	;

switch_stmt
	:	TK_switch^
		LPAREN! right_expr RPAREN!
		LCURLY!	(case_stmt)* RCURLY!
	;

case_stmt
	:	(TK_case^ right_expr | TK_default^) COLON! (statement)*
	;

