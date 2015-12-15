//
// Compute the DFA output (whether it's in an accepting
// State)
// We will rewrite RAM to contain a table of accepting
// states (word i in RAM will be 1 if the state is
// accepting and 0 otherwise).
//

// Note: most of the header parameters and symbols
// are taken from the runDFA assembly script.
.header
wordsize: 16	// We actually only need 8-bit regs, but we'll pack 2 in each word.
regptrsize: 5	// Don't need many regs
romptrsize: 1	// Tiny program (actually only 1 instruction)
ramptrsize: 13	// 4096 words (we use 64 words for each state, so this lets us have 64 states)
instruction: load xor out
//instruction: xor xor out

.code
	load %out2, %state			// %out2 = RAM[%state]
	//xor %out2 < %state, %czero

	xor %ctrl < %cone, %czero	// Set %ctrl=1
	out
---
