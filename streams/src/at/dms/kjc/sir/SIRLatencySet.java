package at.dms.kjc.sir;

import java.util.Iterator;
import java.util.TreeSet;

import at.dms.kjc.AttributeVisitor;
import at.dms.kjc.KjcVisitor;
import at.dms.kjc.SLIRAttributeVisitor;
import at.dms.kjc.SLIRVisitor;

/**
 * A set of Integers corresponding to possible latencies for message
 * delivery.
 */
public class SIRLatencySet extends SIRLatency {
    /**
     * The entries in the set.
     */
    private TreeSet<Integer> entries;

    public SIRLatencySet() {
        this.entries = new TreeSet<Integer>();
    }

    // The following methods just mirror those of <entries>.  More can
    // be added if desired.
    
    public boolean add(Integer x) {
        return entries.add(x);
    }

    public Integer first() {
        return entries.first();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Iterator<Integer> iterator() {
        return entries.iterator();
    }

    public Integer last() {
        return entries.last();
    }

    public boolean remove(Integer x) {
        return entries.remove(x);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Accepts the specified attribute visitor.
     * @param   p               the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        if (p instanceof SLIRAttributeVisitor) {
            return ((SLIRAttributeVisitor)p).visitLatencySet(this);
        } else {
            return this;
        }
    }

    /**
     * Accepts the specified visitor.
     */
    @Override
	public void accept(KjcVisitor p) {
        if (p instanceof SLIRVisitor) {
            ((SLIRVisitor)p).visitLatencySet(this);
        } else {
            at.dms.util.Utils.fail("Use SLIR visitor to visit an SIR node.");
        }
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.sir.SIRLatencySet other = new at.dms.kjc.sir.SIRLatencySet();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.sir.SIRLatencySet other) {
        super.deepCloneInto(other);
        other.entries = (java.util.TreeSet)at.dms.kjc.AutoCloner.cloneToplevel(this.entries);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
