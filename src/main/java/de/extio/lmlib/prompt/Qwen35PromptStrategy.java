package de.extio.lmlib.prompt;

public class Qwen35PromptStrategy implements PromptStrategy {

	private static final String IM_END = "<|im_end|>";
	private static final String THINK_START = "<think>";
	private static final String THINK_END = "</think>";

	private static final String SYSTEM_START = "<|im_start|>system\n";
	private static final String USER_START = "<|im_start|>user\n";
	private static final String ASSISTANT_START = "<|im_start|>assistant\n";
	private static final String ASSISTANT_GENERATION_START = ASSISTANT_START + THINK_START + "\n";

	@Override
	public String getPromptName() {
		return "qwen3.5";
	}

	@Override
	public StringBuilder start(final String system, final String user) {
		final StringBuilder prompt = new StringBuilder();
		if (system != null && !system.isEmpty()) {
			prompt.append(SYSTEM_START);
			prompt.append(system);
			prompt.append(IM_END).append("\n");
		}
		if (user != null && !user.isEmpty()) {
			prompt.append(USER_START);
			prompt.append(user);
			prompt.append(IM_END).append("\n");
		}
		prompt.append(ASSISTANT_GENERATION_START);
		return prompt;
	}

	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		final String assistantContent = assistant == null ? "" : assistant;
		if (this.endsWith(prompt, ASSISTANT_GENERATION_START)) {
			prompt.setLength(prompt.length() - ASSISTANT_GENERATION_START.length());
		}
		prompt.append(ASSISTANT_START);
		prompt.append(assistantContent);
		if (!assistantContent.endsWith(IM_END)) {
			if (!assistantContent.endsWith("\n")) {
				prompt.append("\n");
			}
			prompt.append(IM_END).append("\n");
		}
		else if (!assistantContent.endsWith(IM_END + "\n")) {
			prompt.append("\n");
		}
		if (user != null && !user.isEmpty()) {
			prompt.append(USER_START);
			prompt.append(user);
			prompt.append(IM_END).append("\n");
		}
		prompt.append(ASSISTANT_GENERATION_START);
	}

	@Override
	public String removeEOT(final String prompt) {
		if (prompt == null) {
			return null;
		}
		return prompt.strip().replace(IM_END, "");
	}

	@Override
	public String getResponse(final String prompt) {
		final String assistantBlock = this.assistantBlock(prompt);
		if (assistantBlock == null) {
			return null;
		}
		final int thinkEnd = assistantBlock.indexOf(THINK_END);
		if (thinkEnd != -1) {
			return assistantBlock.substring(thinkEnd + THINK_END.length()).strip();
		}
		if (assistantBlock.startsWith(THINK_START) || assistantBlock.startsWith("\n")) {
			return "";
		}
		return assistantBlock.strip();
	}

	@Override
	public String getReasoning(final String prompt) {
		final String assistantBlock = this.assistantBlock(prompt);
		if (assistantBlock == null) {
			return null;
		}
		final int thinkStart = assistantBlock.indexOf(THINK_START);
		final int thinkEnd = assistantBlock.indexOf(THINK_END);
		if (thinkEnd == -1) {
			return null;
		}
		if (thinkStart != -1 && thinkEnd > thinkStart) {
			return assistantBlock.substring(thinkStart + THINK_START.length(), thinkEnd).strip();
		}
		return assistantBlock.substring(0, thinkEnd).strip();
	}

	private String assistantBlock(final String prompt) {
		if (prompt == null) {
			return null;
		}
		String assistantBlock = prompt;
		final int lastAssistantStart = prompt.lastIndexOf(ASSISTANT_START);
		if (lastAssistantStart != -1) {
			assistantBlock = prompt.substring(lastAssistantStart + ASSISTANT_START.length());
		}
		final int end = assistantBlock.indexOf(IM_END);
		if (end != -1) {
			assistantBlock = assistantBlock.substring(0, end);
		}
		return assistantBlock;
	}

	private boolean endsWith(final StringBuilder value, final String suffix) {
		if (value.length() < suffix.length()) {
			return false;
		}
		for (int i = 0; i < suffix.length(); i++) {
			if (value.charAt(value.length() - suffix.length() + i) != suffix.charAt(i)) {
				return false;
			}
		}
		return true;
	}

}