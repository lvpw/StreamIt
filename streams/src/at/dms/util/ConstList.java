package at.dms.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * This provides a constant interface to a list, where "constant"
 * means that elements can't be added or removed from the list.  They
 * can, however, be set to different elements.
 */
public class ConstList implements Serializable, Cloneable {

    protected LinkedList<Object> list;

    public ConstList() {
        list = new LinkedList<Object>();
    }

    public ConstList(LinkedList<Object> list) {
        this.list = list;
    }

    /** Returns true if this list contains the specified element.  */
    public boolean contains(Object o) {
        return list.contains(o);
    }

    /** Returns true if this list contains all of the elements of the specified collection. */
    public boolean containsAll(Collection c) {
        return list.containsAll(c);
    }
    
    /** Compares the specified object with this list for equality. */
    @Override
	public boolean equals(Object o) {
        return list.equals(o);
    }

    /** Returns the element at the specified position in this list. */
    public Object get(int index) {
        return list.get(index);
    }

    /** Returns the hash code value for this list. */
    @Override
	public int hashCode() {
        return list.hashCode();
    }

    /** Returns the index in this list of the first occurrence of
     * the specified element, or -1 if this list does not contain
     * this element. */
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    /** Returns true if this list contains no elements. */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /** Returns an iterator over the elements in this list in proper sequence. */
    public Iterator<Object> iterator() {
        return list.iterator();
    }

    /** Returns the index in this list of the last occurrence of the
     * specified element, or -1 if this list does not contain this
     * element. */
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }


    /** Returns a list iterator of the elements in this list (in proper sequence). */
    public ListIterator<Object> listIterator() {
        return list.listIterator();
    }


    /** Returns a list iterator of the elements in this list (in
     * proper sequence), starting at the specified position in this
     * list. */
    public ListIterator<Object> listIterator(int index) {
        return list.listIterator(index);
    }


    /** Replaces the element at the specified position in this list
     * with the specified element (optional operation). */
    public Object set(int index, Object element) {
        return list.set(index, element);
    }


    /** Returns the number of elements in this list. */
    public int size() {
        return list.size();
    }


    /** Returns an array containing all of the elements in this list in proper sequence. */
    public Object[] toArray() {
        return list.toArray();
    }

    /** Returns an array containing all of the elements in this list in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array. */
    public Object[] toArray(Object[] a) {
        return list.toArray(a);
    }

    @Override
	public Object clone() {
        ConstList result = new ConstList();
        result.list = (LinkedList<Object>)list.clone();
        return result;
    }
}
