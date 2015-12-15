package org.factcenter.grep;

import org.factcenter.inchworm.VMState;
import org.factcenter.inchworm.app.InchwormIO;
import org.factcenter.inchworm.app.InputStreamIOHandler;
import org.factcenter.qilin.util.BitMatrix;
import test.TestCommon;

import java.io.*;

public class TestRunDFA extends TestCommon {
	
	final static String TEST_REGEX = "[ab]{1,3}a(([a-f]+|8)9)+c";
	
	final static String TEST_INPUT = "babcd88fdfdsafdsahjkl43178943dsdjksldjskL;DJSAKJsl;k89c";
	
	SimpleDFA dfa;
	BitMatrix dfaRAM;
	
	InchwormIO io;
	
	String dataBuf;
	String resultBuf;

	@Override
	protected int getMaxSteps() { return TEST_INPUT.length(); }

    // We're testing the assembly code, so dummy ops should be good enough.
	@Override
	protected boolean useSecureOps() { return false; }
	
	@Override
	protected BitMatrix getRAMContents() { return dfaRAM; }


    // We don't support PathORAM here because we use direct load/store.
    @Override
    protected boolean usePathORAM() { return false; }

	protected InchwormIO getLeftIO() throws IOException {
		outStream = new FileOutputStream(outFile);
		InputStream in = new ByteArrayInputStream(TEST_INPUT.getBytes());
		return io = new InputStreamIOHandler(in, outStream, "(%d) %d\n");
	}
	
	public TestRunDFA() throws IOException {

		// ASCII REGEX
		dfa = SimpleDFA.createFromRegexp(TEST_REGEX, 7, 0);
		
		int charNum = (1 << dfa.charBits);
		int statePtrSize = 8;
		dfaRAM = new BitMatrix((dfa.numStates+1) * charNum * statePtrSize);

		// The transition table is stored packed in RAM;
		// to find the next state when starting at state i and receiving input x:

		// read the word (two bytes) at pos 64*i+x/2; the next state is the MSB if x%2=1 and the LSB if x%2=0
		for (int i = 0; i <= dfa.numStates; ++i) {
			for (int x = 0; x < charNum; x += 2) {
				int n0 = dfa.transitions[i][x];
				int n1 = dfa.transitions[i][x + 1];
				int word = (n1 << 8) | n0;
				
				dfaRAM.setWord((i<<6) | (x >> 1), 16, word);
			}
		}
		
		dataBuf = String.format(".data\n\n %%r[state] = %d\n", dfa.initialState);
		int[] states = dfa.getStateSequence(TEST_INPUT, 0);
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < states.length; ++i) {
			buf.append(String.format("(%d) %d\n", i, states[i]));
		}
		resultBuf = buf.toString();
		
		PrintStream out = new PrintStream(new FileOutputStream("/tmp/out1.gz"));
		dfa.visualizeDFA("DFA", out);
		out.close();
		
		
		logger.debug("DFA initial state: {}", dfa.initialState);
        logger.debug("Expected Results BUF:\n{}", resultBuf);
	}
	
	
	@Override
	protected void justAfterRunning() throws Exception {
		dump();
	}
	
	void dump() throws IOException {
		VMState state = tpc.getLeftPlayer().getState();
		
		for (int i = 0; i < state.getNumRegs(); ++i) {
			System.out.println(String.format("r[%02d]=%04x", i, state.getReg(i)));
		}
	}
	
	@Override
	protected CodeDescription getCodeDescription() {
		InputStream dataInputStream = new ByteArrayInputStream(dataBuf.getBytes());
		InputStream expectedResult = new ByteArrayInputStream(resultBuf.getBytes());
		
		return new CodeDescription(
				getClass().getResourceAsStream("/runDFA-debug.asm"),				// left-code
				null, 						// right-code
				dataInputStream, 						// left data
				null,						  // right data
				expectedResult 		// result reference 
				);
	}

}
