package de.extio.lmlib.profile;

public record ModelCategory(String modelProfile, String shortName) {

	public static final ModelCategory SMALL = new ModelCategory("profile.model.s", "S");
	public static final ModelCategory MEDIUM = new ModelCategory("profile.model.m", "M");
	public static final ModelCategory LARGE = new ModelCategory("profile.model.l", "L");
	public static final ModelCategory XLARGE = new ModelCategory("profile.model.xl", "XL");
	public static final ModelCategory HOT = new ModelCategory("profile.model.hot", "HOT");
	public static final ModelCategory COLD = new ModelCategory("profile.model.cold", "COLD");
	
	public ModelCategory {
		if (modelProfile == null || modelProfile.isEmpty()) {
			throw new IllegalArgumentException("modelProfile must not be null or empty");
		}
		if (shortName == null || shortName.isEmpty()) {
			throw new IllegalArgumentException("shortName must not be null or empty");
		}
	}

	public String getModelProfile() {
		return this.modelProfile;
	}
	
	public String getShortName() {
		return this.shortName;
	}
}
