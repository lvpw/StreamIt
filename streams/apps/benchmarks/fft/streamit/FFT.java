import streamit.*;

class IdentityLocal extends Filter {
    public void init()
    {
        input = new Channel(Float.TYPE, 1);
        output = new Channel(Float.TYPE, 1);
    }

    public void work() {
        output.pushFloat(input.popFloat());
    }
}

class Filter1 extends Filter {

    //    float weights[];
    int curr;
    int W;

    public Filter1(int N, int W) {
        super(N, W);
    }

    public void init(int N, int W) {
        input = new Channel(Float.TYPE, 1);
        output = new Channel(Float.TYPE, 1);
        int i;
        this.W = W;
        //this.weights = new float[W];
        //        for (i=0; i<W; i+=1)
        //            weights[i] = calcWeight(i, N, W);
        curr = 0;
    }

    private float calcWeight(int a, int b, int c) {
        return 1;
    }

    public void work() {
        output.pushFloat(input.popFloat()*
                         2); //weights[curr++]);
        if(curr>= W) curr = 0;
    }
}

class Butterfly1 extends SplitJoin {
    public Butterfly1(int N, int W) { super (N, W); }

    public void init(final int N, final int W) {
        this.setSplitter(WEIGHTED_ROUND_ROBIN(N, N));
        this.add(new IdentityLocal());
        this.add(new Filter1(N, W));
        this.setJoiner(ROUND_ROBIN());
    }
}

class Butterfly2 extends SplitJoin {
    public Butterfly2(int N, int W) { super (N, W); }

    public void init(final int N, final int W) {
        this.setSplitter(DUPLICATE());

        this.add(new Filter() {
                public void init ()
                {
                    input = new Channel(Float.TYPE, 2);
                    output = new Channel(Float.TYPE, 1);
                }

                public void work() {
                    output.pushFloat(input.peekFloat(0) +
                                     input.peekFloat(1));
		    input.popFloat();
		    input.popFloat();
                }
            });
        this.add(new Filter() {
                public void init ()
                {
                    input = new Channel(Float.TYPE, 2);
                    output = new Channel(Float.TYPE, 1);
                }

                public void work() {
                    output.pushFloat(input.peekFloat(0) -
                                     input.peekFloat(1));
		    input.popFloat();
		    input.popFloat();
                }
            });

        this.setJoiner(WEIGHTED_ROUND_ROBIN(N, N));
    }
}

class SplitJoin2 extends SplitJoin {
    public SplitJoin2(int N) {
        super(N);
    }

    public void init(int N) {
        this.setSplitter(ROUND_ROBIN());
        this.add(new IdentityLocal());
        this.add(new IdentityLocal());
        this.setJoiner(WEIGHTED_ROUND_ROBIN((int)
                                            N/4,
                                            (int)
                                            N/4));
    }
}

class SplitJoin1 extends SplitJoin {
    public SplitJoin1(int N) {
        super(N);
    }

    public void init(int N) {
        int i;
        this.setSplitter(WEIGHTED_ROUND_ROBIN((int)N/2, (int)N/2));
        for (i=0; i<2; i+=1)
            this.add(new SplitJoin2(N));
        this.setJoiner(ROUND_ROBIN());
    }
}

class FFTKernelLocal extends Pipeline {
    public FFTKernelLocal(int N)
    {
        super (N);
    }

    public void init(final int N) {
        int i;
        this.add(new SplitJoin1(N));
        for (i=1; i<N; i*=2) {
            this.add(new Butterfly1(i, N));
            this.add(new Butterfly2(i, N));
        }
    }
}

class OneSourceLocal extends Filter
{
    public void init ()
    {
        output = new Channel(Float.TYPE, 1);
    }
    public void work()
    {
        output.pushFloat(1);
    }
}

class FloatPrinterLocal extends Filter
{
    public void init ()
    {
        input = new Channel(Float.TYPE, 1);
    }
    public void work ()
    {
        System.out.println(input.popFloat ());
    }
}

public class FFT extends StreamIt {
    public static void main(String args[]) {
        new FFT().run(args);
    }

    public void init() {
        this.add(new OneSourceLocal());
        this.add(new FFTKernelLocal(32));
        this.add(new FloatPrinterLocal());
    }
}


