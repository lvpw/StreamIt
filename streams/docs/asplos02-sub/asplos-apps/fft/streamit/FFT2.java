import streamit.*;

class CombineDFT extends Filter
{
    CombineDFT(int i)
    {
        super(i);
    }
    float wn_r, wn_i;
    int nWay;
    float results[];
    public void init(int n)
    {
        nWay = n;
        input = new Channel(Float.TYPE, 2 * n);
        output = new Channel(Float.TYPE, 2 * n);
	wn_r = (float) Math.cos(2 * 3.141592654 / ((double) n));
        wn_i = (float) Math.sin(2 * 3.141592654 / ((double) n));
        results = new float[2 * n];
    }

    public void work()
    {
        int i;
        float w_r = 1;
        float w_i = 0;
        for (i = 0; i < nWay; i += 2)
        {
            float y0_r = input.peekFloat(i);
            float y0_i = input.peekFloat(i+1);
            float y1_r = input.peekFloat(nWay + i);
            float y1_i = input.peekFloat(nWay + i + 1);

            float y1w_r = y1_r * w_r - y1_i * w_i;
            float y1w_i = y1_r * w_i + y1_i * w_r;

            results[i] = y0_r + y1w_r;
            results[i + 1] = y0_i + y1w_i;

            results[nWay + i] = y0_r - y1w_r;
            results[nWay + i + 1] = y0_i - y1w_i;

            float w_r_next = w_r * wn_r - w_i * wn_i;
            float w_i_next = w_r * wn_i + w_i * wn_r;
            w_r = w_r_next;
            w_i = w_i_next;
        }

        for (i = 0; i < 2 * nWay; i++)
        {
            input.popFloat ();
            output.pushFloat(results[i]);
        }
    }
}

class FFTReorderSimple extends Filter
{
    FFTReorderSimple (int i) { super (i); }
    
    int nWay;
    int totalData;
    
    public void init (int n)
    {
        nWay = n;
        totalData = nWay * 2;
        
        input = new Channel (Float.TYPE, n * 2);
        output = new Channel (Float.TYPE, n * 2);
    }
    
    public void work ()
    {
        int i;
        
        for (i = 0; i < totalData; i+=4)
        {
            output.pushFloat (input.peekFloat (i));
            output.pushFloat (input.peekFloat (i+1));
        }
        
        for (i = 2; i < totalData; i+=4)
        {
            output.pushFloat (input.peekFloat (i));
            output.pushFloat (input.peekFloat (i+1));
        }
        
        for (i=0;i<nWay;i++)
        {
            input.popFloat ();
            input.popFloat ();
        }
    }
}

class FFTReorder extends Pipeline 
{
    FFTReorder (int i) { super (i); }
    
    public void init (int nWay)
    {
	int i;
	for (i=1; i<(nWay/2); i*=2) {
	    add (new FFTReorderSimple (nWay/i));
	}
    }
}

class FFTKernel1 extends Pipeline
{
    public FFTKernel1 (int i) { super (i); }
    public void init (final int nWay)
    {
        if (nWay > 2)
        {
            add (new SplitJoin ()
            {
                public void init ()
                {
                    setSplitter (ROUND_ROBIN (2));
                    add (new FFTKernel1 (nWay / 2));
                    add (new FFTKernel1 (nWay / 2));
                    setJoiner (ROUND_ROBIN (nWay));
                }
            });
        }
        add (new CombineDFT (nWay));
    }
}

class FFTKernel2 extends SplitJoin
{
    public FFTKernel2(int i)
    {
        super(i);
    }
    public void init(final int nWay)
    {
	setSplitter(ROUND_ROBIN(nWay*2));
	for (int i=0; i<2; i++) {
	    add (new Pipeline() {
		    public void init() {
			add (new FFTReorder (nWay));
			for (int j=2; j<=nWay; j*=2) {
			    add(new CombineDFT (j));
			}
		    }
		});
	}
	setJoiner(ROUND_ROBIN(nWay*2));
    }
}

public class FFT2 extends StreamIt
{
    public static void main(String[] args)
    {
        new FFT2().run(args);
    }
    public void init()
    {
        add(new FFTTestSource(64));
        add(new FFTKernel2(64));
        add(new FloatPrinter());
    }
}

