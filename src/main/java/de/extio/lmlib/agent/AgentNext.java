package de.extio.lmlib.agent;

import java.util.function.Function;

public final record AgentNext(String name, Boolean gradingPassed, Boolean responseAsUserPrompt, Function<String, String> transformResponse) {
	
}
