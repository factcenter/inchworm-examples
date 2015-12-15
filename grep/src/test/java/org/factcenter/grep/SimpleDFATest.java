package org.factcenter.grep;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class SimpleDFATest {
	
	Random rnd;
	
	@SuppressWarnings("deprecation")
	String generateRandomString(int len, int charBits) {
		int actualLen = rnd.nextInt(len);
		byte[] bytes = new byte[actualLen];
		rnd.nextBytes(bytes);
		
		for (int i = 0; i < actualLen; ++i) {
			bytes[i] &= (1 << charBits) - 1;
		}
		
		return new String(bytes, 0);
	}
	
	@Before
	public void setup() {
		rnd = new Random(103); 
	}

	@Test
	public void testRunBadTransitions() {
		SimpleDFA dfa = new SimpleDFA(100, 8);
		
		dfa.initialState = 7;
		
		int state = dfa.run("testing");
		assertEquals(dfa.numStates, state);
	}
	
	@Test
	public void testRunRandomPath() {
		SimpleDFA dfa = new SimpleDFA(103, 8);
		
		String input = generateRandomString(dfa.numStates - 1, 8);
		
		dfa.initialState = rnd.nextInt(dfa.numStates);
		
		int pathLen = input.length();
		
		int state = dfa.initialState;
		for (int i = 0; i < pathLen; ++i) {
			// Only change if we haven't changed it before.
			if (dfa.transitions[state][input.charAt(i)] == dfa.numStates) {
				dfa.transitions[state][input.charAt(i)] = rnd.nextInt(dfa.numStates);
				state = dfa.transitions[state][input.charAt(i)];
			}
		}
		
		int actualState = dfa.run(input);
		assertEquals(state, actualState);
	}
	
	@Test
	public void testRunDeterministicCycle() {
		SimpleDFA dfa = new SimpleDFA(103, 8);
		
		dfa.initialState = 7;
		
		for (int i = 0; i < dfa.numStates; ++i) {
			for (int j = 0; j < dfa.transitions[i].length; ++j) {
				dfa.transitions[i][j] = (i + 1) % dfa.numStates;
			}
		}

		String testInput = "testinthisisatfgdsgfdsgfdsgfdshjkl78493508493jkg";
		
		int state = dfa.run(testInput);
		assertEquals(dfa.initialState + testInput.length(), state);
	}
	
	
	@Test
	public void testCreateFromRegexpTrivial() {
		SimpleDFA dfa = SimpleDFA.createFromRegexp("abcde");
		

		int state = dfa.run("abcde");
		assertTrue(dfa.accepting[state]);
		
		state = dfa.run("abcdef");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("abcdf");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("xxxabcdf");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("xxxabcdef");
		assertFalse(dfa.accepting[state]);
	}
	
	@Test
	public void testCreateFromRegexpReasonable() {
		SimpleDFA dfa = SimpleDFA.createFromRegexp("abcde|.*xyz.*|(0x[0-9a-f]+).*");
		
		int state = dfa.run("abcdef");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("abcdf");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("xxxabcde");
		assertFalse(dfa.accepting[state]);

		state = dfa.run("xxyzabcdf");
		assertTrue(dfa.accepting[state]);
		
		state = dfa.run("abcdex");
		assertFalse("Reached state " + state, dfa.accepting[state]);
		
		state = dfa.run("xxx0xqqq");
		assertFalse(dfa.accepting[state]);
		
		state = dfa.run("0x7qqq");
		assertTrue(dfa.accepting[state]);
		
	}
}
