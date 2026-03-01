package de.extio.lmlib.agent.responsehandler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.extio.lmlib.agent.AgentContext;
import de.extio.lmlib.client.Completion;
import de.extio.lmlib.client.Conversation;
import de.extio.lmlib.util.TextUtils;

public class CsvAgentResponseHandler implements AgentResponseHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvAgentResponseHandler.class);
	
	private final List<String> headings;
	
	private final String keyPrefix;
	
	private final String fieldSeparator;
	
	private final String fieldSeparatorQuoted;
	
	private final String errorPromptSuffix;
	
	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix) {
		this(headings, keyPrefix, ";");
	}
	
	public CsvAgentResponseHandler(final List<String> headings, final String keyPrefix, final String fieldSeparator) {
		if (headings == null || headings.isEmpty()) {
			throw new IllegalArgumentException("Headings list cannot be null or empty");
		}
		this.headings = List.copyOf(headings);
		this.keyPrefix = keyPrefix != null ? keyPrefix : "";
		this.fieldSeparator = fieldSeparator;
		this.fieldSeparatorQuoted = Pattern.quote(fieldSeparator);
		
		this.errorPromptSuffix = "\n\nThe previous response could not be fully processed or validated. " +
				"Please format the response as CSV with '" + fieldSeparator + "' as field separator.\n" +
				"Expected columns: " + String.join(", ", headings) + " \nOne row per line.";
	}
	
	@Override
	public boolean handle(final Completion completion, final AgentContext context) {
		final String resp = completion.response();
		if (resp == null || resp.isBlank()) {
			LOGGER.warn("Empty completion response for CSV parsing");
			return false;
		}
		
		try {
			final boolean success = this.parseCsv(resp, context);
			if (!success) {
				LOGGER.warn("No valid CSV data parsed from response");
				this.addCsvResponseErrorPrompt(context);
				return false;
			}
			
			return true;
		}
		catch (final Exception ex) {
			LOGGER.warn("Failed to parse CSV response", ex);
			this.addCsvResponseErrorPrompt(context);
			return false;
		}
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
				final String[] parts = line.split(this.fieldSeparatorQuoted, -1);
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
			LOGGER.debug("Stored {} values in context key '{}'", columnData.get(i).size(), key);
		}
		
		LOGGER.debug("Parsed {} CSV rows into {} columns", rowCount, this.headings.size());
		return true;
	}
	
	private void addCsvResponseErrorPrompt(final AgentContext context) {
		final var turn = context.getConversation().getConversation().getLast();
		context.getConversation().replaceTurn(new Conversation.Turn(
				turn.type(),
				turn.text() + this.errorPromptSuffix));
	}
	
	private String stripQuotes(final String value) {
		if (value == null || value.length() < 2) {
			return value;
		}
		
		return TextUtils.normalizeModelResponse(value, false);
	}
}
