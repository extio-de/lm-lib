package de.extio.lmlib.prompt;

public class NoPromptStrategy implements PromptStrategy {
	
	@Override
	public String getPromptName() {
		return "noop";
	}
	
	@Override
	public StringBuilder start(final String system, final String user) {
		final var sb = new StringBuilder();
		if (system != null && !system.isBlank()) {
			sb.append(system);
		}
		if (system != null && !system.isBlank() && user != null && !user.isBlank()) {
			sb.append("\n");
		}
		if (user != null && !user.isBlank()) {
			sb.append(user);
		}
		return sb;
	}
	
	@Override
	public void next(final StringBuilder prompt, final String assistant, final String user) {
		prompt.append(assistant);
		prompt.append("\n");
		prompt.append(user);
	}
	
}
