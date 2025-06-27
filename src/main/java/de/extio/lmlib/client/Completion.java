package de.extio.lmlib.client;

public final record Completion(String response, CompletionFinishReason finishReason, CompletionStatistics statistics) {

}
