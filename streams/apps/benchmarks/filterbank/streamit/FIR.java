/*
 *  Copyright 2002 Massachusetts Institute of Technology
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


// This is the complete FIR pipeline

import streamit.*;

/**
 * Class FirFilter
 *
 * Implements an FIR Filter
 */

public class FIR extends Pipeline {

    public FIR (float[] COEFF)
    {
        super (COEFF);
    }

    public void init(float[] COEFF) {
	add (new DelayPipeline(COEFF.length-1));
	add (new FirFilter(COEFF));
    }

}

























