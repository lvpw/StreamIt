#!/usr/local/bin/perl

# this script execites the the TimeTest program with various lengths of FIR filter
# and the frequency replacer to see what the effect of scaling is on
# the execution time of the benchmark.

use strict;
require "reaplib.pl";

my $STANDARD_OPTIONS = "--unroll 100000 --debug";
my $FREQ_OPTIONS     = "--frequencyreplacement";

# the filename to write out the results to. (append first command line arg to name)
my $RESULTS_FILENAME = "freq_fir_timing.tsv";
my $PROGRAM_NAME = "TimeTest";

# array to hold results
my @result_lines;
# heading
push(@result_lines, 
     "Program\tFIR size\t" .
     "normal outputs\tnormal time\tnormal time no printf\t" .
     "freq outputs\tfreq time\tfreq time no printf\t" .
     "load 1\t load 5\t load 15\t");

# number of iterations to run (because we need them to run for a while).
my $ITERS = 500000;
     
# generate the java program from the streamit syntax
print `rm -f $PROGRAM_NAME.java`;
print `make $PROGRAM_NAME.java`;

my $i;
# for various FIR lengths
my @fir_lengths;
#for ($i=1; $i<32; $i*=sqrt(2)) {
for ($i=128; $i<256; $i++) {
    push(@fir_lengths, int($i));
}


my $firLength;
foreach $firLength (@fir_lengths) {
    # modify the program for the appropriate FIR length
    set_fir_length("$PROGRAM_NAME.java", $firLength);

    # compile normally
    print "normal($firLength):";
    # figure out how many outputs are produced ($NUM_ITERS is defined in reaplib.pl)
    do_compile(".", $PROGRAM_NAME, "$STANDARD_OPTIONS");
    my $normal_outputs = get_output_count(".", $PROGRAM_NAME, $ITERS);
    my $normal_time = time_execution(".", $PROGRAM_NAME, $ITERS);

    # now, recompile the C code after removing the printlines
    remove_prints(".", $PROGRAM_NAME);
    do_c_compile(".", $PROGRAM_NAME);
    my $normal_time_np = time_execution(".", $PROGRAM_NAME, $ITERS);
    print "\n";

    # compile frequency replacement
    print "freq 3($firLength):";
    # figure out how many outputs are produced ($NUM_ITERS is defined in reaplib.pl)
    do_compile(".", $PROGRAM_NAME, "$STANDARD_OPTIONS $FREQ_OPTIONS");
    my $freq_outputs = get_output_count(".", $PROGRAM_NAME, $ITERS);
    my $freq_time = time_execution(".", $PROGRAM_NAME, $ITERS);

    # now, recompile the C code after removing the printlines
    remove_prints(".", $PROGRAM_NAME);
    do_c_compile(".", $PROGRAM_NAME);
    my $freq_time_np = time_execution(".", $PROGRAM_NAME, $ITERS);
    print "\n";


    # as a final bit of data, get the load averages
    my ($load_1, $load_5, $load_15) = get_load();


    push(@result_lines, 
	 "$PROGRAM_NAME\t$firLength\t".
	 "$normal_outputs\t$normal_time\t$normal_time_np\t" .
	 "$freq_outputs\t$freq_time\t$freq_time_np\t" .
	 "$load_1\t$load_5\t$load_15");
}


# now, when we are done with all of the tests, write out the results to a tsv file.
print "writing tsv";
open (RFILE, ">$RESULTS_FILENAME");
print RFILE join("\n", @result_lines);
close RFILE;
print "done\n";


