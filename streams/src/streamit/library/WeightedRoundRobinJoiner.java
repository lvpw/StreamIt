/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.library;

import java.util.*;

public class WeightedRoundRobinJoiner extends Joiner
{
    List<Integer> srcsWeight = new ArrayList<Integer>();

    void addWeight(Integer weight)
    {
        assert weight != null && weight.intValue() >= 0;

        srcsWeight.add(weight);
    }

    public boolean isInputUsed(int index)
    {
        assert index < srcsWeight.size();
        return srcsWeight.get(index).intValue() != 0;
    }

    public void connectGraph()
    {
        // do I even have anything to do?
        assert srcs.size() == srcsWeight.size();
        super.connectGraph();
    }

    /*

    This work function has been replaced by a single work function
    in Joiner (parent of this class)
    
    public void work()
    {
    assert srcsWeight.size() == srcs.size();

    int inputIndex;
    for (inputIndex = 0; inputIndex < srcs.size(); inputIndex++)
    {
    int inputCount;

    for (inputCount =
    ((Integer) srcsWeight.get(inputIndex)).intValue();
    inputCount > 0;
    inputCount--)
    {
    passOneData(input[inputIndex], output);
    }
    }
    }
    */

    public int[] getWeights()
    {
        int numChildren = srcs.size();
        int[] weights = new int[numChildren];

        int i;
        for (i = 0; i < numChildren; i++)
            {
                if (srcs.get(i) != null && srcs.get(i).outputChannel != null)
                    {
                        weights[i] = srcsWeight.get(i).intValue();
                    }
            }

        return weights;
    }

    public int getProduction()
    {
        int numChildren = srcs.size();
        int outputTotal = 0;

        int i;
        for (i = 0; i < numChildren; i++)
            {
                if (srcs.get(i) != null && srcs.get(i).outputChannel != null)
                    {
                        outputTotal += srcsWeight.get(i).intValue();
                    }
            }

        return outputTotal;
    }

    public String toString() {
        int[] weights = getWeights();
        StringBuffer result = new StringBuffer("roundrobin(");
        for (int i=0; i<weights.length; i++) {
            result.append(weights[i]);
            if (i!=weights.length-1) {
                result.append(", ");
            } else {
                result.append(")");
            }
        }
        return result.toString();
    }
}
