package streamittest;

import junit.framework.*;
import at.dms.kjc.sir.linear.ComplexNumber;
import at.dms.kjc.sir.linear.FilterMatrix;
import at.dms.kjc.sir.linear.FilterVector;
import at.dms.kjc.sir.linear.LinearForm;


/**
 * Regression test for linear filter extraction and
 * manipulation framework.
 * $Id: TestLinear.java,v 1.6 2002-08-30 20:11:20 aalamb Exp $
 **/

public class TestLinear extends TestCase {
    public TestLinear(String name) {
	super(name);
    }

    public static Test suite() {
	TestSuite suite = new TestSuite();
	suite.addTest(new TestLinear("testSimple"));
	suite.addTest(new TestLinear("testComplexNumberCreationAndAccess"));
	suite.addTest(new TestLinear("testComplexNumberEquality"));
	suite.addTest(new TestLinear("testComplexNumberNegation"));
	suite.addTest(new TestLinear("testComplexNumberAddition"));
	suite.addTest(new TestLinear("testComplexNumberMultiplication"));
	suite.addTest(new TestLinear("testComplexNumberDivision"));
	
	suite.addTest(new TestLinear("testFilterMatrixConstructor"));
	suite.addTest(new TestLinear("testFilterMatrixAccessor"));
	suite.addTest(new TestLinear("testFilterMatrixModifier"));
	suite.addTest(new TestLinear("testFilterMatrixEquals"));
	suite.addTest(new TestLinear("testFilterMatrixCopy"));
	suite.addTest(new TestLinear("testFilterMatrixScale"));
	
	suite.addTest(new TestLinear("testFilterVector"));

	suite.addTest(new TestLinear("testLinearForm"));
	suite.addTest(new TestLinear("testLinearFormAddition"));
	suite.addTest(new TestLinear("testLinearFormCopyToColumn"));
	suite.addTest(new TestLinear("testLinearFormEquals"));
	suite.addTest(new TestLinear("testLinearFormMultiply"));
	suite.addTest(new TestLinear("testLinearFormDivide"));

	return suite;
    }
    
    public void testSimple() {
	assertTrue("was true", true);
    }

    /** Simple complex number tests **/
    public void testComplexNumberCreationAndAccess() {
	ComplexNumber cn;

	double MAX = 5;
	double STEP = .1;
	
	
	cn = new ComplexNumber(0,0);
	cn = new ComplexNumber(0,1);
	cn = new ComplexNumber(1,0);
	cn = new ComplexNumber(5,4);
	cn = new ComplexNumber(1.2,-94.43);

	// test access
	for (double i=-MAX; i<MAX; i+= STEP) {
	    for (double j=-MAX; j<MAX; j+= STEP) {
		cn = new ComplexNumber(i,j);
		assertTrue("real part", cn.getReal() == i);
		assertTrue("im part", cn.getImaginary() == j);
	    }
	}
    }
    
    /** Simple tests for equality **/
    public void testComplexNumberEquality() {
	ComplexNumber cn1;
	ComplexNumber cn2;

	cn1 = new ComplexNumber(.32453252342142523, .78881282845324);
	cn2 = new ComplexNumber(.32453252342142523, .78881282845324);

	// test for self equality
	assertTrue("self", cn1.equals(cn1));
	assertTrue("self", cn2.equals(cn2));
	
	// test for numerical equality
	assertTrue("numerical equality", cn1.equals(cn2));
	assertTrue("numerical equality", cn2.equals(cn1));

	// make sure we aren't just always doing true
	cn2 = new ComplexNumber(.8572983475029834, .78881282845324);
	assertTrue("not always equal", !cn1.equals(cn2));
	assertTrue("not always equal", !cn2.equals(cn1));

	cn2 = new ComplexNumber(-.32453252342142523, .78881282845324);
	assertTrue("not always equal", !cn1.equals(cn2));
	assertTrue("not always equal", !cn2.equals(cn1));
    }

