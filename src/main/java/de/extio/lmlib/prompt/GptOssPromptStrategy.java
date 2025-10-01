package de.extio.lmlib.prompt;

import org.springframework.stereotype.Component;

@Component
public class GptOssPromptStrategy implements PromptStrategy {
    
    private static final String finalMarker = "<|channel|>final<|message|>";
    
    private static final String startMarker = "<|start|>";

    private static final String assistantStart = startMarker + "assistant";
    
    private static final String analysisMarker = "<|channel|>analysis<|message|>";

    @Override
    public StringBuilder start(final String system, final String user) {
        final StringBuilder prompt = new StringBuilder();
        if (!system.isEmpty()) {
            prompt.append("<|start|>system<|message|>");
            prompt.append(system);
            prompt.append("<|end|>");
        }
        prompt.append("<|start|>user<|message|>");
        prompt.append(user);
        prompt.append("<|end|>");
        prompt.append(assistantStart);
        
        return prompt;
    }
    
    @Override
    public void next(final StringBuilder prompt, final String assistant, final String user) {
        final String assistantContent = assistant == null ? "" : assistant;
        if (assistantContent.startsWith(assistantStart)) {
            final int assistantStartLength = assistantStart.length();
            if (prompt.length() >= assistantStartLength) {
                boolean endsWithAssistantStart = true;
                for (int i = 0; i < assistantStartLength; i++) {
                    if (prompt.charAt(prompt.length() - assistantStartLength + i) != assistantStart.charAt(i)) {
                        endsWithAssistantStart = false;
                        break;
                    }
                }
                if (endsWithAssistantStart) {
                    prompt.setLength(prompt.length() - assistantStartLength);
                }
            }
        }
        if (assistantContent.contains(finalMarker)) {
            prompt.append(assistantContent);
            if (!assistantContent.endsWith("<|end|>")) {
                prompt.append("<|end|>");
            }
        }
        else {
            prompt.append("<|channel|>final<|message|>");
            prompt.append(assistantContent);
            prompt.append("<|end|>");
        }
        prompt.append("<|start|>user<|message|>");
        prompt.append(user == null ? "" : user);
        prompt.append("<|end|>");
        prompt.append(assistantStart);
    }
    
    @Override
    public String removeEOT(final String prompt) {
        return prompt.strip().replace("<|end|>", "").replace("<|return|>", "");
    }
    
    @Override
    public String getPromptName() {
        return "gpt-oss";
    }
    
    @Override
    public String getResponse(final String prompt) {
        if (prompt == null) {
            return null;
        }
        final int markerIndex = prompt.indexOf(finalMarker);
        if (markerIndex == -1) {
            return prompt;
        }
        final int start = markerIndex + finalMarker.length();
        int end = prompt.indexOf(startMarker, start);
        if (end == -1) {
            end = prompt.length();
        }
        return prompt.substring(start, end).strip();
    }
    
    @Override
    public String getReasoning(final String prompt) {
        if (prompt == null) {
            return null;
        }
        final int markerIndex = prompt.indexOf(analysisMarker);
        if (markerIndex == -1) {
            return null;
        }
        final int start = markerIndex + analysisMarker.length();
        int end = prompt.indexOf(startMarker, start);
        if (end == -1) {
            end = prompt.length();
        }
        return prompt.substring(start, end).strip();
    }
}
