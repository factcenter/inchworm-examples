package org.factcenter.grep;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

public class SimpleDFA {
	int numStates;
	int charBits;

	/**
	 * For state i, and input character c, transitions[i][c] holds the index of
	 * the next state. The special state {@link #numStates} indicates an invalid
	 * transition (match failure)
	 */
	public int transitions[][];

	/**
	 * Is state i an accepting state
	 */
	public boolean accepting[];

	int initialState;

	public SimpleDFA(int numStates, int charBits) {
		this.numStates = numStates;
		this.charBits = charBits;

		// We don't allow more than 8-bit chars.
		if (charBits > 8)
			charBits = 8;

		transitions = new int[numStates + 1][];
		accepting = new boolean[numStates + 1];
		accepting[numStates] = false; // Dummy state to signal failure.

		for (int i = 0; i < transitions.length; ++i) {
			transitions[i] = new int[1 << charBits];
			for (int j = 0; j < transitions[i].length; ++j)
				transitions[i][j] = numStates; // initialize all transitions to
										// terminal.
		}
	}

	
	public int[] getStateSequence(String input, int charOffset) {
		int[] states = new int[input.length()];
		
		int state = initialState;
		
		for (int i = 0; i < input.length(); ++i) {
			states[i] = state;
			char ch = input.charAt(i);
			ch &= (1 << charBits) - 1;

			int nextState = transitions[state][ch];
			if (nextState >= 0 && nextState <= numStates)
				state = nextState;
			else {
				throw new IllegalArgumentException("Bad State: " + nextState);
			}
		}
		return states;
	}
	
	/**
	 * Run the DFA and return the final state.
	 * 
	 * @param input
	 *            the input string
	 * @param charOffset
	 *            the character offset (should be same as used in
	 *            {@link #createFromRegexp(String, int, int)}).
	 * @return the final state (-1) if the match failed due to invalid transition.
	 */
	public int run(String input, int charOffset) {
		int state = initialState;

		for (int i = 0; i < input.length(); ++i) {
			char ch = input.charAt(i);
			ch &= (1 << charBits) - 1;

			int nextState = transitions[state][ch];
			if (nextState >= 0 && nextState < numStates)
				state = nextState;
			else {
				// Match failed.
				return numStates;
			}
		}

		return state;
	}

	/**
	 * Convenience method to call {@link #run(String, int)} with charOffset 0.
	 * 
	 * @param input
	 * @return
	 */
	public int run(String input) {
		return run(input, 0);
	}

	/**
	 * Create a SimpleDFA from a regular expression.
	 * Running the resulting DFA on an input is equivalent to the {@link Matcher#matches()} method
	 * (checks if the regexp matches the input exactly).
	 * To run the equivalent of {@link Matcher#find()}, use ".*"+regexp+".*". 
	 * 
	 * @param regexp
	 *            regular expression (in {@link RegExp} format)
	 * @param charBits
	 *            number of bits per character (up to 8)
	 * @param charOffset
	 *            start of allowed char range (e.g., if offset is 'a' then the
	 *            character 'a' will be translated to 0).
	 * 
	 * @throws IllegalArgumentException
	 */
	public static SimpleDFA createFromRegexp(String regexp, int charBits,
			int charOffset) throws IllegalArgumentException {
		RegExp parsedRegex = new RegExp(regexp);
		Automaton dfa = parsedRegex.toAutomaton(true);

		int numStates = dfa.getNumberOfStates();
		Map<State, Integer> stateMap = new HashMap<State, Integer>(numStates);
		Set<State> states = dfa.getStates();

		// Map states to integers
		int i = 0;
		for (State state : states) {
			stateMap.put(state, i++);
		}

		SimpleDFA sdfa = new SimpleDFA(numStates, charBits);
		sdfa.initialState = stateMap.get(dfa.getInitialState());

		int charMask = (1 << charBits) - 1;

		for (State state : states) {
			int stateIdx = stateMap.get(state);

			sdfa.accepting[stateIdx] = state.isAccept();

			Set<Transition> transet = state.getTransitions();

			for (Transition trans : transet) {
				int min = trans.getMin() - charOffset;
				int max = trans.getMax() - charOffset;
				if ((min >= (1 << charBits)) || (max < 0))
					continue;

				min = Math.max(0, min);
				max = Math.min(charMask, max);

				int next = stateMap.get(trans.getDest());

				for (int j = min; j <= max; ++j) {
					sdfa.transitions[stateIdx][j] = next;
				}
			}
		}

		return sdfa;
	}

	public static SimpleDFA createFromRegexp(String regexp)
			throws IllegalArgumentException {
		return createFromRegexp(regexp, 8, 0);
	}

    private String formatChar(int ch) {
        String transLabel = (ch > 0x20 && ch < 0x7f) ? " " + (char) ch
                : String.format("0x%02x", ch);
        if (ch == '"')
            transLabel = "\\\"";

        return transLabel;
    }

    private void updateLabel(Map<Integer, String> transitionMap, int dstState, int rangeStart, int rangeEnd) {
        if (dstState == numStates)
            // We don't show edges to the "fail" state.
            return;

        String addLabel = formatChar(rangeStart);
        if (rangeEnd > rangeStart)
            addLabel += ((rangeEnd - rangeStart > 1) ? "-" : ",") + formatChar(rangeEnd);

        String curLabel = transitionMap.get(dstState);
        if (curLabel == null)
            curLabel = addLabel;
        else
            curLabel += "," + addLabel;

        transitionMap.put(dstState, curLabel);
    }

    private void computeTransitionLabels(Map<Integer, String> transitionMap, int i) {
        int rangeStart = 0;
        int curDstState = transitions[i][0];
        for (int j = 1; j < transitions[i].length; ++j) {
            if (transitions[i][j] != curDstState) {
                updateLabel(transitionMap, curDstState, rangeStart, j - 1);
                rangeStart = j;
                curDstState = transitions[i][j];
            }
        }
        updateLabel(transitionMap, curDstState, rangeStart, transitions[i].length - 1);
    }

	/**
	 * Output a graphviz-format graph to visualize the DFA.
	 * 
	 * @param name
	 * @param out
	 */
	public void visualizeDFA(String name, PrintStream out) {
		out.println("digraph \"" + name.replaceAll("\"", "\\\"") + "\" {");

		Map<Integer, String> transitionMap = new HashMap<>();

		for (int i = 0; i < numStates; ++i) {
			String initial = (i == initialState) ? "\\n[Start]" : "";
			String accepts = accepting[i] ? ",peripheries=2" : "";

			String node = String.format("  n%d [label=\"%d%s\"%s];", i, i,
					initial, accepts);
			out.println(node);

			transitionMap.clear();
            computeTransitionLabels(transitionMap, i);

			for (int state : transitionMap.keySet()) {
				String edge = String.format("    n%d -> n%d [label=\"%s\"];",
						i, state, transitionMap.get(state));
				out.println(edge);
			}
		}
		out.println("}");
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("SimpleDFA regexp [outfile]");
			System.exit(0);
		}

		SimpleDFA dfa = createFromRegexp(args[0]);
		
		PrintStream out = System.out;
		if (args.length > 1) {
			try {
				out = new PrintStream(new FileOutputStream(args[1]));
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		dfa.visualizeDFA(args[0], out);
	}

}