    /** test that negating a complex number works **/
    public void testComplexNumberNegation() {
	ComplexNumber cn1;
	ComplexNumber cn2;

	//make sure that nothing screwy wiht zero is happening
	assertTrue("zero is fine", ComplexNumber.ZERO.negate().equals(ComplexNumber.ZERO));
	
	cn1 = new ComplexNumber(1.34879759847, 21324);
	cn2 = cn1.negate();
	assertTrue("negation not equal", !cn1.equals(cn2));

	// we should also be able to negate a number twice and have it be equal
	assertTrue("double negation", cn1.equals(cn1.negate().negate()));
	assertTrue("double negation", cn2.equals(cn2.negate().negate()));
	
    }

    /** test adding complex numbers together **/
    public void testComplexNumberAddition() {
	ComplexNumber cn1;
	ComplexNumber cn2;
	ComplexNumber cn3;

	ComplexNumber zero = ComplexNumber.ZERO;
	ComplexNumber one = ComplexNumber.ONE;
	ComplexNumber negativeOne = one.negate();

	// make sure that adding zero to itself is still zero
	assertTrue("0+0", zero.plus(zero).equals(zero));
	// make sure that one is fine too
	assertTrue("1+0", one.plus(zero).equals(one));	
	assertTrue("0+1", zero.plus(one).equals(one));

	// make sure that adding the negative of a number to itself is zero
	assertTrue("1+-1", one.plus(negativeOne).equals(zero));
	
	// test some random numbers
	cn1 = new ComplexNumber(1.2, 3.2);
	cn2 = new ComplexNumber(-1.2, -3);
	cn3 = new ComplexNumber(.4, -.3);
	assertTrue(cn1.plus(cn2).equals(new ComplexNumber(0, .2)));
	assertTrue(cn2.plus(cn1).equals(new ComplexNumber(0, .2)));
	assertTrue(cn1.plus(cn3).equals(new ComplexNumber(1.6, 2.9)));
	assertTrue(cn3.plus(cn1).equals(new ComplexNumber(1.6, 2.9))); 
	assertTrue(cn2.plus(cn3).equals(new ComplexNumber(-.8, -3.3)));
	assertTrue(cn3.plus(cn2).equals(new ComplexNumber(-.8, -3.3))); 
    }


    /** test multiplication complex numbers together **/
    public void testComplexNumberMultiplication() {
	ComplexNumber one  = ComplexNumber.ONE;
	ComplexNumber zero = ComplexNumber.ZERO;

	assertTrue("1*0=0", one.times(zero).equals(zero));
	assertTrue("0*1=0", zero.times(one).equals(zero));
	assertTrue("1*1-1", one.times(one).equals(one));

	// now, try with some legit. complex numbers
	ComplexNumber cn1 = new ComplexNumber(2,3); //2+3i
	ComplexNumber cn2 = new ComplexNumber(-4,5);//-4+5i

	ComplexNumber prod = new ComplexNumber(-23,-2);
	assertTrue("prod is good", cn1.times(cn2).equals(prod));
	assertTrue("prod is good", cn2.times(cn1).equals(prod));
	
    }

