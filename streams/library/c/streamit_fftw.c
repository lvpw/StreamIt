/*
 * interface to the fftw library to replace streamit_fft.c (which 
 * contains a simple non optimized implementation of fft).
 * 
 * It is reasonably important to note that all of the functions
 * have as their first argument a pointer to "this" in the
 * streamit program. We simply ignore this.
 * 
 * $Id: streamit_fftw.c,v 1.7 2003-03-08 21:12:02 aalamb Exp $
 */

#include <sfftw.h>
#include <srfftw.h>

/* Linked list of the FFTW plans we know about. */
struct rfftw_plan_list 
{
  int size;
  rfftw_plan rtoc_plan;
  rfftw_plan ctor_plan;
  float *buff;
  struct rfftw_plan_list *next;
};
static struct rfftw_plan_list *plans;

static struct rfftw_plan_list *get_plan(int size)
{
  struct rfftw_plan_list *plan;
  float *spare_storage;

  /* 1. Look for a plan; if we have it, return it. */
  for (plan = plans; plan; plan = plan->next)
    if (plan->size == size)
      return plan;
  
  /* 2. We lose.  Create a new plan. */
  plan = malloc(sizeof(struct rfftw_plan_list));
  plan->size = size;
  plan->next = plans;
  plans = plan;
  plan->buff = malloc(size * sizeof(float));
  spare_storage = malloc(size * sizeof(float));
  plan->rtoc_plan = rfftw_create_plan_specific
    (size, FFTW_REAL_TO_COMPLEX,
     FFTW_ESTIMATE | FFTW_OUT_OF_PLACE | FFTW_USE_WISDOM,
     spare_storage, 1, plan->buff, 1);
  plan->ctor_plan = rfftw_create_plan_specific
    (size, FFTW_COMPLEX_TO_REAL,
     FFTW_ESTIMATE | FFTW_OUT_OF_PLACE | FFTW_USE_WISDOM,
     plan->buff, 1, spare_storage, 1);
  free(spare_storage);
  return plan;
}


/* Multiplies an FFTW halfcomplex array by a known set of
 * complex constants, also in halfcomplex format.
 * output: Y (float array of size size)
 * input1: X (float array of size size)
 * input2: H (float array of size size)
 *
 * All arrays are  Hermitian, meaning that for all
 * i 0<=i<n, x[i] = conj(x[n-i]).  FFTW then stores this in a single
 * array, where for 0<=i<=n/2, x[i] is the real part of X[i] (and also
 * the real part of X[n-i]), and for 0<i<n/2, x[n-i] is the complex part
 * of X[i] (and the negated complex part of X[n-i]).  It appears to
 * follow from the documentation that X[0] is strictly real (which is
 * due to the math of the FFT.
 *
 * The output can be safely set to be one of the inputs if desired.
 */
void do_halfcomplex_multiply(void* thisptr, float *Y, float *X, float *H, int size)
{
  int i;
  
  /* We get to ignore the last half of H.  Whee!  :-) */
  /* First off, calculate buff[0], which apparently is strictly real. */
  Y[0] = X[0] * H[0];
  
  /* Now go through the complex parts of the array: */
  for (i = 1; i < size/2; i++)
    {
      /* (a+bi)(c+di) = (ac-bd)+(ad+bc)i */
      float X_real = X[i];
      float X_imag = X[size-i];
      float H_real = H[i];
      float H_imag = H[size-i];
      Y[i]      = X_real * H_real - X_imag * H_imag;
      Y[size-i] = X_real * H_imag + X_imag * H_real;
    }
  
  /* If size is even, we also need to deal with the middle (real) element. */
  if (!(size & 1)) {
    Y[size/2] = X[size/2] * H[size/2];
  }
}


/* 
 * takes an input array of floats (in the time domain) 
 * and produces an output array of floats in the same array.
 */
//void do_fast_convolution_fftw(float* x, float* H, int size) {
//  int i;
//  struct rfftw_plan_list *plan;

  /* Start off by finding the plan pair, or creating one. */
//  plan = get_plan(size);
  
  /* Run the forward FFT. */
