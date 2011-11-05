package at.dms.kjc.smp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import at.dms.kjc.JArrayAccessExpression;
import at.dms.kjc.JBlock;
import at.dms.kjc.JExpression;
import at.dms.kjc.JFieldAccessExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.slir.StaticSubGraph;
import at.dms.kjc.slir.WorkNode;
import at.dms.kjc.slir.InputNode;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.OutputNode;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.WorkNodeInfo;
import at.dms.kjc.slir.fission.FissionGroup;
import at.dms.kjc.backendSupport.BasicSpaceTimeSchedule;

public class OutputRotatingBuffer extends RotatingBuffer {

    /** the name of the write rotation structure (always points to its head) */
    protected String writeRotStructName;
    /** the name of the pointer to the current write rotation of this buffer */
    protected String currentWriteRotName;
    /** the name of the pointer to the write buffer of the current rotation */
    protected String currentWriteBufName;
    
    /** the address buffers that this buffer rotation uses as destinations for transfers */ 
    protected HashMap<InputRotatingBuffer, SourceAddressRotation> addressBuffers;
    
    /** whether transmitter can write directly into a receiver's buffer */
    protected boolean directWrite;
    /** filter whose receiver this transmitter will directly write to */
    protected WorkNode directWriteFilter;
    
    /**
     * Create all the output buffers necessary for this slice graph.  Iterate over
     * the steady-state schedule, visiting each slice and creating an output buffer
     * for the filter of the slice
     * 
     * @param slices The steady-state schedule of slices
     */
    public static void createOutputBuffers(BasicSpaceTimeSchedule schedule) {

    	StaticSubGraph ssg = schedule.getSSG();
    	
        //for (Filter slice : schedule.getScheduleList()) {
    	for (Filter slice : ssg.getFilterGraph()) {	

//        	System.out.println("OutputRotatingBuffer.createOutputBuffers calling on slice=" + slice.getWorkNode().toString());

            if(KjcOptions.sharedbufs && FissionGroupStore.isFizzed(slice)) {
                assert FissionGroupStore.isUnfizzedSlice(slice);

                FissionGroup group = FissionGroupStore.getFissionGroup(slice);
                for(Filter fizzedSlice : group.fizzedSlices)
                    createOutputBuffer(fizzedSlice, schedule);
            }
            else {
                createOutputBuffer(slice, schedule);
            }
        }
    }

    public static void createOutputBuffer(Filter slice, BasicSpaceTimeSchedule schedule) {
                
        //don't do anything for file readers or writers,
        //for file readers the output buffer is allocated in ProcessFileReader
        if (slice.getInputNode().getNextFilter().isPredefined())
            return;
        
        if (!slice.getOutputNode().noOutputs()) {
            assert slice.getOutputNode().totalWeights(SchedulingPhase.STEADY) > 0;
            Core parent = SMPBackend.scheduler.getComputeNode(slice.getWorkNode());
            
            // create the new buffer, the constructor will put the buffer in the hashmap
            OutputRotatingBuffer buf = new OutputRotatingBuffer(slice.getWorkNode(), parent);
            
            buf.setRotationLength(schedule);
            buf.setBufferSize();
            buf.createInitCode();
        }
    }

    /**
     * Create a new output buffer that is associated with the filter node.
     * 
     * @param filterNode The filternode for which to create a new output buffer.
     */
    protected OutputRotatingBuffer(WorkNode filterNode, Core parent) {
        super(filterNode.getEdgeToNext(), filterNode, parent);
        
        bufType = filterNode.getFilter().getOutputType();

        checkDirectWrite();
//		if(directWrite)
//			System.out.println(filterNode + " has directWrite to " + directWriteFilter);
//		else
//			System.out.println(filterNode + " does not have directWrite");
        
//         if(directWrite)
//             this.ident = InputRotatingBuffer.getInputBuffer(directWriteFilter).getIdent();

        writeRotStructName =  this.getIdent() + "write_rot_struct";
        currentWriteRotName = this.getIdent() + "_write_current";
        currentWriteBufName = this.getIdent() + "_write_buf";
        
        setOutputBuffer(filterNode, this);
    }
    
