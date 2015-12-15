//
// Run a DFA. We use only 7 LSB bits of the inputs, and limit to at most 63 states.
//

.header
wordsize: 16	// We actually only need 8-bit regs, but we'll pack 2 in each word.
regptrsize: 5	// Don't need many regs
romptrsize: 1	// Tiny program (actually only 1 instruction)
ramptrsize: 13	// 4096 words (we use 64 words for each state, so this lets us have 64 states)
instruction: in rol rol and xor load mux rol and

.const
    MAX_STATES = 64
    WORDSIZE = 16
    STATE = state

.data
	czero:  %r[0] = 0 // constant 0
	cone:   1
	csix:   6
	ceight:	8

	rightshift1:
		(WORDSIZE - 1)

	statemask:
		(MAX_STATES - 1)

	tmp1:
		0
	tmp2:
		0

	state:
		%r[freeregs - 1] db 0 // Start with state 0. (only the  6 LSBs matter)

		%r[ctrl] = 1

// The transition table is stored packed in RAM;
// to find the next state when starting at state i and receiving input x:

// read the word (two bytes) at pos 64*i+x/2; the next state is the MSB if x%2=1 and the LSB if x%2=0

.code
	in							// Input from user to %in

	// Compute tmp1 = 64*state+[in]/2
	rol %tmp1 < %state, %csix	// tmp1 = ROL(state, 6) == state << 6 (we only use the 6 LSBs, so the 6 MSBs are always 0)


	rol %tmp2 < %in, %rightshift1 // tmp2 = ROR(in, 1)
	and %tmp2 < %tmp2, %statemask // tmp2 &= statemask

	xor %tmp1 < %tmp1, %tmp2	  // tmp1 += tmp2; (tmp1 == now)

	load %tmp1 < %tmp1			// tmp1 = RAM[tmp1]

	mux %tmp2 < %in, %czero, %ceight // tmp2 = x & 1 == 0 ? 0 : 8
	rol %tmp1 < %tmp1, %tmp2		// tmp1 = ROL(tmp1, tmp2) [this swaps bytes if in%2 == 1

	and %state < %tmp1, %statemask // state = tmp1 & 0x3f

	// We actually don't need the next command here -- there's only one instruction!
---
