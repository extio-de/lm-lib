package de.extio.lmlib.profile;

public enum ModelCategory {
	
	SMALL("profile.model.s", "S"),
	MEDIUM("profile.model.m", "M"),
	LARGE("profile.model.l", "L"),
	HOT("profile.model.hot", "HOT"),
	COLD("profile.model.cold", "COLD");
	
	private final String modelProfile;
	
	private final String shortName;
	
	ModelCategory(final String modelProfile, final String shortName) {
		this.modelProfile = modelProfile;
		this.shortName = shortName;
	}
	
	public String getModelProfile() {
		return this.modelProfile;
	}
	
	public String getShortName() {
		return this.shortName;
	}
}
