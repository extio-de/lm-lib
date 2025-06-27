package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class Usage {
    
    private int completionTokens;
    
    private int promptTokens;
    
    private int totalTokens;
    
    @JsonProperty("completion_tokens")
    public int getCompletionTokens() {
        return completionTokens;
    }
    
    public void setCompletionTokens(final int completionTokens) {
        this.completionTokens = completionTokens;
    }
    
    @JsonProperty("prompt_tokens")
    public int getPromptTokens() {
        return promptTokens;
    }
    
    public void setPromptTokens(final int promptTokens) {
        this.promptTokens = promptTokens;
    }
    
    @JsonProperty("total_tokens")
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(final int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    @Override
    public String toString() {
        return "Usage [completionTokens=" + completionTokens + ", promptTokens=" + promptTokens + ", totalTokens=" + totalTokens + "]";
    }
    
}