    /** test division of complex numbers **/
    public void testComplexNumberDivision() {
	ComplexNumber one  = ComplexNumber.ONE;
	ComplexNumber zero = ComplexNumber.ZERO;

	// try various operations with the identity elements
	assertTrue("1/1=1", one.dividedby(one).equals(one));
	assertTrue("0/1=0", zero.dividedby(one).equals(zero));
	try {one.dividedby(zero); fail("non div by 0 failure");} catch (Exception e){}
	
	ComplexNumber cn1 = new ComplexNumber(2,3);
	ComplexNumber cn2 = new ComplexNumber(5,7);

	assertTrue("cn1/1=cn1", cn1.dividedby(one).equals(cn1));
	assertTrue("cn2/1=cn2", cn2.dividedby(one).equals(cn2));

	// divide cn1 by cn2, ans should be 31/74 + 1/74i
	ComplexNumber answer1 = new ComplexNumber((((double)31)/((double)74)), (((double)1)/((double)74)));
	assertTrue("cn1/cn2", cn1.dividedby(cn2).equals(answer1));

	// visa versa, ans should be 31/13 - 1/13i
	ComplexNumber answer2 = new ComplexNumber((((double)31)/((double)13)), ((double)-1)*(((double)1)/((double)13)));
	assertTrue("cn2/cn1", cn2.dividedby(cn1).equals(answer2));
    }

    
    /** FilterMatrix tests **/
    public void testFilterMatrixConstructor() {
	int MAX = 10;
	FilterMatrix fm;
	// basically, just make a bunch of these matricies of various sizes
	// and make sure nothing bad happens, and that all elements are initialized
	// to zero
	for (int i=1; i<MAX; i++) {
	    for (int j=1; j<MAX; j++) {
		fm = new FilterMatrix(i,j);
		// check that the rows and cols are as expected
		assertTrue("row size matches", i == fm.getRows());
		assertTrue("col size matches", j == fm.getCols());
		for (int a=0; a<i; a++) {
		    for (int b=0; b<j; b++) {
			// check that the elt is 0
			ComplexNumber zero = new ComplexNumber(0,0);
			assertTrue(("(" + a + "," + b + ")=0"),
				   zero.equals(fm.getElement(a,b)));
		    }
		}
	    }
	}
	// also make sure that we can't do stupid things like instantiate matricies
	// with negative dimensions
	for (int i=0; i>-MAX; i--) {
	    for (int j=0; j>-MAX; j--) {
		try {
		    fm = new FilterMatrix(i,j);
		    // if we get here, it let us instantiate a matrix
		    // with negative dimensions
		    fail("can't instantiate a matrix with dimensions (" +
			 i + "," + j + ")");
		} catch (IllegalArgumentException e) {
		    // this is the case that we expect, so no worries
		}
	    }
	}
	
	
    }
    /**
     * Test the accessors a little bit more to make
     * sure that they catch errors as they are supposed to.
     **/
    public void testFilterMatrixAccessor() {
	int ROW_MAX = 3;
	int COL_MAX = 4;
	int MAX = 10;
	// make a matrix, and then try to get a bunch of elements
	// Make sure that they are valid ones
	FilterMatrix fm = new FilterMatrix(ROW_MAX,COL_MAX);
	assertTrue("right size row", fm.getRows() == ROW_MAX);
	assertTrue("right size col", fm.getCols() == COL_MAX);

	for (int i=0; i<MAX; i++) {
	    for (int j=0; j<MAX; j++) {
		// if we are within the bounds, expect no error
		if ((i<ROW_MAX) && (j<COL_MAX)) {
		    ComplexNumber zero = new ComplexNumber(0,0);
		    assertTrue("elt is 0", zero.equals(fm.getElement(i,j)));
		} else {
		    // we expect an exception
		    try {fm.getElement(i,j); fail("no exception on illegal access");}
		    catch (IllegalArgumentException e) {/*no worries*/}
		}
	    }
	}
    }


    /** Test setting the values within the matrix **/
    public void testFilterMatrixModifier() {
	// for this test, it is rather simple. We will just create a
	// matrix, set its elements to be particular values and then
	// verify that the appropriate numbers were stored.
	FilterMatrix fm;
	int MAX = 10;
	for (int i=0; i<MAX; i++) {
	    for (int j=0; j<MAX;j++) {
		fm = new FilterMatrix(i+1,j+1);
		for (int a=0; a<=i; a++) {
		    for (int b=0; b<=j; b++) {
			double re_val = Math.random();
			double im_val = Math.random();
			fm.setElement(a,b,new ComplexNumber(re_val, im_val));
			ComplexNumber value = new ComplexNumber(re_val, im_val);
			assertTrue("value matches", value.equals(fm.getElement(a,b)));
		    }
		}
	    }
	}

    }
    
