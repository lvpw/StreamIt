/*
 *  Copyright 2001 Massachusetts Institute of Technology
 *
 *  Permission to use, copy, modify, distribute, and sell this software and its
 *  documentation for any purpose is hereby granted without fee, provided that
 *  the above copyright notice appear in all copies and that both that
 *  copyright notice and this permission notice appear in supporting
 *  documentation, and that the name of M.I.T. not be used in advertising or
 *  publicity pertaining to distribution of the software without specific,
 *  written prior permission.  M.I.T. makes no representations about the
 *  suitability of this software for any purpose.  It is provided "as is"
 *  without express or implied warranty.
 */

import streamit.*;
import java.lang.Math;

/**
 * Class ComplexToMag
 *
 * A filter to convert complex data into its magnitude.
 */

class ComplexToMag extends Filter
{
  public ComplexToMag ()
  {
    super ();
  }

  public void init()
  {
    input = new Channel (Float.TYPE, 2);
    output = new Channel (Float.TYPE, 1);
  }

  public void work()
  {
    //System.out.println("Running the Complex to Mag converter...");
    output.pushFloat((float) Math.sqrt(input.peekFloat(0)*input.peekFloat(1)));
    input.popFloat();
    input.popFloat();
  }
}
