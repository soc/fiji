package fiji.plugin;

import java.util.NoSuchElementException;

public enum BeadBrightness {
	VERY_WEAK("Very weak", 0.001f),
	WEAK("Weak", 0.008f),
	COMPARABLE_TO_SAMPLE("Comparable to Sample", 0.03f),
	STRONG("Strong", 0.1f),
	ADVANCED("Advanced ...", Float.NaN), 
	INTERACTIVE("Interactive ...", Float.NaN);
	
	BeadBrightness(final String description, final float minPeakValue){
		this.description = description;
		this.minPeakValue = minPeakValue;
	}
	
	public final String description;
	private final float minPeakValue;
	
	static final String[] descriptions;
	
	static {
		BeadBrightness[] bbs = BeadBrightness.values();
		descriptions = new String[bbs.length];
		for (int i = 0; i < bbs.length; i++) {
			descriptions[i] = bbs[i].description;
		}
	}
	
	public float getMinPeakValue(){
		if(minPeakValue == Float.NaN)
			throw new UnsupportedOperationException();
		else return minPeakValue;
	}
		
	public static String[] getDescriptions(){
		return descriptions.clone();
	}
	
	public static BeadBrightness DEFAULT = WEAK;
	
	private static BeadBrightness ordinalOf(int ord) {
		return values()[ord];
	}
	
	public static BeadBrightness byDescription(String description){
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
