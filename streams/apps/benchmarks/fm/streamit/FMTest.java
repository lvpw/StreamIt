/*
 * Test for testing LowPassFilter with float files
 */

import streamit.*;
import streamit.io.*;

public class FMTest extends StreamIt
{
    static public void main(String[] t)
    {
        new FMTest().run(t);
    }
    
    public void init()
    {
	add(new FloatOneSource());
	add(new FMRadio());
	add(new FloatPrinter());
    }
}

        