    private void checkDirectWrite() {
		directWrite = false;
		directWriteFilter = null;
		
		// Get receivers that receive all outputs of this transmitter in steady-state
		// In other words, receivers that appear in every weight of this filter's OutputSliceNode
		
		InterFilterEdge steadyDests[][] = filterNode.getParent().getOutputNode().getDests(SchedulingPhase.STEADY);
		Set<InterFilterEdge> steadyDestSet = filterNode.getParent().getOutputNode().getDestSet(SchedulingPhase.STEADY);
		Set<InterFilterEdge> candidateDestsSteady = new HashSet<InterFilterEdge>();
		
		for(InterFilterEdge edge : steadyDestSet) {
			boolean recvAllOutputs = true;
			
			for(int x = 0 ; x < steadyDests.length ; x++) {
				boolean recvOutput = false;
				
				for(int y = 0 ; y < steadyDests[x].length ; y++) {
					if(steadyDests[x][y].equals(edge)) {
						recvOutput = true;
						break;
					}
				}
				
				if(!recvOutput) {
					recvAllOutputs = false;
					break;
				}
			}
			
			if(recvAllOutputs) {
				candidateDestsSteady.add(edge);
			}
		}
		
		if(candidateDestsSteady.isEmpty()) {
			//System.out.println("  failed 1");
			return;
		}
		
		// Get receivers that receive everything or do not receive anything from
		// this transmitter in initialization
		
		InterFilterEdge initDests[][] = filterNode.getParent().getOutputNode().getDests(SchedulingPhase.INIT);
		Set<InterFilterEdge> initDestsSet = filterNode.getParent().getOutputNode().getDestSet(SchedulingPhase.INIT);
		Set<InterFilterEdge> candidateDestsInit = new HashSet<InterFilterEdge>();
		
		for(InterFilterEdge edge : initDestsSet) {
			boolean recvAllOutputs = true;
			
			for(int x = 0 ; x < initDests.length ; x++) {
				boolean recvOutput = false;
				
				for(int y = 0 ; y < initDests[x].length ; y++) {
					if(initDests[x][y].equals(edge)) {
						recvOutput = true;
						break;
					}
				}
				
				if(!recvOutput) {
					recvAllOutputs = false;
					break;
				}
			}
			
			if(recvAllOutputs) {
				candidateDestsInit.add(edge);
			}
		}
		
		for(InterFilterEdge edge : initDestsSet) {
			boolean recvNoOutputs = true;
			
			for(int x = 0 ; x < initDests.length ; x++) {
				boolean recvOutput = false;
				
				for(int y = 0 ; y < initDests[x].length ; y++) {
					if(initDests[x][y].equals(edge)) {
						recvOutput = true;
						break;
					}
				}
				
				if(recvOutput) {
					recvNoOutputs = false;
					break;
				}
			}
			
			if(recvNoOutputs) {
				candidateDestsInit.add(edge);
			}
		}
		
		if(!initDestsSet.isEmpty() && candidateDestsInit.isEmpty()) {
			//System.out.println("  failed 2");
			return;
		}
		
		// Intersect candidate receivers from steady-state and initialization
		Set<InterFilterEdge> candidateDests = new HashSet<InterFilterEdge>();
		
		if(!initDestsSet.isEmpty()) {
			for(InterFilterEdge edge : candidateDestsSteady)
				if(candidateDestsInit.contains(edge))
					candidateDests.add(edge);
		}
		else {
			candidateDests.addAll(candidateDestsSteady);
		}
		
		if(candidateDests.isEmpty()) {
			//System.out.println("  failed 3");
			return;
		}
		
		// Take only candidate receivers that are single appearance
		Set<InterFilterEdge> saCandidateDests = new HashSet<InterFilterEdge>();
		
		for(InterFilterEdge edge : candidateDests)
			if(edge.getDest().singleAppearance())
				saCandidateDests.add(edge);
			
		if(saCandidateDests.isEmpty()) {
			//System.out.println("  failed 4");
			return;
		}
		
		// Check that schedules for OutputSliceNode and InputSliceNode are executed only once
		Set<InterFilterEdge> finalCandidateDests = new HashSet<InterFilterEdge>();
		
		for(InterFilterEdge edge : saCandidateDests) {
			WorkNodeInfo consumer = WorkNodeInfo.getFilterInfo(edge.getDest().getNextFilter());
			WorkNodeInfo producer = WorkNodeInfo.getFilterInfo(edge.getSrc().getPrevFilter());
		
	        if ((edge.getDest().getWidth(SchedulingPhase.INIT) > 1 && 
	                edge.getDest().totalWeights(SchedulingPhase.INIT) != consumer.totalItemsPopped(SchedulingPhase.INIT)) ||
	            (edge.getDest().getWidth(SchedulingPhase.STEADY) > 1 &&       
	                        edge.getDest().totalWeights(SchedulingPhase.STEADY) != consumer.totalItemsPopped(SchedulingPhase.STEADY)) ||
	            (edge.getSrc().getWidth(SchedulingPhase.INIT) > 1 &&   
	                edge.getSrc().totalWeights(SchedulingPhase.INIT) != producer.totalItemsSent(SchedulingPhase.INIT)) ||
	            (edge.getSrc().getWidth(SchedulingPhase.STEADY) > 1 &&   
	                edge.getSrc().totalWeights(SchedulingPhase.STEADY) != producer.totalItemsSent(SchedulingPhase.STEADY)))
	        {
	        	continue;
	        }
	        
	        finalCandidateDests.add(edge);
		}
		
		if(finalCandidateDests.isEmpty()) {
			//System.out.println("  failed 6");
			return;
		}
		
		//System.out.println("Number of final candidates for directWrite: " + finalCandidateDests.size());
		
		// Pick one of the candidates receivers to directly write to
		directWrite = true;
		directWriteFilter = finalCandidateDests.iterator().next().getDest().getNextFilter();
    }
    
