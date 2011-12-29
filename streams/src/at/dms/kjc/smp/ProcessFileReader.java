package at.dms.kjc.smp;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import at.dms.kjc.CClassType;
import at.dms.kjc.CStdType;
import at.dms.kjc.JBlock;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.slir.FileInputContent;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.OutputNode;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.WorkNode;

public class ProcessFileReader {
    
    protected WorkNode filterNode;
    protected SchedulingPhase phase;
    protected SMPBackEndFactory factory;
    protected SMPComputeCodeStore codeStore;
    protected FileInputContent fileInput;
    protected Core allocatingCore;
    protected OutputNode fileOutput;

    protected static HashMap<WorkNode, Core> allocatingCores;

    protected static HashMap<FileReaderCodeKey, FileReaderCode> fileReaderCodeStore;
    protected static HashMap<FileReaderCodeKey, JMethodDeclaration> PPMethodStore;

    protected static HashSet<String> fileNames;
    
    static {
        allocatingCores = new HashMap<WorkNode, Core>();

        fileReaderCodeStore = new HashMap<FileReaderCodeKey, FileReaderCode>();
        PPMethodStore = new HashMap<FileReaderCodeKey, JMethodDeclaration>();

        fileNames = new HashSet<String>();
    }
    
    public ProcessFileReader (WorkNode filter, SchedulingPhase phase, SMPBackEndFactory factory) {
        this.filterNode = filter;
        this.fileInput = (FileInputContent)filter.getWorkNodeContent();
        this.phase = phase;
        this.factory = factory;
        this.allocatingCore = nextAllocatingCore();
        this.fileOutput = filter.getParent().getOutputNode();
        codeStore = allocatingCore.getComputeCode();
    }
     
    public void processFileReader() {
    	System.out.println("ProcessFileReader.processFileReader");
        if (phase == SchedulingPhase.INIT) {
            fileNames.add(fileInput.getFileName());
            allocateAndCommunicateAddrs();
        }

        System.out.print("Generating FileReader code: ");
        for (InterFilterEdge edge : fileOutput.getDestSet(SchedulingPhase.STEADY)) {
            if(KjcOptions.sharedbufs && FissionGroupStore.isFizzed(edge.getDest().getParent())) {
                for(Filter fizzedSlice : FissionGroupStore.getFizzedSlices(edge.getDest().getParent())) {
                    System.out.print(".");
                    generateCode(fizzedSlice.getWorkNode());
                }
            }
            else {
                System.out.print(".");
                generateCode(edge.getDest().getNextFilter());
            }
        }
        System.out.println();
    }

    private void generateCode(WorkNode dsFilter) {
        InputRotatingBuffer destBuf = InputRotatingBuffer.getInputBuffer(dsFilter);
        FileReaderCodeKey frcKey = new FileReaderCodeKey(filterNode, dsFilter);

        FileReaderCode fileReaderCode = fileReaderCodeStore.get(frcKey);
        if(fileReaderCode == null) {
            fileReaderCode = new FileReaderRemoteReads(destBuf);
            fileReaderCodeStore.put(frcKey, fileReaderCode);
        }
        
        SMPComputeCodeStore codeStore = SMPBackend.scheduler.getComputeNode(dsFilter).getComputeCode();
        
        switch (phase) {
        case INIT : generateInitCode(fileReaderCode, codeStore, destBuf); break;
        case PRIMEPUMP : generatePPCode(dsFilter, fileReaderCode, codeStore, destBuf); break;
        case STEADY : generateSteadyCode(fileReaderCode, codeStore, destBuf); break;
        }
    }
    
    private void generateInitCode(FileReaderCode fileReaderCode, SMPComputeCodeStore codeStore, 
            InputRotatingBuffer destBuf) {

        JBlock statements = new JBlock(fileReaderCode.commandsInit);

        //create a method 
        JMethodDeclaration initMethod = new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
                CStdType.Void,
                "__File_Reader_Init__" + fileReaderCode.getID() + "__",
                JFormalParameter.EMPTY,
                CClassType.EMPTY,
                statements,
                null,
                null);

