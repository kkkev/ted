package nu.ted.guide;

import java.util.Map;

public class GuideFactory {
	
	static private Map<String, GuideDB> knownGuides;

	public static void addGuide(GuideDB guide) {
		knownGuides.put(guide.getName(), guide);
	}
	
	public static GuideDB getGuide(String name) {
		return knownGuides.get(name);
	}
}
