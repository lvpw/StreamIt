/** 
 * VectAdd.java - a very simple program -> add two vectors (C[] = A[] + B[])  
 */ 

import streamit.*; 


/**
 * The main kernel: i1 + i2 -> o1 (add two numbers to produce an output)  
 */  
class VectAddKernel extends Filter  
{ 
  public void init() 
  {
    input = new Channel(Integer.TYPE, 2); 
    output = new Channel(Integer.TYPE, 1); 
  } 
  public void work() 
  {
    output.pushInt(input.popInt() + input.popInt()); 
  } 
} 

/** 
 * A simple source that sends out elements from a vector repeatedly.  
 */ 
class VectSource extends Filter  
{
  int N;
  int idx;  
  int Z[]; 

  public VectSource(int N, int Z[]) 
  {
    super(N, Z);  
  } 
  public void init(final int N, final int Z[]) 
  { 
    output = new Channel(Integer.TYPE, 1); 
    this.N = N; 
    this.idx = 0;
  
    //this.Z = new int[N]; 
    //for (int i=0; i<N; i++) 
    //  this.Z[i] = Z[i]; 
 
    this.Z = Z; 
  } 
  public void work() 
  { 
    output.pushInt(Z[idx]); 
    idx++; 
    if (idx >= N) idx = 0; 
  } 
} 

/** 
 * Sends out elements from the two input vectors 
 */ 
class TwoVectSource extends SplitJoin 
{ 
  /* the input vectors */  
  int A[], B[]; 

  public TwoVectSource(int N) 
  { 
    super(N); 
  } 
  public void init(final int N) 
  { 
    /* set up the input vectors */ 
    A = new int[N]; 
    B = new int[N]; 
    for (int i=0; i<N; i++) 
    { 
      A[i] = i; 
      B[i] = i; //N-i; 
    } 

    /* generate and mix the two streams */ 
    this.setSplitter(NULL()); 
    this.add(new VectSource(N, A)); 
    this.add(new VectSource(N, B));
    this.setJoiner(ROUND_ROBIN());  
  } 
} 

/** 
 * Prints the output vector  
 */ 
class VectPrinter extends Filter 
{ 
  public void init() 
  { 
    input = new Channel(Integer.TYPE, 1); 
  }  
  public void work() 
  {
    System.out.println(input.popInt()); 
  } 
} 

/** 
 * The driver class 
 */ 
class VectAdd extends StreamIt 
{
  public static void main(String args[]) 
  { 
    (new VectAdd()).run(args); 
  }  
  public void init() 
  { 
    final int N =  10; 

    this.add(new TwoVectSource(N)); 
    this.add(new VectAddKernel()); 
    this.add(new VectPrinter()); 
  } 
} 

