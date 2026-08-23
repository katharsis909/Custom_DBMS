package SEMANTIC;

class GRAMMAR_PRODUCTIONS {}
/*
<program>          ::= <statement_list>

<statement_list>   ::= <statement> (";" <statement>)* ";"
// Each statement is followed by a semicolon

<statement>        ::= <create_stmt>
                     | <create_index_stmt>
                     | <select_stmt>
                     | <drop_stmt>

<create_stmt>      ::= "CREATE" "TABLE" <table_name> "(" <table_element_list> ")"

<table_element_list> ::= <table_element> ("," <table_element>)*

<table_element>    ::= <column_def>
                     | <primary_key_constraint>
                     | <foreign_key_constraint>

<column_def>       ::= <column_name> <data_type> ["PRIMARY" "KEY"] [<inline_foreign_key>]

<primary_key_constraint> ::= "PRIMARY" "KEY" "(" <column_name> ("," <column_name>)* ")"

<inline_foreign_key> ::= "REFERENCES" <table_name> "(" <column_name> ")"

<foreign_key_constraint> ::= "FOREIGN" "KEY" "(" <column_name> ")" "REFERENCES" <table_name> "(" <column_name> ")"

<create_index_stmt> ::= "CREATE" "INDEX" <index_name> "ON" <table_name> "(" <column_name> ("," <column_name>)* ")"

<data_type>        ::= "INT" | "STRING"

<drop_stmt>        ::= "DROP" "TABLE" <table_name>

<select_stmt>      ::= "SELECT" <select_list> "FROM" <table_ref> <join_clause>* [<where_clause>] [<group_by_clause>] [<having_clause>] [<order_by_clause>]

<table_ref>        ::= <table_name> ["AS" <alias_name> | <alias_name>]

<join_clause>      ::= "JOIN" <table_ref> "ON" <column_ref> "=" <column_ref>

<select_list>      ::= "*"
                     | <select_item> ("," <select_item>)*

<select_item>      ::= <column_ref>
                     | <aggregate_function> ["AS" <alias_name>]

<aggregate_function> ::= ("COUNT" | "SUM" | "AVG" | "MIN" | "MAX") "(" (<column_ref> | "*") ")"

<group_by_clause> ::= "GROUP" "BY" <column_ref> ("," <column_ref>)*

<having_clause>   ::= "HAVING" <having_condition> ("AND" <having_condition>)*

<having_condition> ::= <aggregate_function> <comparison_operator> <value>

<order_by_clause> ::= "ORDER" "BY" <order_by_item> ("," <order_by_item>)*

<order_by_item>   ::= <column_ref> ["ASC" | "DESC"]

<where_clause> ::= "WHERE" <condition_list>

<condition_list> ::= <unary_condition> ("AND" <unary_condition>)*
// AND is now part of the condition structure

<unary_condition>  ::= <column_ref> <comparison_operator> <value>

<comparison_operator> ::= "=" | "!=" | "<" | "<=" | ">" | ">="

<value>            ::= <string_literal> | <numeric_literal>

<table_name>       ::= <identifier>
<column_name>      ::= <identifier>
<column_ref>       ::= <column_name> | <table_name> "." <column_name>
<index_name>       ::= <identifier>
<alias_name>       ::= <identifier>

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