    /** test equality of matricies **/
    public void testFilterMatrixEquals() {
	int ROWS = 15;
	int COLS = 15;
	FilterMatrix matrix1 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix2 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix3 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix4 = new FilterMatrix(ROWS-1, COLS);
	FilterMatrix matrix5 = new FilterMatrix(ROWS, COLS-1);
	FilterMatrix matrix6 = new FilterMatrix(ROWS+1, COLS-1);

	// check equality right off the bat
	assertTrue("virgin matrices", matrix1.equals(matrix2));
	assertTrue("virgin matrices", matrix1.equals(matrix3));

	// set the elements of mat1,2 and 3 to some value
	for (int i=0; i<ROWS; i++) {
	    for (int j=0; j<COLS; j++) {
		double val = i*j;
		matrix1.setElement(i,j,val);
		matrix2.setElement(i,j,val);
		matrix3.setElement(i,j,val);
	    }
	}

	// sabotage one of the elements in matrix 3.
	matrix3.setElement(ROWS/2, COLS/3, new ComplexNumber(0,0));

	// check self equality
	assertTrue("self 1", matrix1.equals(matrix1));
	assertTrue("self 2", matrix2.equals(matrix2));
	assertTrue("self 2", matrix3.equals(matrix3));
	assertTrue("self 3", matrix4.equals(matrix4));
	assertTrue("self 4", matrix5.equals(matrix5));
	assertTrue("self 5", matrix6.equals(matrix6));

	// 1 and 2 should be the same
	assertTrue("1==2", matrix1.equals(matrix2));
	assertTrue("2==1", matrix2.equals(matrix1));

	// 3 should not be
	assertTrue("1!=3", !matrix1.equals(matrix3));
	assertTrue("3!=1", !matrix3.equals(matrix1));

	// 456 have dimension mismatches with 1.
	assertTrue("1!=4", !matrix1.equals(matrix4));
	assertTrue("4!=1", !matrix4.equals(matrix1));
	assertTrue("1!=5", !matrix1.equals(matrix5));
	assertTrue("1!=6", !matrix1.equals(matrix6));
    }

    /** test the clone operation **/
    public void testFilterMatrixCopy() {
	int ROWS = 10;
	int COLS = 10;

	FilterMatrix matrix1 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix2 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix3 = new FilterMatrix(ROWS, COLS);
	
	// set the elements to their row+col values
	for (int i=0; i<ROWS; i++) {
	    for (int j=0; j<COLS; j++) {
		matrix1.setElement(i,j, i+j);
		matrix2.setElement(i,j, i+j);
	    }
	}

	// blank one should not be the same as the modifed one
	assertTrue(!matrix1.equals(matrix3));
	assertTrue("1==2", matrix1.equals(matrix2));
	
	// copy matrix 1
	FilterMatrix matrix1Copy = matrix1.copy();
	// check the equality with matrix 1
	assertTrue("clone equal", matrix1.equals(matrix1Copy));
	assertTrue("clone equal", matrix1Copy.equals(matrix1));
	// check with 2
	assertTrue("2==clone1", matrix2.equals(matrix1Copy));
	assertTrue("clone1==2", matrix1Copy.equals(matrix2));
	// now, change matrix one and verify that it doesn't change the clone
	matrix1.setElement(0,0,1000);
	assertTrue("1!=copy", !matrix1.equals(matrix1Copy));
	assertTrue("2==copy", matrix2.equals(matrix1Copy));
	// clone both matrix 2 and the matrix 1 copy and test equality
	assertTrue("copy1==copy2", matrix1Copy.copy().equals(matrix2.copy()));
	
    }	
    
