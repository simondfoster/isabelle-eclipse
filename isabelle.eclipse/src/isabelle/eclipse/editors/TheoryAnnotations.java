package isabelle.eclipse.editors;

import isabelle.Command;
import isabelle.Markup;
import isabelle.Command.State;
import isabelle.Text.Info;
import isabelle.Text.Range;
import isabelle.eclipse.IsabelleEclipsePlugin;
import isabelle.scala.DocumentStatusFacade;
import isabelle.scala.ISessionCommandsListener;
import isabelle.scala.ISessionRawMessageListener;
import isabelle.scala.ResultFacade;
import isabelle.scala.SessionActor;
import isabelle.scala.SessionFacade;
import isabelle.scala.SnapshotFacade;
import isabelle.scala.SnapshotFacade.CommandInfo;
import isabelle.scala.SnapshotFacade.MarkupMessage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class TheoryAnnotations {
	
	private final TheoryEditor editor;

	public static final String MARKER_PROBLEM = "isabelle.eclipse.markerProblem";
	public static final String MARKER_INFO = "isabelle.eclipse.markerInfo";
	
	public static final String ANNOTATION_BAD = "isabelle.eclipse.editor.markup.bad";
	public static final String ANNOTATION_TOKEN = "isabelle.eclipse.editor.markup.token";
	
	public static final String ANNOTATION_OUTDATED = "isabelle.eclipse.editor.commandStatus.outdated";
	public static final String ANNOTATION_UNFINISHED = "isabelle.eclipse.editor.commandStatus.unfinished";
	public static final String ANNOTATION_UNPROCESSED = "isabelle.eclipse.editor.commandStatus.unprocessed";
	public static final String ANNOTATION_FAILED = "isabelle.eclipse.editor.commandStatus.failed";
	public static final String ANNOTATION_FINISHED = "isabelle.eclipse.editor.commandStatus.finished";
	
	private static final Object THEORY_ANNOTATIONS = new Object();
	
	private SessionFacade session = null;
	private final SessionActor sessionActor = new SessionActor().commandsChanged(new ISessionCommandsListener() {
		
		@Override
		public void commandsChanged(final Set<Command> commands) {
//			System.out.println("Commands changed: " + commands);
			
			// notify to the UI thread
			Display display = getDisplay();
	        if (!display.isDisposed()) {
	           display.asyncExec(new Runnable() {
	              public void run() {
	            	  updateCommandMarkers(commands);
	              }
	           });
	        }
		}
	}).rawMessages(new ISessionRawMessageListener() {
		
		@Override
		public void handleMessage(ResultFacade result) {
//			System.out.println("Message changed: " + result.getMessageString());
//			
//			// notify to the UI thread
//			Display display = getDisplay();
//	        if (!display.isDisposed()) {
//	           display.asyncExec(new Runnable() {
//	              public void run() {
//	            	  updateAllMarkers();
//	              }
//	           });
//	        }
		}
	});
	
	public TheoryAnnotations(TheoryEditor editor) {
		super();
		this.editor = editor;
		System.out.println("Create markers support");
	}
	
	private Display getDisplay() {
		return editor.getSite().getWorkbenchWindow().getWorkbench().getDisplay();
	}
	
	public void connect(SessionFacade session) {
		System.out.println("Connecting annotation session");
		Assert.isTrue(this.session == null || this.session == session);
		
		if (this.session == session) {
			// nothing to do here
			return;
		}
		
		this.session = session;
		this.session.addCommandsChangedActor(sessionActor);
		
		updateAllMarkers();
	}
	
	public void disconnect(SessionFacade session) {
		
		Assert.isTrue(this.session == session);
		
		this.session.removeCommandsChangedActor(sessionActor);
		this.session = null;
	}
	
	private void updateAllMarkers() {
		SnapshotFacade snapshot = editor.getSnapshot();
		
		Set<Command> snapshotCommands = new LinkedHashSet<Command>();
		if (snapshot != null) {
			snapshotCommands.addAll(snapshot.getCommands());
		}
		
		updateCommandMarkers(snapshotCommands);
	}
	
	private void updateCommandMarkers(Set<Command> commands) {
		
//		System.out.println("Update markers");
		
		IResource markerResource = getMarkerResource();
		
		SnapshotFacade snapshot = editor.getSnapshot();
		if (snapshot == null) {
//			// no snapshot - delete previous markers?
//			deleteMarkers(markerResource);
			return;
		}
		
		// copy commands set
		commands = new LinkedHashSet<Command>(commands);
		// only use commands that are in the snapshot
		commands.retainAll(snapshot.getCommands());

		if (commands.isEmpty()) {
			return;
		}
		
		deleteMarkers(markerResource);
		
		try {

			updateAnnotations();
			createMarkupMarkers(snapshot, markerResource);
		
		} catch (CoreException e) {
			IsabelleEclipsePlugin.getDefault().log(e.getLocalizedMessage(), e);
		}
		
	}

	private void deleteMarkers(IResource markerResource) {
		// remove all current markers
		try {
			markerResource.deleteMarkers(MARKER_PROBLEM, false, IResource.DEPTH_ZERO);
			markerResource.deleteMarkers(MARKER_INFO, false, IResource.DEPTH_ZERO);
		} catch (CoreException e1) {
			IsabelleEclipsePlugin.getDefault().log(e1.getLocalizedMessage(), e1);
		}
	}
	
	private int getDocumentLength() {
		
		if (editor.getDocument() == null) {
			return 0;
		}
		
		return editor.getDocument().getLength();
	}
	
	private void createMarkupMarkers(SnapshotFacade snapshot, IResource markerResource) throws CoreException {
		String[] messageMarkups = new String[] { Markup.WRITELN(), Markup.WARNING(), Markup.ERROR() };
		Iterator<Info<MarkupMessage>> messageRanges = 
			snapshot.selectMarkupMessages(messageMarkups, new Range(0, getDocumentLength()));
		
//		Iterator<Info<String>> badRanges = snapshot.selectMarkupNames(new String[] { Markup.BAD() }, new Range(0, getDocumentLength()));
//		StringBuilder out = new StringBuilder();
//		while (badRanges.hasNext()) {
//			Info<String> range = badRanges.next();
//			out.append(range.info());
//			out.append(";");
//		}
		
		while (messageRanges.hasNext()) {
			Info<MarkupMessage> info = messageRanges.next();
			Range range = info.range();
			String markup = info.info().getName();
			String message = info.info().getText();
			
			if (Markup.WRITELN().equals(markup)) {
				createMarkupMessageMarker(markerResource, MARKER_INFO, IMarker.SEVERITY_INFO, range, message);
			} else if (Markup.WARNING().equals(markup)) {
				createMarkupMessageMarker(markerResource, MARKER_PROBLEM, IMarker.SEVERITY_WARNING, range, message);
			} else if (Markup.ERROR().equals(markup)) {
				createMarkupMessageMarker(markerResource, MARKER_PROBLEM, IMarker.SEVERITY_ERROR, range, message);
			}
		}
	}
	
	private void createMarkupMessageMarker(IResource markerResource, String type, 
			int severity, Range range, String message) throws CoreException {
		
		Map<String, Object> markerAttrs = new HashMap<String, Object>();
		
		markerAttrs.put(IMarker.SEVERITY, severity);
		markerAttrs.put(IMarker.CHAR_START, range.start());
		markerAttrs.put(IMarker.CHAR_END, range.stop());
		try {
			if (editor.getDocument() != null) {
				int line = editor.getDocument().getLineOfOffset(range.start()) + 1;
				markerAttrs.put(IMarker.LOCATION, "line " + line);	
			}
		} catch (BadLocationException ex) {
			// ignore
		}
		markerAttrs.put(IMarker.MESSAGE, message);
		
		MarkerUtilities.createMarker(markerResource, markerAttrs, type);
	}
	
	
	private void updateAnnotations() throws CoreException {
		
		IDocument document = editor.getDocument();
		if (document == null) {
			return;
		}
		
		IAnnotationModel baseAnnotationModel = 
			editor.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
		if (baseAnnotationModel == null) {
			return;
		}
		
		// use modern models
		Assert.isTrue(baseAnnotationModel instanceof IAnnotationModelExtension);
		
		AnnotationModel annotationModel = getAnnotationModel(
				(IAnnotationModelExtension) baseAnnotationModel, THEORY_ANNOTATIONS); 
		
		Map<Annotation, Position> addAnnotations = new HashMap<Annotation, Position>();

		SnapshotFacade snapshot = editor.getSnapshot();
		
		if (session != null && snapshot != null) {
			
			int editorLineCount = document.getNumberOfLines();
			
			for (int line = 0; line < editorLineCount; line++) {
				
				try {
					
					int lineStart = document.getLineOffset(line);
					int lineLength = document.getLineLength(line);
					
					Range lineRange = properLineRange(document, lineStart, lineStart + lineLength);
					
					addAnnotations.putAll(createCommandStatusAnnotations(session, snapshot, lineRange));
					addAnnotations.putAll(createMarkupAnnotations(session, snapshot, lineRange));
				
				} catch (BadLocationException ex) {
					// ignore bad location
				}
				
			}
		}
		
		annotationModel.removeAllAnnotations();
		
		annotationModel.replaceAnnotations(new Annotation[0], addAnnotations);
		
	}

	private IResource getMarkerResource() {
		return IsabelleFileDocumentProvider.getMarkerResource(editor.getEditorInput());
	}
	
	private AnnotationModel getAnnotationModel(IAnnotationModelExtension baseModel, Object key) {
		
		AnnotationModel model = (AnnotationModel) baseModel.getAnnotationModel(key);
		if (model == null) {
			model = new AnnotationModel();
			baseModel.addAnnotationModel(key, model);
		}
		
		return model;
	}

	private Range properLineRange(IDocument document, int start, int end) {
		int stop = start < end ? end - 1 : Math.min(end, document.getLength());
		return new Range(start, stop);
	}
	
	private Map<Annotation, Position> createCommandStatusAnnotations(
			SessionFacade session, SnapshotFacade snapshot, Range lineRange) {
		
		Map<Annotation, Position> annotations = new HashMap<Annotation, Position>();
		
		// get annotations for command status
		Iterator<CommandInfo> cmds = snapshot.commandRange(snapshot.revert(lineRange));
		while (cmds.hasNext()) {
			CommandInfo commandTuple = cmds.next();
			Command command = commandTuple.getCommand();
			int commandStart = commandTuple.getCommandStart();
			
			if (command.is_ignored()) {
				continue;
			}
			
			String annotationType = getStatusAnnotationType(session, snapshot, command);
			
			if (annotationType == null) {
				continue;
			}
			
			Range range = lineRange.restrict(snapshot.convert(command.range().$plus(commandStart)));
			
			addAnnotation(annotations, annotationType, range);
		}
		
		return annotations;
	}

	private String getStatusAnnotationType(SessionFacade session, SnapshotFacade snapshot, Command command) {
		State state = snapshot.state(command);
		
		if (snapshot.getSnapshot().is_outdated()) {
//			System.out.println("Command " + command.toString() + " is outdated");
			return ANNOTATION_OUTDATED;
		}
		
		DocumentStatusFacade status = session.commandStatus(state);
		if (status.isForked()) {
			if (status.getForked() > 0) {
//				System.out.println("Command " + command.toString() + " is unfinished");
				return ANNOTATION_UNFINISHED;
			}
		}
		
		if (status.isUnprocessed()) {
//			System.out.println("Command " + command.toString() + " is unprocessed");
			return ANNOTATION_UNPROCESSED;
		}
		
		if (status.isFailed()) {
//			System.out.println("Command " + command.toString() + " is failed");
			return ANNOTATION_FAILED;
		}
		
		if (status.isFinished()) {
			return ANNOTATION_FINISHED;
		}
		
		return null;
	}

	private Map<Annotation, Position> createMarkupAnnotations(
			SessionFacade session, SnapshotFacade snapshot, Range lineRange) {
		
		Map<Annotation, Position> annotations = new HashMap<Annotation, Position>();
		
		// get annotations for command status
		Iterator<Info<String>> markupRanges = snapshot.selectMarkupNames(new String[] { Markup.BAD(), Markup.TOKEN_RANGE() }, lineRange);
		while (markupRanges.hasNext()) {
			Info<String> info = markupRanges.next();
			Range range = info.range();
			String markup = info.info();
			
			String annotationType = getMarkupAnnotationType(markup);
			
			if (annotationType == null) {
				continue;
			}
			
			addAnnotation(annotations, annotationType, range);
		}
		
		return annotations;
	}
	
	private String getMarkupAnnotationType(String markup) {
		
		if (Markup.TOKEN_RANGE().equals(markup)) {
			return ANNOTATION_TOKEN;
		}
		
		if (Markup.BAD().equals(markup)) {
			return ANNOTATION_BAD;
		}
		
		return null;
	}
	
	private void addAnnotation(Map<Annotation, Position> annotations, String annotationType, Range range) {
		Annotation annotation = new Annotation(false);
		annotation.setType(annotationType);
		
		Position position = new Position(range.start(), range.stop() - range.start());
		annotations.put(annotation, position);
	}
	
//	private void updateStatusAnnotations() throws CoreException {
//		
//		IDocument document = editor.getDocument();
//		if (document == null) {
//			return;
//		}
//		
//		int editorLineCount = document.getNumberOfLines();
//		
//		IAnnotationModel annotationModel = editor.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
//		if (annotationModel == null) {
//			return;
//		}
//		
//		SessionFacade session = editor.getIsabelleSession();
//		SnapshotFacade snapshot = editor.getSnapshot();
//		
//		// use modern models
//		Assert.isTrue(annotationModel instanceof IAnnotationModelExtension);
//		
//		Set<Annotation> modelAnnotations = getModelLineAnnotations(annotationModel);
//
//		Map<Annotation, Position> addAnnotations = new HashMap<Annotation, Position>();
//		
//		int submittedLine = editor.getSubmittedLine();
//		for (int line = 0; line < editorLineCount; line++) {
//			
//			Annotation annotation = getLineAnnotation(line);
//			
//			try {
//				
//				int lineStart = document.getLineOffset(line);
//				int lineLength = document.getLineLength(line);
//				
//				if (!ANNOTATION_BAD.equals(annotation.getType())) {
//					annotation.setType(ANNOTATION_BAD);
//				}
//				
//				String annotationType = null;
//				if (line <= submittedLine) {
//					annotationType = ANNOTATION_FINISHED;
//				}
//				
//				if (!modelAnnotations.contains(annotation)) {
//					addAnnotations.put(annotation, new Position(lineStart, lineLength));
//				} else {
//					
//					// remove as used - the remainder will be deleted
//					modelAnnotations.remove(annotation);
//					
//					boolean updatePosition = false;
//					Position pos = annotationModel.getPosition(annotation);
//					if (pos.getOffset() != lineStart) {
//						pos.setOffset(lineStart);
//						updatePosition = true;
//					}
//					if (pos.getLength() != lineLength) {
//						pos.setLength(lineLength);
//						updatePosition = true;
//					}
//					
//					if (updatePosition) {
//						((IAnnotationModelExtension) annotationModel).modifyAnnotationPosition(annotation, pos);
//					}
//				}
//			
//			} catch (BadLocationException ex) {
//				// ignore bad location
//			}
//			
//			
////			Map<String, Object> markerAttrs = new HashMap<String, Object>();
////			
////			markerAttrs.put(IMarker.LINE_NUMBER, line);
////			markerAttrs.put(IMarker.SEVERITY, 2);
////			
////			if (document != null) {
////				try {
////					int lineOffset = document.getLineOffset(line);
////					int lineLength = document.getLineLength(line);
////					
////					markerAttrs.put(IMarker.CHAR_START, lineOffset);
////					markerAttrs.put(IMarker.CHAR_END, lineOffset + lineLength);
////				} catch (BadLocationException ex) {
////					ex.printStackTrace();
////				}
////			}
////			
////			MarkerUtilities.createMarker(markerResource, markerAttrs, COMMAND_STATUS_MARKER);
//			
////			markerResource.findMarkers(type, includeSubtypes, depth);
//		}
//		
//		((IAnnotationModelExtension) annotationModel).replaceAnnotations(
//				modelAnnotations.toArray(new Annotation[modelAnnotations.size()]), 
//				addAnnotations);
//		
//	}
//	
//	private void updateTestAnnotations(IDocument document, int editorLineCount, IAnnotationModel baseAnnotationModel) {
//		
//		AnnotationModel annotationModel = getAnnotationModel(
//				(IAnnotationModelExtension) baseAnnotationModel, THEORY_ANNOTATIONS); 
//		
//		Set<Annotation> modelAnnotations = getModelLineAnnotations(annotationModel);
//
//		Map<Annotation, Position> addAnnotations = new HashMap<Annotation, Position>();
//		
//		int submittedLine = editor.getSubmittedLine();
//		for (int line = 0; line < editorLineCount; line++) {
//			
//			Annotation annotation = getLineAnnotation(line);
//			
//			try {
//				
//				int lineStart = document.getLineOffset(line);
//				int lineLength = document.getLineLength(line);
//				
//				if (!ANNOTATION_BAD.equals(annotation.getType())) {
//					annotation.setType(ANNOTATION_BAD);
//				}
//				
//				String annotationType = null;
//				if (line <= submittedLine) {
//					annotationType = ANNOTATION_FINISHED;
//				}
//				
//				if (!modelAnnotations.contains(annotation)) {
//					addAnnotations.put(annotation, new Position(lineStart, lineLength));
//				} else {
//					
//					// remove as used - the remainder will be deleted
//					modelAnnotations.remove(annotation);
//					
//					boolean updatePosition = false;
//					Position pos = annotationModel.getPosition(annotation);
//					if (pos.getOffset() != lineStart) {
//						pos.setOffset(lineStart);
//						updatePosition = true;
//					}
//					if (pos.getLength() != lineLength) {
//						pos.setLength(lineLength);
//						updatePosition = true;
//					}
//					
//					if (updatePosition) {
//						((IAnnotationModelExtension) annotationModel).modifyAnnotationPosition(annotation, pos);
//					}
//				}
//			
//			} catch (BadLocationException ex) {
//				// ignore bad location
//			}
//			
//			
////			Map<String, Object> markerAttrs = new HashMap<String, Object>();
////			
////			markerAttrs.put(IMarker.LINE_NUMBER, line);
////			markerAttrs.put(IMarker.SEVERITY, 2);
////			
////			if (document != null) {
////				try {
////					int lineOffset = document.getLineOffset(line);
////					int lineLength = document.getLineLength(line);
////					
////					markerAttrs.put(IMarker.CHAR_START, lineOffset);
////					markerAttrs.put(IMarker.CHAR_END, lineOffset + lineLength);
////				} catch (BadLocationException ex) {
////					ex.printStackTrace();
////				}
////			}
////			
////			MarkerUtilities.createMarker(markerResource, markerAttrs, COMMAND_STATUS_MARKER);
//			
////			markerResource.findMarkers(type, includeSubtypes, depth);
//		}
//		
//		((IAnnotationModelExtension) annotationModel).replaceAnnotations(
//				modelAnnotations.toArray(new Annotation[modelAnnotations.size()]), 
//				addAnnotations);
//	}
//	
//	@SuppressWarnings("unchecked")
//	private Set<Annotation> getModelLineAnnotations(IAnnotationModel annotationModel) {
//		Set<Annotation> modelAnnotations = new HashSet<Annotation>();
//		for (Iterator<Annotation> it = annotationModel.getAnnotationIterator(); it.hasNext(); ) {
//			Annotation annotation = it.next();
//			
//			if (LINE_ANNOTATIONS.contains(annotation.getType())) {
//				modelAnnotations.add(annotation);
//			}
//		}
//		
//		return modelAnnotations;
//	}
//	
//	private Annotation getLineAnnotation(int line) {
//		Integer lineNo = new Integer(line);
//		Annotation annotation = lineAnnotations.get(lineNo);
//		
//		if (annotation == null) {
//			annotation = new Annotation(false);
//			lineAnnotations.put(lineNo, annotation);
//		}
//		
//		return annotation;
//	}
}