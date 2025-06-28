package de.extio.lmlib.client.oai.completion.text;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CompletionStreamOptions {

    private boolean includeUsage;

    @JsonProperty("include_usage")
    public boolean isIncludeUsage() {
        return includeUsage;
    }

    public void setIncludeUsage(final boolean includeUsage) {
        this.includeUsage = includeUsage;
    }
}
