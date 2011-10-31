/**
 * 
 */
package at.dms.kjc.backendSupport;

import at.dms.kjc.slir.Edge;
import at.dms.kjc.slir.InterSSGEdge;

/**
 * @author soule
 *
 */
public abstract class Channel<E extends Edge<?, ?> > {

    protected E theEdge;
	
    protected Channel(E edge) {
        assert edge != null;
        this.theEdge = edge;
    }
    
    public E getEdge() {
		return theEdge;
	}

	/**
	 * @return
	 */
	public String peekMethodName() {
		assert false;
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @return
	 */
	public String popMethodName() {
		// TODO Auto-generated method stub
		assert false;		
		return null;
	}

	/**
	 * @return
	 */
	public String popManyMethodName() {
		assert false;
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @return
	 */
	public String pushMethodName() {
		assert false;
		// TODO Auto-generated method stub
		return null;
	}

	
    
}