//  rfftw_one(plan->rtoc_plan, (fftw_real *)x, (fftw_real *)plan->buff);

  /* Do the multiplication element-wise in frequency.  Note that this
   * is a little weird because of the ordering; see the FFTW documentation.
   * This happens in place. */
  //do_halfcomplex_multiply(plan->buff, H, size);

  //_debug_print_halfcomplex("Y", plan->buff, size);

  /* Run the backward FFT (trashing storage). */
//  rfftw_one(plan->ctor_plan, (fftw_real *)plan->buff, (fftw_real  *)x);

//}


/**
 * Replaces the contents of input_buff with the value of its FFT.
 * input_buff: input (real format)/output (halfcomplex format)
 *
 * Since buff is a assumed completly real, the corresponding complex
 * valued FFT(input_buff) is stored in the "half complex array" format of
 * fftw (see http://www.fftw.org/doc/fftw_2.html#SEC5)
 **/
void convert_to_freq(void* thisptr, float* input_buff, int size) 
{
  struct rfftw_plan_list *plan;
  int i;

  /* Start off by finding the plan pair, or creating one. */
  plan = get_plan(size);

  /* Run the forward FFT on the input buffer, saving the result
     into the plan's buffer. */
  rfftw_one(plan->rtoc_plan, (fftw_real *)input_buff, (fftw_real *)plan->buff);

  /* copy the values from the plan buffer (eg the output) into the 
   * input buffer (return value is passed via input). **/
  for (i=0; i<size; i++) {
    input_buff[i] = plan->buff[i];
  }

  /** and we are done. Return value is the input_buffer parameter. **/
}

/** 
 * Scales each element of the passed array by 1/size. 
 *
 * buffer: input/output
 * Since FFTW does not perform the 1/N scaling of the inverse
 * DFT, the N point IFFT(FFT(x)) will result in x scaled by N.
 * This function is used to pre-scale the coefficients of H
 * by 1/N so we don't have to do it on each filter invocation.
 **/
void scale_by_size(void* thisptr,float* buffer, int size) 
{
  int i;
  for (i=0; i<size; i++) {
    buffer[i] = buffer[i]/size;
  }
}


/**
 * Converts the contents of input_buff from the frequency domain
 * to the time domain, omitting the 1/N factor.
 *
 * input_buff: input in half complex array format.
 * output_buff: output of real values (because the IFFT of a 
 *              halfcomplex (eg symmetric) sequency is purely real.
 * 
 * Since this function uses FFTW to compute the inverse FFT,
 * the result is not scaled by the 1/N factor that it should be.
 * In our implementation, the impulse response of the filter
 * is prescaled scaled by 1/N so we get the correct answer.
 *
 * Note that this function trashes the values in input_buff.
 **/
void convert_from_freq(void* thisptr, float* input_buff, float* output_buff, int size) 
{
  struct rfftw_plan_list *plan;

  /* Start off by finding the plan pair, or creating one. */
  plan = get_plan(size);

  /* Run the backward FFT (destroys the contents of input_buff) */
  // reverse is specified by the plan. Then comes input followed by output.
  
  rfftw_one(plan->ctor_plan, (fftw_real *)input_buff, (fftw_real *)output_buff);
  
  /** and we are done. Return value is the input_buffer parameter. **/
}




/** debugging routine that prints a halfcomplex array. **/
void _debug_print_halfcomplex(char* prefix, float* complex_arr, int size) {
  int i;
  // the first element is purely real
  printf("%s[%d]:%f\n", prefix, 0, complex_arr[0]);
  for (i=1; i<(size/2); i++) {
    printf("%s[%d]:%f+%fi\n", prefix, i, complex_arr[i], complex_arr[size-i]);
  }
  // if size even, also print out the middle element which is purely real.
  if ((size % 2) == 0) {
    printf("%s[%d]:%f\n", prefix, (size/2), complex_arr[size/2]);
  }
}

/** debugging routine that prints a real array. **/
void _debug_print_real(char* prefix, float* arr, int size) {
  int i;
  for (i=0; i<size; i++) {
    printf("%s[%d]:%f\n", prefix, i, arr[i]);
  }
}
