package de.extio.lmlib.client;

public final record Completion(String response, String reasoning, CompletionFinishReason finishReason, CompletionStatistics statistics) {

}
