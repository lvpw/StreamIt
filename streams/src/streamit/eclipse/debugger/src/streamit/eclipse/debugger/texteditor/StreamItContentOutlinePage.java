package streamit.eclipse.debugger.texteditor;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

/**
 * @author kkuo
 */
public class StreamItContentOutlinePage extends ContentOutlinePage {

	private Object fInput;
	private IDocumentProvider fDocumentProvider;
	private ITextEditor fTextEditor;
    
    /**
     * Creates a content outline page using the given provider and the given 
     * editor.
     */
    public StreamItContentOutlinePage(IDocumentProvider provider, ITextEditor editor) {
		super();
		fDocumentProvider = provider;
		fTextEditor = editor;
    }
	
    /* (non-StreamItdoc)
     * Method declared on ContentOutlinePage
     */
    public void createControl(Composite parent) {
		super.createControl(parent);
		TreeViewer viewer = getTreeViewer();
		viewer.setContentProvider(new StreamItContentProvider(fDocumentProvider, fTextEditor.getEditorInput()));
		viewer.setLabelProvider(new StreamItLabelProvider());
		viewer.addSelectionChangedListener(this);
		
		if (fInput != null) viewer.setInput(fInput);
    }
	    
    /* (non-StreamItdoc)
     * Method declared on ContentOutlinePage
     */
    public void selectionChanged(SelectionChangedEvent event) {
		super.selectionChanged(event);
		
		ISelection selection = event.getSelection();
		if (selection.isEmpty()) {
			fTextEditor.resetHighlightRange();
		} else {
		    StreamSegment s = (StreamSegment) ((IStructuredSelection) selection).getFirstElement();
		    int offset = s.getPosition().getOffset();
		    int length = s.getPosition().getLength();
		    try {
				fTextEditor.selectAndReveal(offset, length);
		    } catch (IllegalArgumentException x) {
				fTextEditor.resetHighlightRange();
		    }
		}
    }
	
    /**
     * Sets the input of the outline page
     */
    public void setInput(Object input) {
		fInput = input;
		update();
    }
	
    /**
     * Updates the outline page.
     */
    public void update() {
		TreeViewer viewer = getTreeViewer();
		
		if (viewer != null) {
		    Control control = viewer.getControl();
		    if (control != null && !control.isDisposed()) {
				control.setRedraw(false);
				viewer.setInput(fInput);
				viewer.expandAll();
				control.setRedraw(true);
		    }
		}
    }
}