    public boolean hasDirectWrite() {
    	return directWrite;
    }
    
    public WorkNode getDirectWriteFilter() {
    	return directWriteFilter;
    }
    
    /**
     * Set the rotation length of this rotating buffer
     */
    public void setRotationLength(BasicSpaceTimeSchedule schedule) {
        //calculate the rotation length

        int srcMult;
        if(KjcOptions.sharedbufs && FissionGroupStore.isFizzed(filterNode.getParent())) {
            srcMult = schedule.getPrimePumpMult(FissionGroupStore.getUnfizzedSlice(filterNode.getParent()));
        }
        else {
            srcMult = schedule.getPrimePumpMult(filterNode.getParent());
        }

        //first find the max rotation length given the prime pump 
        //mults of all the sources
        int maxRotLength = 0;
        for (Filter dest : filterNode.getParent().getOutputNode().getDestSlices(SchedulingPhase.STEADY)) {
            int diff = srcMult - schedule.getPrimePumpMult(dest);
            assert diff >= 0;
            if (diff > maxRotLength)
                maxRotLength = diff;
        }

        rotationLength = maxRotLength + 1;
    }
    
    protected void setBufferSize() {
        if(KjcOptions.sharedbufs && FissionGroupStore.isFizzed(filterNode.getParent())) {
            FissionGroup group = FissionGroupStore.getFissionGroup(filterNode.getParent());

            if(LoadBalancer.isLoadBalanced(filterNode.getParent())) {
                bufSize = Math.max(group.unfizzedFilterInfo.totalItemsSent(SchedulingPhase.INIT),
                                   group.unfizzedFilterInfo.totalItemsSent(SchedulingPhase.STEADY));
            }
            else {
                bufSize = Math.max(group.unfizzedFilterInfo.totalItemsSent(SchedulingPhase.INIT) /
                                   group.fizzedSlices.length,
                                   group.unfizzedFilterInfo.totalItemsSent(SchedulingPhase.STEADY) /
                                   group.fizzedSlices.length);
            }
        }
        else {
            WorkNodeInfo fi = WorkNodeInfo.getFilterInfo(filterNode);
        
            bufSize = Math.max(fi.totalItemsSent(SchedulingPhase.INIT),
                               fi.totalItemsSent(SchedulingPhase.STEADY));
        }
    }

    /**
     * Allocate the constituent buffers of this rotating buffer structure
     */
    protected void allocBuffers() {
        if(directWrite)
            return;

        super.allocBuffers();
    }
    
    public void createAddressBuffers() {
    	//fill the addressBuffers array
    	OutputNode outputNode = filterNode.getParent().getOutputNode();
    	
        addressBuffers = new HashMap<InputRotatingBuffer, SourceAddressRotation>();
        for (InterFilterEdge edge : outputNode.getDestSet(SchedulingPhase.STEADY)) {
            InputRotatingBuffer input = InputRotatingBuffer.getInputBuffer(edge.getDest().getNextFilter());
            addressBuffers.put(input, input.getAddressRotation(filterNode));
        }
    }
    
