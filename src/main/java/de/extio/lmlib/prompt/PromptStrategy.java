package de.extio.lmlib.prompt;

public interface PromptStrategy {
	
	String getPromptName();
	
	StringBuilder start(String system, String user);
	
	void next(StringBuilder prompt, String assistant, String user);
	
	default String removeEOT(final String prompt) {
		return prompt.strip();
	}
	
	default String getResponse(final String prompt) {
		return prompt;
	}

	default String getReasoning(final String prompt) {
		return null;
	}
	
}
