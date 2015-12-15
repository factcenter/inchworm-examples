# Inchworm Implementation Example (GREP)

The program implements a secure regular expression search:

* one party has as input a private regular expression 
* the other party has as input a private file. 

The output is 1 if the regex matches the file and 0 otherwise. The size of the file is public, and a bound on the size of the regex DFA is also revealed.
The regular expression is compiled to a DFA using the [BRICS automaton pacakge](http://www.brics.dk/automaton/) 