        codeStore.addMethod(initMethod);
        codeStore.addInitStatement(new JExpressionStatement(null,
                new JMethodCallExpression(null, new JThisExpression(null),
                        initMethod.getName(), new JExpression[0]), null));
    }

    private void generatePPCode(WorkNode node, FileReaderCode fileReaderCode, SMPComputeCodeStore codeStore, 
            InputRotatingBuffer destBuf) {

        FileReaderCodeKey frcKey = new FileReaderCodeKey(filterNode, node);

        if (!PPMethodStore.containsKey(frcKey)) {
            JBlock statements = new JBlock(fileReaderCode.commandsSteady);

            //create a method 
            JMethodDeclaration ppMethod = new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
                    CStdType.Void,
                    "__File_Reader_PrimePump__" + fileReaderCode.getID() + "__",
                    JFormalParameter.EMPTY,
                    CClassType.EMPTY,
                    statements,
                    null,
                    null);

            codeStore.addMethod(ppMethod);
            PPMethodStore.put(frcKey, ppMethod);
        }

        codeStore.addInitStatement(new JExpressionStatement(null,
                new JMethodCallExpression(null, new JThisExpression(null),
                        PPMethodStore.get(frcKey).getName(), new JExpression[0]), null));
    }
    
    private void generateSteadyCode(FileReaderCode fileReaderCode, SMPComputeCodeStore codeStore, 
            InputRotatingBuffer destBuf) {
        
        JBlock steadyBlock = new JBlock(fileReaderCode.commandsSteady);
        
        codeStore.addSteadyLoopStatement(steadyBlock);
    }
    
    private void allocateAndCommunicateAddrs() {
        long fileSize = getFileSizeBytes();
        JBlock block = new JBlock();

        codeStore.appendTxtToGlobal("FILE *input;\n");
        codeStore.appendTxtToGlobal("off_t num_inputs;\n");
        codeStore.appendTxtToGlobal(fileInput.getType() + "*fileReadBuffer;\n");
        //codeStore.appendTxtToGlobal("int fileReadIndex__n" + codeStore.getParent().getUniqueId() + " = 0;\n");

        SMPBackend.chip.getOffChipMemory().getComputeCode().appendTxtToGlobal("extern " + fileInput.getType() + "*fileReadBuffer;\n");
        SMPBackend.chip.getOffChipMemory().getComputeCode().appendTxtToGlobal("extern off_t num_inputs;\n");

        //open the file read the file into the buffer on the heap
        block.addStatement(Util.toStmt("struct stat statbuf"));
        block.addStatement(Util.toStmt("stat(\"" + fileInput.getFileName() + "\", &statbuf)"));
        block.addStatement(Util.toStmt("num_inputs = statbuf.st_size / " + fileInput.getType().getSizeInC()));
        block.addStatement(Util.toStmt("fileReadBuffer = (" + fileInput.getType() + " *)malloc(statbuf.st_size)"));
        block.addStatement(Util.toStmt("input = fopen(\"" + fileInput.getFileName() + "\", \"r\")"));
        block.addStatement(Util.toStmt("if(fread((void *)fileReadBuffer, " + fileInput.getType().getSizeInC() + ", num_inputs, input) != num_inputs)" +
        								"printf(\"Error reading %lu bytes of input file\\n\", (unsigned long)statbuf.st_size)"));

        /*
        for (Core other : SMPBackend.chip.getCores()) {
            if (codeStore.getParent() == other) 
                continue;

            other.getComputeCode().appendTxtToGlobal("int fileReadIndex__n" + other.getUniqueId() + " = 0;\n");
        }
        */

        codeStore.addStatementFirstToBufferInit(block);
    }


    /**
     * @return The core we should allocate this file reader on.  Remember that 
     * the file reader is allocated to off-chip memory.  We just cycle through the cores
     * if there is more than one file reader, one reader per core.
     */
    private Core nextAllocatingCore() {
        if(allocatingCores.get(filterNode) != null)
            return allocatingCores.get(filterNode);
        
        // Try cores that are not yet allocating and already have existing code
        for (Core core : SMPBackend.chip.getCores()) {
            if (!allocatingCores.containsValue(core) && core.getComputeCode().shouldGenerateCode()) {
                allocatingCores.put(filterNode, core);
                return core;
            }
        }

        // Try cores that are not yet allocating, but do not already have code
        for (Core core : SMPBackend.chip.getCores()) {
            if (!allocatingCores.containsValue(core)) {
                allocatingCores.put(filterNode, core);
                return core;
            }
        }

        assert false : "Too many file readers for this chip (one per core)!";
        return null;
    }
    
    private long getFileSizeBytes() {
        long size = 0;
        
        try {
            File inputFile = new File(fileInput.getFileName());
            size = inputFile.length();
            System.out.println("Input file " + fileInput.getFileName() + " has size " + size);
        } catch (Exception e) {
            System.err.println("Error opening input file: " + fileInput.getFileName());
            System.exit(1);
        }
        return size;
    }
    
    private class FileReaderCodeKey {
        public WorkNode filter;
        public WorkNode dsFilter;

        public FileReaderCodeKey(WorkNode filter, WorkNode dsFilter) {
            this.filter = filter;
            this.dsFilter = dsFilter;
        }

        public boolean equals(Object obj) {
            if(obj instanceof FileReaderCodeKey) {
                FileReaderCodeKey id = (FileReaderCodeKey)obj;

                if(this.filter.equals(id.filter) &&
                   this.dsFilter.equals(id.dsFilter)) {
                    return true;
                }

                return false;
            }

            return false;
        }

        public int hashCode() {
            return filter.hashCode() + dsFilter.hashCode();
        }
    }
}