    /**
     * Return the address rotation that this output rotation uses for the given input slice node
     * 
     * @param input the input slice node 
     * @return the dma address rotation used to store the address of the 
     * rotation associated with this input slice node
     */
    public SourceAddressRotation getAddressBuffer(InputNode input) {
        assert addressBuffers.containsKey(InputRotatingBuffer.getInputBuffer(input.getNextFilter())) ;
        return addressBuffers.get(InputRotatingBuffer.getInputBuffer(input.getNextFilter()));
    }
    
    /** Create an array reference given an offset */   
    public JFieldAccessExpression writeBufRef() {
        return new JFieldAccessExpression(new JThisExpression(), currentWriteBufName);
    }
    
    /** Create an array reference given an offset */   
    public JArrayAccessExpression readBufRef(JExpression offset) {
    	assert(false);
    	return null;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#endInitWrite()
     */
    public List<JStatement> endInitWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
        //in the init stage we use dma to send the output to the dest filter
        //but we have to wait until the end because are not double buffering
        //also, don't rotate anything here
        list.addAll(transferCommands.writeTransferCommands(SchedulingPhase.INIT));
        return list;
    }
    
    /**
     *  We don't want to transfer during the first execution of the primepump
     *  so guard the execution in an if statement.
     */
    public List<JStatement> beginPrimePumpWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
                
        list.add(transferCommands.zeroOutHead(SchedulingPhase.PRIMEPUMP));