    /** test the scale operation in FilterMatricies. **/
    public void testFilterMatrixScale() {
	int ROWS = 10;
	int COLS = 10;

	double factor = 5;
	ComplexNumber FACTOR = new ComplexNumber(factor,0);
	ComplexNumber FACTOR_INVERSE = new ComplexNumber(1/factor,0);

	FilterMatrix matrix1 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix2 = new FilterMatrix(ROWS, COLS);
	FilterMatrix matrix3 = new FilterMatrix(ROWS, COLS);
	
	// construct matrix 1 and its scaled version in matrix 2
	for (int i=0; i<ROWS; i++) {
	    for (int j=0; j<COLS; j++) {
		double val = i+j;
		matrix1.setElement(i,j, val);
		matrix2.setElement(i,j, val*factor);
	    }
	}

	// scale matrix 1 by factor and test to matrix 2
	assertTrue("non equal", !matrix1.equals(matrix2));

	// save old copy of matrix1
	FilterMatrix matrix1Orig = matrix1.copy();
	//scale and compare to matrix2
	matrix1.scale(FACTOR);
	assertTrue("scale", matrix1.equals(matrix2));

	// scale back
	matrix1.scale(FACTOR_INVERSE);
	assertTrue("unscale", matrix1Orig.equals(matrix1));
    }

    /** Test the FilterVector class **/
    public void testFilterVector() {
	// there are fewer tests here because most of the functionality is inhereted from
	// FilterMatrix
	double D_INCREMENT = .1;
	double D_MAX = 10;
	int MAX = 10;
	FilterVector fv;
	
	// make sure that we can create vectors and that all elts are 0
	for (int i=1; i<=MAX; i++) {
	    fv = new FilterVector(i);
	    for (int a=0; a<i; a++) {
		assertTrue("init to zero", ComplexNumber.ZERO.equals(fv.getElement(a)));
	    }
	}

	// also, ensure that we can't use the two argument form of set element or get element
	fv = new FilterVector(MAX);
	try{fv.getElement(1,1);fail("used two arg form of get");} catch (RuntimeException e) {/*no prob*/}
	try{fv.setElement(1,1,new ComplexNumber(1,2));fail("used two arg form of set");} catch (RuntimeException e) {/*no prob*/}


	// do a little exercise in getting elements from the vector
	for (int i=1; i<=MAX; i++) {
	    fv = new FilterVector(i);
	    // for each element in the vector
	    for (int j=0; j<i; j++) {
		// make a lot of complex numbers
		for (double a=-D_MAX; a<D_MAX; a+=D_INCREMENT) {
		    for (double b=-D_MAX; b<D_MAX; b+=D_INCREMENT) {
			ComplexNumber cn = new ComplexNumber(a,b);
			fv.setElement(j, cn);
			// make sure that this element is the same
			assertTrue("elt in vector is same",
				   fv.getElement(j).equals(new ComplexNumber(a,b)));
			// make sure that the size is correct
			assertTrue("Correct size", fv.getSize() == i);
		    }
		}
	    }
	}
    }

    public void testLinearForm() {
	LinearForm lf;
	// make sure that we can't make negative sized linear forms
	try {lf = new LinearForm(-1); fail("no error!");} catch(RuntimeException e){}
	try {lf = new LinearForm(0); fail("no error!");} catch(RuntimeException e){}

	// now, make sure that if we make a linear form with an integer offset,
	// isIntegerOffset works correctly
	lf = new LinearForm(1);
	lf.setOffset(10);
	assertTrue("int is actually an int", lf.isIntegerOffset());

	// make sure that the actual offset is the same
	assertTrue("Offset matches", lf.getIntegerOffset() == 10);

	// make sure that we can't get an integer offset of a offset
	// that is non integer
	lf.setOffset(3.2);
	try{lf.getIntegerOffset(); fail("should die");} catch(RuntimeException e) {}

	// now, make sure that the isOnlyOffset method works
	lf = new LinearForm(5);
	assertTrue("is only offset", lf.isOnlyOffset());
	// set the offset, and try again
	lf.setOffset(1);
	assertTrue("is only offset", lf.isOnlyOffset());
	lf.setOffset(-83.42);
	assertTrue("is only offset", lf.isOnlyOffset());
	
	
	// set an element and make sure that it is false
	lf.setWeight(3, new ComplexNumber(4,-1.4322));
	assertTrue("is not only offset", !(lf.isOnlyOffset()));	
    }

