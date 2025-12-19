package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

@Component
public class Qwen3PromptStrategy implements PromptStrategy {

	private static final String IM_START = "<|im_start|>";
	private static final String IM_END = "<|im_end|>";
	private static final String THINK_START = "<think>";
	private static final String THINK_END = "</think>";

	private static final String SYSTEM_START = IM_START + "system\n";
	private static final String USER_START = IM_START + "user\n";
	private static final String ASSISTANT_START = IM_START + "assistant\n";

	@Override
	public String getPromptName() {
		return "qwen3";
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
		prompt.append(ASSISTANT_START);
		return prompt;
	}

	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		final String assistantContent = assistant == null ? "" : assistant;

		if (assistantContent.startsWith(ASSISTANT_START)) {
			final int assistantStartLength = ASSISTANT_START.length();
			if (prompt.length() >= assistantStartLength) {
				boolean endsWithAssistantStart = true;
				for (int i = 0; i < assistantStartLength; i++) {
					if (prompt.charAt(prompt.length() - assistantStartLength + i) != ASSISTANT_START.charAt(i)) {
						endsWithAssistantStart = false;
						break;
					}
				}
				if (endsWithAssistantStart) {
					prompt.setLength(prompt.length() - assistantStartLength);
				}
			}
		}

		prompt.append(assistantContent);
		if (!assistantContent.endsWith(IM_END)) {
			if (!assistantContent.endsWith("\n")) {
				prompt.append("\n");
			}
			prompt.append(IM_END).append("\n");
		} else if (!assistantContent.endsWith(IM_END + "\n")) {
			prompt.append("\n");
		}

		if (user != null && !user.isEmpty()) {
			prompt.append(USER_START);
			prompt.append(user);
			prompt.append(IM_END).append("\n");
		}
		prompt.append(ASSISTANT_START);
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
		
		// Remove thinking part if present
		final int thinkEnd = assistantBlock.indexOf(THINK_END);
		if (thinkEnd != -1) {
			return assistantBlock.substring(thinkEnd + THINK_END.length()).strip();
		}
		
		return assistantBlock.strip();
	}

	@Override
	public String getReasoning(final String prompt) {
		if (prompt == null) {
			return null;
		}
		
		String assistantBlock = prompt;
		final int lastAssistantStart = prompt.lastIndexOf(ASSISTANT_START);
		if (lastAssistantStart != -1) {
			assistantBlock = prompt.substring(lastAssistantStart + ASSISTANT_START.length());
		}
		
		final int thinkStart = assistantBlock.indexOf(THINK_START);
		final int thinkEnd = assistantBlock.indexOf(THINK_END);
		
		if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
			return assistantBlock.substring(thinkStart + THINK_START.length(), thinkEnd).strip();
		}
		
		return null;
	}
}
