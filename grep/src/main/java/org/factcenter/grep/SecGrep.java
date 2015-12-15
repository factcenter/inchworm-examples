package org.factcenter.grep;

import org.factcenter.inchworm.MemoryArea;
import org.factcenter.inchworm.app.Run;
import org.antlr.runtime.RecognitionException;
import org.factcenter.qilin.protocols.concrete.DefaultOTExtender;
import org.factcenter.qilin.util.BitMatrix;

import java.io.*;

public class SecGrep extends Run {
	final static String ASM_DFA_RUNNER = "/runDFA.asm";
    final static String ASM_DFA_RUNNER_DEBUG = "/runDFA-debug.asm";
    final static String ASM_COMPUTE_OUTPUT = "/computeOutput.asm";
	

	SimpleDFA dfa;
	BitMatrix dfaRAM;
	BitMatrix acceptStateRAM;
	
	String dataBuf;

    File graphOut;
	
	long inputLen;
	
	public SecGrep(String progName, PrintStream errOut) {
		super(progName, errOut);

	}
	
	
	public void setupDFA(String regex) {
		// ASCII REGEX
		dfa = SimpleDFA.createFromRegexp(regex, 7, 0);

		int charNum = (1 << dfa.charBits);
		int statePtrSize = 8;
		dfaRAM = new BitMatrix((dfa.numStates+1) * charNum * statePtrSize);
		acceptStateRAM = new BitMatrix(dfaRAM.getNumCols());

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
			// Setup the accepting state encoding
			acceptStateRAM.setWord(i, 16, dfa.accepting[i] ? 1 : 0);

		}


		dataBuf = String.format(".data\n\n %%r[state] = %d\n", dfa.initialState);

		// visualization
        if (graphOut != null) {
            try {
                PrintStream out = new PrintStream(new FileOutputStream(graphOut));
                dfa.visualizeDFA("DFA /" + regex + "/", out);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

 
		progDataStream = new ByteArrayInputStream(dataBuf.getBytes());
		
	}

    @Override
    public void createOptions() {
        super.createOptions();

        parser.accepts("regex", "Regular expression to search for (party 0 only)")
                .withRequiredArg();

        parser.accepts("graph", "File to write graphviz-format DFA visualization (party 0 only)")
                .withRequiredArg();

        parser.accepts("debug", "Use debugging version of MPC with intermediate output (party 0 only)");

    }

    @Override
    public void parse(String[] args) {
        super.parse(args);

        party = options.has("regex") ? 0 : 1;

        if (options.has("graph"))
            graphOut = new File((String) options.valueOf("graph"));
    }

    public void parseAndSetup(String[] args) throws IOException {
		createOptions();
		addFileOptions();

		parse(args);
		parseFileOptions();

		createIOHandler();
		
		// Open code streams:
		if (party == 0) {
            if (options.has("debug")) {
                progCodeStream = SecGrep.class.getResourceAsStream(ASM_DFA_RUNNER_DEBUG);
                logger.debug("Using debugging version of DFA runner");
            } else {
                progCodeStream = SecGrep.class.getResourceAsStream(ASM_DFA_RUNNER);
            }
			if (!options.has("regex"))
				printHelpAndExit("Party 0 [connecting] must provide regex!", -1);
			
			setupDFA((String)options.valueOf("regex"));
			logger.debug("DFA initial state: {}", dfa.initialState);	
		} else {
			if (!options.has("in")) 
				printHelpAndExit("Party 1 must provide input file!", -1);
		}
	}


    public void setupGrep() throws IOException {
        setupInchworm();

        if (party == 1) {
            // Write input file length.
            inputLen = new File(inFile).length();
            logger.info("Input file ({}) has {} bytes", inFile, inputLen);

            toPeer.writeLong(inputLen);
            toPeer.flush();
        } else {
            inputLen = toPeer.readLong();
            logger.info("Input file has {} bytes", inputLen);

            // Write state table to RAM.
            state.getMemory(MemoryArea.Type.TYPE_RAM).store(0, dfaRAM);
        }
    }
	
	public void run() throws IOException {
		run((int) inputLen);
		
		// Run the output retrieval script.
		if (party == 0) {
			try {
				player.loadNewPlayerCode(getClass().getResourceAsStream(ASM_COMPUTE_OUTPUT), true, false);
			} catch (RecognitionException e) {
				e.printStackTrace(errOut);
			}
			state.getMemory(MemoryArea.Type.TYPE_RAM).store(0, acceptStateRAM);
		} else {
			player.otherPlayerLoadsCode();
		}

		run(1);
	}
	
	public static void main(String[] args) {
		SecGrep grep = new SecGrep(SecGrep.class.getCanonicalName(), System.err);

		try {
            grep.parseAndSetup(args);

            grep.setupTCPServer();

            do {
                if (grep.runAsServer) {
                    grep.logger.info("Waiting for player {} to connect", 1 - grep.party);
                }
                grep.setupGrep();

                grep.run();

                // We stop the current server and start another one.
                ((DefaultOTExtender)grep.otExtender).stopServer();
            } while (grep.runAsServer);
		} catch (IOException e) {
			System.err.println("I/O Error: " + e.getMessage());
			System.exit(1);
		}
	}
}