    /** test the additon and negation of linear forms **/
    public void testLinearFormAddition() {
	LinearForm lf1;
	LinearForm lf2;

	int MAX=10;
	
	lf1 = new LinearForm(MAX);
	lf2 = new LinearForm(MAX);
	// manually construct lf1 and lf2 to be its negative
	for (int i=0; i<MAX; i++) {
	    lf1.setWeight(i, new ComplexNumber(i,i));
	    lf2.setWeight(i, new ComplexNumber(-i,-i));
	}
	lf1.setOffset(new ComplexNumber(-32.423,0));
	lf2.setOffset(new ComplexNumber(32.423,0));

	LinearForm negativelf1 = lf1.negate();

	// check offsets
	assertTrue("Same offsets", negativelf1.getOffset().equals(lf2.getOffset()));
	assertTrue("Same offsets", lf2.getOffset().equals(negativelf1.getOffset()));
	
	// make sure that the element of negating lf1 are the same as lf2
	for (int i=0; i<MAX; i++) {
	    assertTrue("element " + i,
		       negativelf1.getWeight(i).equals(lf2.getWeight(i)));
	}

	// check to see if they sum to zero
	LinearForm lfzero = lf1.plus(lf2);
	assertTrue("Zero Offset", lfzero.getOffset().equals(ComplexNumber.ZERO));
	for (int i=0; i<MAX; i++) {
	    assertTrue("element " + i + " is zero",
		       lfzero.getWeight(i).equals(ComplexNumber.ZERO));
	}
	lfzero = lf2.plus(lf1);
	assertTrue("Zero Offset", lfzero.getOffset().equals(ComplexNumber.ZERO));
	for (int i=0; i<MAX; i++) {
	    assertTrue("element " + i + " is zero",
		       lfzero.getWeight(i).equals(ComplexNumber.ZERO));
	}
	
	// make sure that we can't add linear forms of different sizes
	lf1 = new LinearForm(10);
	lf2 = new LinearForm(11);
	try{lf1.plus(lf2); fail("diff linear form size");} catch (RuntimeException e) {}
    }

    public void testLinearFormCopyToColumn() {
	LinearForm lf;
	FilterMatrix fm;
	int MAX = 10;
	
	fm = new FilterMatrix(MAX,MAX);
	lf = new LinearForm(MAX);
	// test that we can't add the linear form to the wrong column
	try {lf.copyToColumn(fm, MAX); fail();}catch(RuntimeException e){}
	// make sure that we can't add a linear form of the incorrect size
	try {(new LinearForm(MAX-1)).copyToColumn(fm,MAX/2); fail();} catch (IllegalArgumentException e){}

	// set up the linear form with values, and then test to ensure that the values were copied
	for (int i=0; i<MAX; i++) {
	    lf.setWeight(i, new ComplexNumber(i+.01, i-.01));
	}

	// try copying into the first col
	int col = 0;
	lf.copyToColumn(fm, col);
	// ensure that all elements got there safely
	for (int i=0; i<MAX; i++) {
	    assertTrue("elements are the same", lf.getWeight(i).equals(fm.getElement(i,col)));
	    assertTrue("elements are the same", fm.getElement(i,col).equals(lf.getWeight(i)));
	}

	// copy another column
	fm = new FilterMatrix(MAX,MAX);
	col = MAX/2;
	lf.copyToColumn(fm, col);
	// ensure that all elements got there safely
	for (int i=0; i<MAX; i++) {
	    assertTrue("elements are the same", lf.getWeight(i).equals(fm.getElement(i,col)));
	    assertTrue("elements are the same", fm.getElement(i,col).equals(lf.getWeight(i)));
	}
	// make sure that the other elements are still 0
	for (int i=0; i<MAX; i++) {
	    for (int j=0; j<MAX; j++) {
		if (col == j) {
		    // should be the same as lf
		    assertTrue("elements are the same", lf.getWeight(i).equals(fm.getElement(i,j)));
		    assertTrue("elements are the same", fm.getElement(i,j).equals(lf.getWeight(i)));
		} else {
		    // should be zero
		    assertTrue("Still zero", fm.getElement(i,j).equals(ComplexNumber.ZERO));
		    assertTrue("Still zero", ComplexNumber.ZERO.equals(fm.getElement(i,j)));
		}
	    }
	}
    }

