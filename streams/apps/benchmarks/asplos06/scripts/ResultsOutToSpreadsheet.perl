#!/usr/uns/bin/perl
#
###############################################################################
# Take the results.out files for raw and for spacetime and put out lines for
# a spreadsheet (in semicolon-separated-value format).
#
# Create columns for:
#  (1) benchmark name
#  (2) benchmark options
#  (3) throughput, defined as number of cycles per data item output
#  (4) utilization, defined as used instruction issues / available issues.
#  (%) MFLOPs
#
# The input to this script is a list of result.out files for which to create 
# rows in the spreadsheet.  The files are expected to be in subdirectories
# of the CWD with names of the form  ... .raw...
# The lowest subdirectory name up to '.raw' is output as the benchmark name.
# the characters in the directory name after '.raw' are output as the
# options, unless there are no such characters, in which case the options
# field is set to '-space'.
#
# To use, do something like:
#   
# find . -name "results.out" -exec echo `pwd`/'{}' \; >! resultfiles
# $STREAMIT_HOME/apps/benchmarks/asplos06/scripts/ResultsToSpreadsheet.perl \
#  resultfiles > results.csv
#
# You can then load results.csv into a spreadsheet, or sort it and look at 
# the semicolon-separated format.  If the latter, you may want to restore
# the column headers line to the top of the file from wherever sort left it.
# (we could do something with head, wc, and tail,  or with grep to remove
#  and replce the header line, but not worth the effort right now.)
#
# Notes:
# (1) Because of a bug in the speed setting, we had to pull out the MFLOPS 
#  field for spacetime from numbers for individual steady states and take
#  the mean.  This bug may now be fixed.
# (2) The results.out files for -raw  and -raw -spacetime are substantially
#  different.
# (3) Mike Gordon says that the utilization numbers for -raw versus 
#  -raw -spacetime do not allow a fair comparison, since there is extra
# code needed for -raw that is not needed for -raw -spacetime...
# 
###############################################################################

use warnings;
use strict;

# print header
print " benchmark;options;throughput;utilization;MFLOPS\n";

foreach (<>) {
    chomp;
    my $filenameandpath = $_;
    my ($fullfilename) = /.*\/([_A-Za-z0-9]+)\.raw(.*)\/results.out/;
    next unless -s $filenameandpath;  # ignore 0-length files.
    my $benchmark = $1;
    my $options = $2;
    $options = "-space" unless $options;
    if ($options eq "-space") {
	my $data = `(tail -1 "$filenameandpath")`;
	chomp($data);
	#print "$data\n";
	#tiles;used;avg_cycles/steady;XX;XX;XX;outputs_per_steady;??;MFLOPS;instr_issued;XX;XX;XX%;XX;XX;max_instrs_issued
	$data =~/^([0-9]*);([0-9]*);([0-9]*);[0-9]*;[0-9]*;[0-9]*;([0-9]*);[0-9]*;([0-9]*);([0-9]*);[0-9]*;[0-9]*;[0-9]*%;[0-9]*;[0-9]*;([0-9]*)$/;
	my $tiles = $1;
	my $used_tiles = $2;
	my $cyc_per_steady = $3;
	my $outputs_per_steady = $4;
	my $mflops = $5;
	my $instrs_issued = $6;
	my $max_instrs_issued = $7;
	my $throughput = "";
	if (defined($cyc_per_steady) && defined($outputs_per_steady)) {
	    $throughput = $cyc_per_steady / $outputs_per_steady;
	}
	my $utilization = "";
	if (defined($instrs_issued) && defined($max_instrs_issued )) {
	    $utilization = $instrs_issued / $max_instrs_issued;
	}
	$mflops = "" unless defined($mflops);
	print "$benchmark;$options;$throughput;$utilization;$mflops\n";
    } elsif ($options =~ /^-spacetime/) {
	open (RESULTS, "< $filenameandpath") or next;
	my @mflops = ();
	my $tiles = "";
	my $used_tiles = "";
	my $throughput = "";
        my $instrs_issued;
	my $max_instrs_issued;
	my $utilization = "";
	my $mflops = "";
	while (<RESULTS>) {
	    chomp;
	    if (/MFLOPS = ([0-9]+)/) {
		push(@mflops, $1);
	    } elsif (/^([0-9]+);([0-9]+);([0-9]+);([0-9]+);([0-9]+);[0-9]+$/) {
	        #tiles;assigned;throughput;work_cycles;total_cycles;bogus_mflops
	        $tiles = $1;
		$used_tiles = $2;
		$throughput = $3;
		$instrs_issued = $4;
		$max_instrs_issued = $5;
		if (defined($instrs_issued) && defined($max_instrs_issued )) {
		    $utilization = $instrs_issued / $max_instrs_issued;
		}
	    }
	}
	close(RESULTS);
        my $totalmflops = 0;
        foreach (@mflops) {
	    $totalmflops += $_ if defined($_);
        }
	my $ss_count = @mflops+0;
	if ($ss_count) {
	    $mflops = $totalmflops / $ss_count;
	}
	print "$benchmark;$options;$throughput;$utilization;$mflops\n";
    } else {
	print STDERR "unrecognized file format\n";
    }
}
