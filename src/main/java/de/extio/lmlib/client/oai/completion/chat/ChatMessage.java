package de.extio.lmlib.client.oai.completion.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

final class ChatMessage {
    
    private String role;
    
    private String content;
    
    public ChatMessage() {
    }
    
    public ChatMessage(final String role, final String content) {
        this.role = role;
        this.content = content;
    }

    @JsonProperty("role")
    public String getRole() {
        return role;
    }
    
    public void setRole(final String role) {
        this.role = role;
    }
    
    @JsonProperty("content")
    public String getContent() {
        return content;
    }
    
    public void setContent(final String content) {
        this.content = content;
    }
    
}
