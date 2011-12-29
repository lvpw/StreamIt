package at.dms.kjc.sir;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import at.dms.kjc.CType;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.lir.LIRStreamType;

/**
 * This represents a pipeline of stream structures, as would be
 * declared with a Stream construct in StreaMIT.
 */
public class SIRPipeline extends SIRContainer implements Cloneable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 2777399058529155196L;

	/**
     * No argument constructor, FOR AUTOMATIC CLONING ONLY.
     */
    private SIRPipeline() {
        super();
    }
    
    /**
     * Construct a new SIRPipeline with the given fields and methods.
     */
    public SIRPipeline(SIRContainer parent,
                       String ident,
                       JFieldDeclaration[] fields,
                       JMethodDeclaration[] methods) {
        super(parent, ident, fields, methods);
    }

    /**
     * Construct a new SIRPipeline with empty fields and methods.
     */
    public SIRPipeline(SIRContainer parent,
                       String ident) {
        this(parent, ident, JFieldDeclaration.EMPTY(), JMethodDeclaration.EMPTY() );
    }

    /**
     * Construct a new SIRPipeline with no parent and empty fields and methods.
     */
    public SIRPipeline(String ident) {
        this(null, ident);
    }

    /**
     * Returns the output type of this.
     */
    @Override
	public CType getOutputType() {
        // output type is output type of last element in list
        return get(size()-1).getOutputType();
    }
    
    /**
     * Returns the input type of this.
     */
    @Override
	public CType getInputType() {
        // input type is input type of first element of the list
        return get(0).getInputType();
    }
    
    /**
     * Returns the type of this stream.
     */
    @Override
	public LIRStreamType getStreamType() {
        return LIRStreamType.LIR_PIPELINE;
    }

    @Override
	public int getPushForSchedule(Map<SIROperator, int[]>[] counts) {
        // the pipeline pushes what the last element pushes
        return get(size()-1).getPushForSchedule(counts);
    }

    @Override
	public int getPopForSchedule(Map<SIROperator, int[]>[] counts) {
        // the pipeline pops what the first item pops
        return get(0).getPopForSchedule(counts);
    }

    /**
     * Returns a list of the children of this 
     * Use instead of getChildren if want to get List<SIRStream> rather than List<SIROperator>   
     */
    public List<SIRStream> getSequentialStreams() {
        List<SIRStream> result = new LinkedList<SIRStream>();
        for (int i=0; i<size(); i++) {
            result.add(get(i));
        }
        return result;
    }

    /**
     * Returns a list of the children between <first> and <last>,
     * inclusive.  Assumes that <first> and <last> are both contained
     * in this, and that <first> comes before <last>.
     */
    public List<Object> getChildrenBetween(SIROperator first, SIROperator last) {
        assert myChildren().contains(first):
            "first=" + first.getName() + " is not a child of " + this;
        assert myChildren().contains(last):
            "last=" + last.getName() + " is not a child of " + this;
        // make result
        LinkedList<Object> result = new LinkedList<Object>();
        // start iterating through children at <first>
        ListIterator<Object> iter = myChildren().listIterator(myChildren().indexOf(first));
        Object o;
        do {
            // get next child and add to result list
            o = iter.next();
            result.add(o);
            // quit when we've added the last one
        } while (o!=last);
        // return result
        return result;
    }

    /**
     * Sets children of this to be all the children of <children>, and
     * set all the parent fields in <children> to be this.
     */
    public void setChildren(List<SIRStream> children) {
        clear();
        for (int i=0; i<children.size(); i++) {
            add(children.get(i));
        }
    }

    /**
     * Returns a list of tuples (two-element arrays) of SIROperators,
     * representing a tape from the first element of each tuple to the
     * second.
     */
    @Override
	public List<SIROperator[]> getTapePairs() {
        // construct result
        LinkedList<SIROperator[]> result = new LinkedList<SIROperator[]>();
        // go through list of children
        for (int i=0; i<size()-1; i++) {
            // make an entry from one stream to next
            SIROperator[] entry = { get(i), get(i+1) };
            // add entry 
            result.add(entry);
        }
        // return result
        return result;
    }

    /**
     * See documentation in SIRContainer.
     */
    @Override
	public void replace(SIRStream oldStr, SIRStream newStr) {
        int index = myChildren().indexOf(oldStr);
        assert index!=-1:
            "Trying to replace with bad parameters, since " + this +
            " doesn't contain " + oldStr;
        myChildren().set(index, newStr);
        // set parent of new stream
        newStr.setParent(this);
    }

    /**
     * Replaces the sequence of <start> ... <end> within this pipeline with
     * the single stream <newStream>.  Requires that <start> and <end> are
     * both in this, with <start> coming before <end>.
     */
    public void replace(SIRStream start, SIRStream end, SIRStream newStream) {
        // get positions of start and ending streams
        int index1 = myChildren().indexOf(start);
        int index2 = myChildren().indexOf(end);
        assert index1!=-1 && index1!=-1 && index1 <= index2:
            "Trying to replace with bad parameters, from start at " +
            "position " + index1 + " to end at position " + index2;
        // remove the old streams
        for (int i=index1; i<=index2; i++) {
            remove(index1);
        }
        // add the new stream
        add(index1, newStream);
    }

    /**
     * Accepts attribute visitor <v> at this node.
     */
    @Override
	public Object accept(AttributeStreamVisitor v) {
        return v.visitPipeline(this,
                               fields,
                               methods,
                               init);
    }

    @Override
	public String toString() {
        return "SIRPipeline name=" + getName();
    }


    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.sir.SIRPipeline other = new at.dms.kjc.sir.SIRPipeline();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.sir.SIRPipeline other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}

