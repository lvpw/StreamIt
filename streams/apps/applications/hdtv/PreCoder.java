/**
 * Simple, 1/2 precoder for use in HDTV. It xors
 * the input with the previous output to give the current
 * outputChannel. Eg x = input, y = output
 * so y(t) = x(t) XOR y(t-1)
 *
 * Starts with state = 0 (or equivaletly y(-1) = 0)
 *
 * x2 ---->XOR-------> y2 ---------------------> z2
 *          ^      | 
 *          |      |
 *          |--D<--| (state)
 *
 **/


import streamit.library.*;

class PreCoder extends Filter {
    /** last input that we saw **/
    int state;
    public void init() {
	inputChannel= new Channel(Integer.TYPE, 1); // pops one
	outputChannel= new Channel(Integer.TYPE, 1); // pushes one
 	this.state = 0; // start with 0 as state
    }
    public void work() {
	int t = inputChannel.popInt() ^ this.state; // xor input with state
	outputChannel.pushInt(t);
	// update state
	this.state = t;
    }
}