        return list;
    }

    public List<JStatement> endPrimePumpWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();

        //add the transfer commands for the data that was just computed
        list.addAll(transferCommands.writeTransferCommands(SchedulingPhase.STEADY));
        //generate the rotate statements for this output buffer
        list.addAll(rotateStatementsCurRot());
        //generate the rotate statements for the address buffers
        for (SourceAddressRotation addrRot : addressBuffers.values()) {
            list.addAll(addrRot.rotateStatements());
        }

        return list;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#beginSteadyWrite()
     */
    public List<JStatement> beginSteadyWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
        list.add(transferCommands.zeroOutHead(SchedulingPhase.STEADY));
        return list;
    }
    
    /**
     * The rotate statements that includes the current buffer (for output of this 
     * firing) and transfer buffer.
     */
    protected List<JStatement> rotateStatements() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
        list.addAll(rotateStatementsCurRot());
        return list;
    }

    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#endSteadyWrite()
     */
    public List<JStatement> endSteadyWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
        
        list.addAll(transferCommands.writeTransferCommands(SchedulingPhase.STEADY));
        
        //generate the rotate statements for this output buffer
        list.addAll(rotateStatements());
        
        //generate the rotation statements for the address buffers that this output
        //buffer uses
        for (SourceAddressRotation addrRot : addressBuffers.values()) {
            list.addAll(addrRot.rotateStatements());
        }
        return list;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#writeDecls()
     */
    public List<JStatement> writeDecls() {
        List<JStatement> retval = new LinkedList<JStatement>();
        retval.addAll(transferCommands.writeDecls());
        return retval;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#popMethodName()
     */
    public String popMethodName() {
        assert false : "Should not call pop() method on output buffer.";
        return "";
    }

    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#popMethod()
     */
    public JMethodDeclaration popMethod() {
        assert false : "Should not call pop() method on output buffer.";
        return null;
    }    
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#popManyMethodName()
     */
    public String popManyMethodName() {
        assert false : "Should not call pop() method on output buffer.";
        return "";
    }
 
        /**
     * Pop many items at once ignoring them.
     * Default method generated here to call popMethod() repeatedly.
     */
    public JMethodDeclaration popManyMethod() {
        assert false : "Should not call pop() method on output buffer.";
        return null;
     }

    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#assignFromPopMethodName()
     */
    public String assignFromPopMethodName() {
        assert false : "Should not call pop() method on output buffer.";
        return "";
    }
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#assignFromPopMethod()
     */
    public JMethodDeclaration assignFromPopMethod() {
        assert false : "Should not call pop() method on output buffer.";
        return null;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#peekMethodName()
     */
    public String peekMethodName() {
        assert false : "Should not call peek() method on output buffer.";
        return "";
    }
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#peekMethod()
     */
    public JMethodDeclaration peekMethod() {
        assert false : "Should not call peek() method on output buffer.";
        return null;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#assignFromPeekMethodName()
     */
    public String assignFromPeekMethodName() {
        assert false : "Should not call peek() method on output buffer.";
        return "";
    }
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#assignFromPeekMethod()
     */
    public JMethodDeclaration assignFromPeekMethod() {
        assert false : "Should not call peek() method on output buffer.";
        return null;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#pushMethodName()
     */
    public String pushMethodName() {
        return "__push_" + unique_id;
        //return "__push" + this.getIdent();
    }
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#pushMethod()
     */
    public JMethodDeclaration pushMethod() {
        return transferCommands.pushMethod();
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#beginInitWrite()
     */
    public List<JStatement> beginInitWrite() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();
        if (WorkNodeInfo.getFilterInfo(filterNode).totalItemsSent(SchedulingPhase.INIT) > 0)
            list.add(transferCommands.zeroOutHead(SchedulingPhase.INIT));   
        return list;
    }

    protected List<JStatement> rotateStatementsCurRot() {
        LinkedList<JStatement> list = new LinkedList<JStatement>();

        if(!directWrite) {
            list.add(Util.toStmt(currentWriteRotName + " = " + currentWriteRotName + "->next"));
            list.add(Util.toStmt(currentWriteBufName + " = " + currentWriteRotName + "->buffer"));
        }

        return list;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#topOfWorkSteadyWrite()
     */
    public List<JStatement> topOfWorkSteadyWrite() {
        return new LinkedList<JStatement>(); 
    }
 
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#dataDeclsH()
     */
    public List<JStatement> dataDeclsH() {
        return new LinkedList<JStatement>();
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#dataDecls()
     */
    public List<JStatement> dataDecls() {
        List<JStatement> retval = new LinkedList<JStatement>();
        return retval;
    }
    
    /* (non-Javadoc)
     * @see at.dms.kjc.backendSupport.ChannelI#writeDeclsExtern()
     */
    public List<JStatement> writeDeclsExtern() {
        return new LinkedList<JStatement>();
    }   

    /**
     * Generate the code to setup the structure of the rotating buffer 
     * as a circular linked list.
     */
    protected void setupRotation() {
        if(directWrite)
            return;

        String temp = "__temp__";
        SMPComputeCodeStore cs = parent.getComputeCode();
        //this is the typedef we will use for this buffer rotation structure
        String rotType = rotTypeDefPrefix + getType().toString();
        
        //add the declaration of the rotation buffer of the appropriate rotation type
        parent.getComputeCode().appendTxtToGlobal(rotType + " *" + writeRotStructName + ";\n");
        //add the declaration of the pointer that points to the current rotation in the rotation structure
        parent.getComputeCode().appendTxtToGlobal(rotType + " *" + currentWriteRotName + ";\n");
        //add the declaration of the pointer that points to the current buffer in the current rotation
        parent.getComputeCode().appendTxtToGlobal(bufType.toString() + " *" + currentWriteBufName + ";\n");

        JBlock block = new JBlock();
        
        //create a temp var
        if (this.rotationLength > 1)
            block.addStatement(Util.toStmt(rotType + " *" + temp));
        
        //create the first entry!!
        block.addStatement(Util.toStmt(writeRotStructName + " =  (" + rotType+ "*)" + "malloc(sizeof("
                + rotType + "))"));
        
        //modify the first entry
        block.addStatement(Util.toStmt(writeRotStructName + "->buffer = " + bufferNames[0]));
        if (this.rotationLength == 1)  {
            //loop the structure
            block.addStatement(Util.toStmt(writeRotStructName + "->next = " + writeRotStructName));
        }
        else {
            block.addStatement(Util.toStmt(temp + " = (" + rotType+ "*)" + "malloc(sizeof("
                    + rotType + "))"));    
            
            block.addStatement(Util.toStmt(writeRotStructName + "->next = " + 
                    temp));
            
            block.addStatement(Util.toStmt(temp + "->buffer = " + bufferNames[1]));
            
            for (int i = 2; i < this.rotationLength; i++) {
                block.addStatement(Util.toStmt(temp + "->next =  (" + rotType+ "*)" + "malloc(sizeof("
                        + rotType + "))"));
                block.addStatement(Util.toStmt(temp + " = " + temp + "->next"));
                block.addStatement(Util.toStmt(temp + "->buffer = " + bufferNames[i]));
            }
            
            block.addStatement(Util.toStmt(temp + "->next = " + writeRotStructName));
        }
        block.addStatement(Util.toStmt(currentWriteRotName + " = " + writeRotStructName));
        block.addStatement(Util.toStmt(currentWriteBufName + " = " + currentWriteRotName + "->buffer"));

        cs.addStatementToBufferInit(block);
    }

}
