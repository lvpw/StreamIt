// This is the definition of the helper routines for inlining in Gsm

//class Helper
//{
int intify(int a)
{
    if (a >= 32767)
	{
	    return 32767;
	}
    else
	{
	    if (a <= -32768)
		{
		    return -32768;
		}
	    else
		{
		    return a;
		}
	}
}
    
int gsm_add(int a, int b)

{
    int ltmp = a + b;
    if (ltmp >= 32767)
	{
	    return 32767;
	}
    else
	{
	    if (ltmp <= -32768)
		{
		    return -32768;
		}
	    else
		{
		    return ltmp;
		}
	}
}

int gsm_sub(int a, int b)
{
    int ltmp = a - b;
    if (ltmp >= 32767)
	{
	    return 32767;
	}
    else
	{
	    if (ltmp <= -32768)
		{
		    return -32768;
		}
	    else
		{
		    return ltmp;
		}
	}
}

int gsm_mult(int a, int b)
{
    int temp =  a *  b >> 15;
    if (temp >= 32767)
	{
	    return 32767;
	}
    else
	{
	    if (temp <= -32768)
		{
		    return -32768;
		}
	    else
		{
		    return (int) temp;
		}
	}       
}

int gsm_mult_r(int a, int b)
{
    int temp = ( a *  b) + 16384;
    int answer = (int) (temp >> 15);
    return answer;
}

int gsm_abs(int a)
{
    int answer;
    int temp;
    if (a < 0)
	{
	    if (a == -32768)
		{
		    answer = 32767;
		}
	    else
		{
		    temp = a * -1;
		    if (temp >= 32767)
			{
			    answer = 32767;
			}
		    else
			{
			    if (temp <= -32768)
				{
				    answer = -32768;
				}
			    else
				{
				    answer = (int) temp;
				}
			}
		}
	}
    else
	{
	    answer = a;
	}
    return answer;
}
//}






