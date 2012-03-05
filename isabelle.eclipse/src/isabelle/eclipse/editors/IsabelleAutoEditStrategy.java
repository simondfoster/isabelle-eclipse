package isabelle.eclipse.editors;

import isabelle.scala.SessionFacade;
import isabelle.scala.SessionFacade.CompletionInfo;
import isabelle.scala.SessionFacade.DecodedCompletion;

import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

public class IsabelleAutoEditStrategy implements IAutoEditStrategy {

	private final TheoryEditor editor;

	public IsabelleAutoEditStrategy(TheoryEditor editor) {
		this.editor = editor;
	}
	
	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		
		SessionFacade session = editor.getIsabelleSession();
		if (session == null) {
			return;
		}
		
		System.out.println("Auto edit for " + command.text + " at " + command.offset);
		
		try {
			
			int offset = command.offset;
			int line = document.getLineOfOffset(offset);
			int start = document.getLineOffset(line);
			String text = document.get(start, offset - start);

			// add the command changes
			text = text + command.text;
			
			CompletionInfo completionResult = session.getCompletion(text, true);
			if (completionResult == null) {
				return;
			}
				
			List<DecodedCompletion> completions = completionResult.getCompletions();
			if (completions.size() != 1) {
				// more than one completion or none - cannot replace automatically
				return;
			}

			String word = completionResult.getWord();
			if (!text.endsWith(word)) {
				// the completion does not correspond to the typed thing
				return;
			}
			
			if (command.text.length() > word.length()) {
				// added text is more than the word, so we still need to insert
				// but with replacement of end of the insertion - do not support
				// at the moment
				return;
			}
			
			// the word has been written - replace with appropriate construct
			DecodedCompletion completion = completions.get(0);
			String replaceStr = completion.getDecoded();
			command.offset = offset + command.text.length() - word.length();
			command.length = word.length() - command.text.length();
			command.text = replaceStr;

		} catch (BadLocationException e) {
		}
		
//		String text= command.text;
//		if (text == null)
//			return;
//
//		int index= text.indexOf('\t');
//		if (index > -1) {
//
//			StringBuffer buffer= new StringBuffer();
//
//			fLineTracker.set(command.text);
//			int lines= fLineTracker.getNumberOfLines();
//
//			try {
//
//				for (int i= 0; i < lines; i++) {
//
//					int offset= fLineTracker.getLineOffset(i);
//					int endOffset= offset + fLineTracker.getLineLength(i);
//					String line= text.substring(offset, endOffset);
//
//					int position= 0;
//					if (i == 0) {
//						IRegion firstLine= document.getLineInformationOfOffset(command.offset);
//						position= command.offset - firstLine.getOffset();
//					}
//
//					int length= line.length();
//					for (int j= 0; j < length; j++) {
//						char c= line.charAt(j);
//						if (c == '\t') {
//							position += insertTabString(buffer, position);
//						} else {
//							buffer.append(c);
//							++ position;
//						}
//					}
//
//				}
//
//				command.text= buffer.toString();
//
//			} catch (BadLocationException x) {
//			}
//		}

	}

}