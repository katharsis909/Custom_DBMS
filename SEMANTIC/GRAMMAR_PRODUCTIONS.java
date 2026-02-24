package SEMANTIC;

class GRAMMAR_PRODUCTIONS {}
/*
<program>          ::= <statement_list>

<statement_list>   ::= <statement> (";" <statement>)* ";"
// Each statement is followed by a semicolon

<statement>        ::= <create_stmt>
                     | <select_stmt>
                     | <drop_stmt>

<create_stmt>      ::= "CREATE" "TABLE" <table_name> "(" <column_def_list> ")"

<column_def_list>  ::= <column_def> ("," <column_def>)*

<column_def>       ::= <column_name> <data_type>

<data_type>        ::= "INT" | "STRING"

<drop_stmt>        ::= "DROP" "TABLE" <table_name>

<select_stmt>      ::= "SELECT" <select_list> "FROM" <table_name> [<where_clause>]

<select_list>      ::= "*"
                     | <column_name> ("," <column_name>)*

<where_clause> ::= "WHERE" <condition_list>

<condition_list> ::= <unary_condition> ("AND" <unary_condition>)*
// AND is now part of the condition structure

<unary_condition>  ::= <column_name> "=" <value>

<value>            ::= <string_literal> | <numeric_literal>

<table_name>       ::= <identifier>
<column_name>      ::= <identifier>

<identifier>       ::= [a-zA-Z_][a-zA-Z0-9_]*

<string_literal>   ::= "'" <any_char_except_quote>* "'"
<numeric_literal>  ::= [0-9]+


<insert_stmt> ::= "INSERT" "INTO" <table_name> "(" <value_list> ")"
<value_list>  ::= <value> ("," <value>)*
<value>       ::= <string_literal> | <numeric_literal>

*/

// Parser design: LL(1), left-to-right, no full backtracking required.
// Optional productions (like WHERE) are handled by checking token presence.
// Non-terminals like condition are not AST nodes themselves.
