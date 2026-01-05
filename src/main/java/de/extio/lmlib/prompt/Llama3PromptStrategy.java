package de.extio.lmlib.prompt;

public class Llama3PromptStrategy implements PromptStrategy {
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		prompt.append("<|begin_of_text|>");
		if (!system.isEmpty()) {
			prompt.append("<|start_header_id|>system<|end_header_id|>\n\n");
			prompt.append(system);
			prompt.append("<|eot_id|>");
		}
		prompt.append("<|start_header_id|>user<|end_header_id|>\n\n");
		prompt.append(user);
		prompt.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n");
		
		return prompt;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n" + user + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n");
	}
	
	@Override
	public String removeEOT(final String prompt) {
		return prompt.strip().replace("<|eot_id|>", "");
	}
	
	@Override
	public String getPromptName() {
		return "llama3";
	}
}
