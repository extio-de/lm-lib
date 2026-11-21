package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Chunk;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.util.TextUtils;

public class CsvAgentResponseHandler implements StreamedAgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvAgentResponseHandler.class);
	
	private final List<String> headings;
	
	private final String keyPrefix;
	
	private final String fieldSeparator;
	
	private final String fieldSeparatorQuoted;
	
	private final String errorPrompt;

	private final String reasoningKey;

	private final BiConsumer<AgentContext, Chunk> afterChunkUpdate;

	private final boolean joinExcessColumns;
	
	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix) {
		this(headings, keyPrefix, ";");
	}
	
	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix, final String fieldSeparator) {
		this(headings, keyPrefix, fieldSeparator, null);
	}

	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix, final String fieldSeparator, final BiConsumer<AgentContext, Chunk> afterChunkUpdate) {
		this(headings, keyPrefix, fieldSeparator, afterChunkUpdate, false);
	}

	/**
	 * @param headings
	 *            the CSV column headings; defines the expected column order and the context keys the values are stored in
	 * @param keyPrefix
	 *            prefix prepended to each heading to build the context key (may be null or empty)
	 * @param fieldSeparator
	 *            the separator between CSV fields, e.g. ";" or ","
	 * @param afterChunkUpdate
	 *            optional callback invoked for every streamed chunk, may be null
	 * @param joinExcessColumns
	 *            if true, lines with more columns than headings are not skipped; instead the excess
	 *            columns are joined into the last column value using the field separator
	 */
	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix, final String fieldSeparator, final BiConsumer<AgentContext, Chunk> afterChunkUpdate, final boolean joinExcessColumns) {
		if (headings == null || headings.isEmpty()) {
			throw new IllegalArgumentException("Headings list cannot be null or empty");
		}
		this.headings = List.copyOf(headings);
		this.keyPrefix = keyPrefix != null ? keyPrefix : "";
		this.fieldSeparator = fieldSeparator;
		this.fieldSeparatorQuoted = Pattern.quote(fieldSeparator);
		this.reasoningKey = this.keyPrefix + "_reasoning";
		this.afterChunkUpdate = afterChunkUpdate;
		this.joinExcessColumns = joinExcessColumns;
		
		this.errorPrompt = "\n\nThe previous response could not be fully processed or validated. " +
				"Please format the response as CSV with '" + fieldSeparator + "' as field separator.\n" +
				"Expected columns: " + String.join(this.fieldSeparator, headings) + " \nOne row per line.";
	}
	
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		final String resp = completion.response();
		if (resp == null || resp.isBlank()) {
			LOGGER.warn("Empty completion response for CSV parsing");
			return true;
		}
		
		try {
			final boolean success = this.parseCsv(resp, context);
			if (!success) {
				LOGGER.warn("No valid CSV data parsed from response");
				this.addCsvResponseErrorPrompt(context, resp);
				return false;
			}
			
			return true;
		}
		catch (final Exception ex) {
			LOGGER.warn("Failed to parse CSV response", ex);
			this.addCsvResponseErrorPrompt(context, resp);
			return false;
		}
	}

	@Override
	public void beforeStream(final AgentContext context) {
		context.getContext().remove(this.reasoningKey);
	}

	@Override
	public boolean handleChunk(final Chunk chunk, final AgentContext context) {
		boolean handled = false;
		if (chunk.reasoningContent() != null) {
			context.setStringValue(this.reasoningKey, Objects.requireNonNullElseGet(context.getStringValue(this.reasoningKey), () -> "").concat(chunk.reasoningContent()));
			handled = true;
		}
		if (this.afterChunkUpdate != null) {
			this.afterChunkUpdate.accept(context, chunk);
			return true;
		}
		return handled;
	}
	
	private boolean parseCsv(final String csvText, final AgentContext context) {
		final String[] lines = TextUtils.normalizeModelResponse(csvText.replace("\\r", "\r").replace("\\n", "\n"), false).split("\n");
		
		int startLine = 0;
		
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i].trim();
			
			if (line.isEmpty()) {
				continue;
			}
			
			if (!line.contains(this.fieldSeparator)) {
				startLine = i + 1;
				continue;
			}
			
			final String lowerLine = line.toLowerCase();
			boolean isHeader = true;
			for (final String heading : this.headings) {
				if (!lowerLine.contains(heading.toLowerCase())) {
					isHeader = false;
					break;
				}
			}
			
			if (isHeader) {
				startLine = i + 1;
				continue;
			}
			
			break;
		}
		
		final List<List<String>> columnData = new ArrayList<>();
		for (int i = 0; i < this.headings.size(); i++) {
			columnData.add(new ArrayList<>());
		}
		
		int rowCount = 0;
		for (int i = startLine; i < lines.length; i++) {
			final String line = lines[i].trim();
			
			if (line.isEmpty()) {
				continue;
			}
			
			if (!line.contains(this.fieldSeparator)) {
				continue;
			}
			
			try {
				String[] parts = line.split(this.fieldSeparatorQuoted, -1);
				if (this.joinExcessColumns && parts.length > this.headings.size()) {
					LOGGER.debug("Joining {} excess columns into last column of line '{}'",
							parts.length - this.headings.size(), line);
					parts = this.joinExcessColumns(parts);
				}
				if (parts.length != this.headings.size()) {
					LOGGER.debug("Column count mismatch: expected {}, got {} for line '{}'",
							this.headings.size(), parts.length, line);
					continue;
				}
				
				for (int col = 0; col < parts.length; col++) {
					final String value = this.stripQuotes(parts[col].trim());
					columnData.get(col).add(value);
				}
				rowCount++;
			}
			catch (final Exception ex) {
				LOGGER.debug("Failed to parse line '{}': {}", line, ex.getMessage());
			}
		}
		
		if (rowCount == 0) {
			return false;
		}
		
		for (int i = 0; i < this.headings.size(); i++) {
			final String key = this.keyPrefix + this.headings.get(i);
			context.setStringValues(key, columnData.get(i));
			LOGGER.debug("Stored {} values in context key '{}': {}", columnData.get(i).size(), key, columnData.get(i));
		}
		
		LOGGER.debug("Parsed {} CSV rows into {} columns", rowCount, this.headings.size());
		return true;
	}
	
	private void addCsvResponseErrorPrompt(final AgentContext context, final String csvResponse) {
		// final var turn = context.getConversation().getConversation().getLast();
		// context.getConversation().replaceTurn(new Conversation.Turn(
		// 		turn.type(),
		// 		turn.text() + this.errorPromptSuffix));
		context.getConversation().addTurn(new Conversation.Turn(
				Conversation.TurnType.ASSISTANT,
				csvResponse != null ? csvResponse : ""));
		context.getConversation().addTurn(new Conversation.Turn(
				Conversation.TurnType.USER,
				this.errorPrompt));
	}
	
	private String[] joinExcessColumns(final String[] parts) {
		final int last = this.headings.size() - 1;
		final String[] joined = new String[this.headings.size()];
		System.arraycopy(parts, 0, joined, 0, last);
		
		final StringBuilder lastValue = new StringBuilder(parts[last].trim());
		for (int i = last + 1; i < parts.length; i++) {
			lastValue.append(this.fieldSeparator).append(parts[i].trim());
		}
		joined[last] = lastValue.toString();
		
		return joined;
	}
	
	private String stripQuotes(final String value) {
		if (value == null || value.length() < 2) {
			return value;
		}
		
		return TextUtils.normalizeModelResponse(value, false);
	}
}
