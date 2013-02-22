package isabelle.eclipse.ui.editors;

import isabelle.eclipse.core.text.IsabelleAnnotation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;

/**
 * Defines the constants used to render Isabelle annotations/markers. Contains annotation/marker
 * types to instantiate as well as bidirectional mapping from abstract definition types (
 * {@link IsabelleAnnotation}) to concrete annotation/marker types.
 * 
 * @author Andrius Velykis
 */
public class IsabelleAnnotationConstants {

	private static final String MARKER_PROBLEM = "isabelle.eclipse.ui.markerProblem";
	private static final String MARKER_LEGACY = "isabelle.eclipse.ui.markerLegacy";
	private static final String MARKER_INFO = "isabelle.eclipse.ui.markerInfo";
	
	// default annotations for the problem/info markers
	private static final String ANNOTATION_ERROR = "org.eclipse.ui.workbench.texteditor.error";
	private static final String ANNOTATION_WARNING = "org.eclipse.ui.workbench.texteditor.warning";
	// TODO add custom legacy annotation
	private static final String ANNOTATION_LEGACY = "isabelle.eclipse.ui.annotation.legacy";
	private static final String ANNOTATION_INFO = "isabelle.eclipse.ui.annotation.info";
	
	// TODO foreground colours, Isabelle_Markup.foreground? Or actually syntax colours?
	private static final String ANNOTATION_BAD = "isabelle.eclipse.ui.annotation.markup.bad";
	private static final String ANNOTATION_INTENSIFY = "isabelle.eclipse.ui.annotation.markup.intensify";
	private static final String ANNOTATION_TOKEN = "isabelle.eclipse.ui.annotation.markup.token";
	
	private static final String ANNOTATION_OUTDATED = "isabelle.eclipse.ui.annotation.cmd.outdated";
	private static final String ANNOTATION_UNFINISHED = "isabelle.eclipse.ui.annotation.cmd.unfinished";
	// TODO use Isabelle's colors? At least as preference defaults?
	private static final String ANNOTATION_UNPROCESSED = "isabelle.eclipse.ui.annotation.cmd.unprocessed";
//	private static final String ANNOTATION_FAILED = "isabelle.eclipse.ui.annotation.cmd.failed";
//	private static final String ANNOTATION_FINISHED = "isabelle.eclipse.ui.annotation.cmd.finished";
	
	public static final Map<IsabelleAnnotation, String> ANNOTATION_KEYS;
	public static final Map<String, IsabelleAnnotation> ANNOTATION_TYPES;
	
	public static final Map<IsabelleAnnotation, MarkerKey> MARKER_KEYS;
	public static final Map<MarkerKey, IsabelleAnnotation> MARKER_TYPES;
	
	public static final Set<String> MARKERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			MARKER_PROBLEM, MARKER_INFO)));
	
	static {
		
		Map<IsabelleAnnotation, String> annTK = new HashMap<IsabelleAnnotation, String>();
		Map<String, IsabelleAnnotation> annKT = new HashMap<String, IsabelleAnnotation>();
		
		Map<IsabelleAnnotation, MarkerKey> markerTK = new HashMap<IsabelleAnnotation, MarkerKey>();
		Map<MarkerKey, IsabelleAnnotation> markerKT = new HashMap<MarkerKey, IsabelleAnnotation>();
		
		for (IsabelleAnnotation type : IsabelleAnnotation.values()) {
			
			String annKey = getAnnotationKey(type);
			if (annKey != null) {
				annTK.put(type, annKey);
				annKT.put(annKey, type);
			}
			
			MarkerKey markerKey = getMarkerKey(type);
			if (markerKey != null) {
				markerTK.put(type, markerKey);
				markerKT.put(markerKey, type);
			}
		}
		
		ANNOTATION_KEYS = Collections.unmodifiableMap(annTK);
		ANNOTATION_TYPES = Collections.unmodifiableMap(annKT);
		MARKER_KEYS = Collections.unmodifiableMap(markerTK);
		MARKER_TYPES = Collections.unmodifiableMap(markerKT);
	}
	
	private static String getAnnotationKey(IsabelleAnnotation type) {
		switch (type) {
		case MARKUP_BAD: return ANNOTATION_BAD;
		case MARKUP_INTENSIFY: return ANNOTATION_INTENSIFY;
		case MARKUP_TOKEN_RANGE: return ANNOTATION_TOKEN;
		case MESSAGE_ERROR: return ANNOTATION_ERROR;
		case MESSAGE_LEGACY: return ANNOTATION_LEGACY;
		case MESSAGE_WARNING: return ANNOTATION_WARNING;
		case MESSAGE_WRITELN: return ANNOTATION_INFO;
		case STATUS_OUTDATED: return ANNOTATION_OUTDATED;
		case STATUS_UNFINISHED: return ANNOTATION_UNFINISHED;
		case STATUS_UNPROCESSED: return ANNOTATION_UNPROCESSED;
		default: return null;
		}
	}
	
	private static MarkerKey getMarkerKey(IsabelleAnnotation type) {
		switch (type) {
		case MESSAGE_ERROR: return new MarkerKey(MARKER_PROBLEM, IMarker.SEVERITY_ERROR);
		case MESSAGE_WARNING: return new MarkerKey(MARKER_PROBLEM, IMarker.SEVERITY_WARNING);
		case MESSAGE_LEGACY: return new MarkerKey(MARKER_LEGACY, IMarker.SEVERITY_WARNING);
		case MESSAGE_WRITELN: return new MarkerKey(MARKER_INFO, IMarker.SEVERITY_INFO);
		default: return null;
		}
	}
	
	/**
	 * Private constructor to prevent instantiation of constants class
	 */
	private IsabelleAnnotationConstants() {}
	
	public static class MarkerKey {
		private final String key;
		private final Integer severity;
		
		public MarkerKey(String key, Integer severity) {
			this.key = key;
			this.severity = severity;
		}

		public String getKey() {
			return key;
		}

		public Integer getSeverity() {
			return severity;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((severity == null) ? 0 : severity.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MarkerKey other = (MarkerKey) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (severity == null) {
				if (other.severity != null)
					return false;
			} else if (!severity.equals(other.severity))
				return false;
			return true;
		}
	}
	
}
