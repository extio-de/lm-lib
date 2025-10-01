package de.extio.lmlib.client.oai.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StreamOptions {

    protected boolean includeUsage;

    @JsonProperty("include_usage")
    public boolean isIncludeUsage() {
        return includeUsage;
    }

    public void setIncludeUsage(final boolean includeUsage) {
        this.includeUsage = includeUsage;
    }
}
