import streamit.*;

class Test2
{
        public Test2 ()
        {
                Init ();
        }
        public void Init ()
        {
        }
}

class PrintInt extends Filter
{
    public void InitIO ()
    {
        input = new Channel (Integer.TYPE);
        output = null;
    }
    public void Work ()
    {
        System.out.println (input.PopInt ());
    }
}

public class test extends Stream
{
    static public void main (String [] t)
    {
        test x = new test ();
        x.Run ();
    }
    public void Init ()
    {
        Add (new FeedbackLoop ()
        {
            public void Init ()
            {
                SetDelay (2);
                SetJoiner (WEIGHTED_ROUND_ROBIN (0,1));
                SetBody (new Filter ()
                {
                    public void InitIO ()
                    {
                        input = new Channel (Integer.TYPE);
                        output = new Channel (Integer.TYPE);
                    }

                    public void Work ()
                    {
                        output.PushInt (input.PeekInt (0) + input.PeekInt (1));
                        input.PopInt ();
                    }
                });
                SetSplitter (DUPLICATE ());
            }

            public void InitPath (int index, Channel path)
            {
                path.PushInt(index);
            }
        });
        Add (new PrintInt ());
    }
}