    public void testLinearFormEquals() {
	// nothing really to do because all of the code really sits in
	// FilterMatrix
	// we just want to make sure that we can scale linear forms
	int SIZE = 100;
	LinearForm lf1 = new LinearForm(SIZE);
	LinearForm lf2 = new LinearForm(SIZE);
	for (int i=0; i<SIZE; i++) {
	    lf1.setWeight(i,new ComplexNumber(i+1,i+2));
	    lf2.setWeight(i,new ComplexNumber(i+1,i+2));
	}
	lf1.setOffset(new ComplexNumber(3,-4));
	lf2.setOffset(new ComplexNumber(3,-4));

	assertTrue("identity", lf1.equals(lf1));
	assertTrue("identity", lf2.equals(lf2));
	assertTrue("0==0", (new LinearForm(SIZE)).equals(new LinearForm(SIZE)));
	
	assertTrue("equal", lf1.equals(lf2));
	assertTrue("equal", lf2.equals(lf1));

	// change a weight and check equality again.
	lf1.setWeight(SIZE/7, ComplexNumber.ZERO);
	assertTrue("not equal", !lf1.equals(lf2));
	assertTrue("not equal", !lf2.equals(lf1));
	assertTrue("not equal", !lf1.equals(new LinearForm(SIZE-1)));
	assertTrue("not equal", !lf2.equals(new LinearForm(SIZE-1)));
	assertTrue("not equal", !lf1.equals(new LinearForm(SIZE+1)));
	assertTrue("not equal", !lf2.equals(new LinearForm(SIZE+1)));
    }
	
    public void testLinearFormMultiply() {
	// we just want to make sure that we can scale linear forms
	int SIZE = 100;
	ComplexNumber scaleFactor = new ComplexNumber(4,23);
	
	LinearForm lf1 = new LinearForm(SIZE);
	LinearForm lf2 = new LinearForm(SIZE);
	for (int i=0; i<SIZE; i++) {
	    lf1.setWeight(i, new ComplexNumber(i+1,i+2));
	    lf2.setWeight(i, (new ComplexNumber(i+1,i+2)).times(scaleFactor));
	}
	ComplexNumber offset = new ComplexNumber(13,-2);
	lf1.setOffset(offset);
	lf2.setOffset(offset.times(scaleFactor));

	// make sure that they are not the same
	assertTrue("pre, not the same", !lf1.equals(lf2));
	assertTrue("pre, not the same", !lf2.equals(lf1));

	// scale lf1 by the factor
	LinearForm scaled1 = lf1.multiplyByConstant(scaleFactor);
	assertTrue("same", scaled1.equals(lf2));
	assertTrue("same", lf2.equals(scaled1));

	// make sure that they are not the same
	assertTrue("post, not the same", !lf1.equals(lf2));
	assertTrue("post, not the same", !lf2.equals(lf1));

    }

    /** test dividing all elements of a linear form by a constant. **/
    public void testLinearFormDivide() {
    }
    
    

}
