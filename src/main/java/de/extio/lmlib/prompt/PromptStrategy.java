package de.extio.lmlib.prompt;

public interface PromptStrategy {
	
	String getPromptName();
	
	StringBuilder start(String system, String question, String text);
	
	void continue_(StringBuilder prompt, String assistant);
	
	void next(StringBuilder prompt, String assistant, String user);
	
	default String removeEOT(final String prompt) {
		return prompt.strip();
	}
	
}
