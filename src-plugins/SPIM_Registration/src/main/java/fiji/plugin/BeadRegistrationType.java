package fiji.plugin;

import java.util.NoSuchElementException;

public enum BeadRegistrationType {
	SINGLE_CHANNEL("Single-channel"),
	MULTI_CHANNEL("Multi-channel (same beads visible in different channels)");
	
	private BeadRegistrationType(String description) {
		this.description = description;
	}
	
	public final String description;
	
	static final String[] descriptions;
	
	static {
		BeadRegistrationType[] brts = BeadRegistrationType.values();
		descriptions = new String[brts.length];
		for (int i = 0; i < brts.length; i++) {
			descriptions[i] = brts[i].description;
		}
	}
	
	public static String[] getDescriptions() {
		return descriptions.clone();
	}
	
	public static final BeadRegistrationType DEFAULT = SINGLE_CHANNEL;
	
	private static BeadRegistrationType ordinalOf(int ord) {
		return values()[ord];
	}
	
	public static BeadRegistrationType byDescription(String description){
		return ordinalOf(indexOf(descriptions, description));	
	}
	
	private static <T> int indexOf(T[] haystack, T needle) {
		for(int i = 0; i < haystack.length; i ++) {
			if(haystack[i].equals(needle))
				return i;
		}
		throw new NoSuchElementException();
	}
}
