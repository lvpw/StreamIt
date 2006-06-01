#!/bin/sh -e
#
# release.sh: assemble a StreamIt release
# David Maze <dmaze@cag.lcs.mit.edu>
# $Id: release.sh,v 1.49 2006-06-01 14:56:09 dimock Exp $
#

# foe script debugging: -v print line in script, -x print expanded line
#set -v
#set -x

# Interesting/configurable variables:

# For a version release
#VERSION=2.0
#TAG=streamit-2-0

# For a snapshot release
VERSION=2.0.`date +%Y%m%d`
TAG=HEAD

test -z "$TMPDIR" && TMPDIR=/tmp
PRECIOUS=
CVSROOT2=

usage() {
  cat >&2 <<EOF
release.sh: assemble a StreamIt release

Usage:
  release.sh [options]

Options:
  --version (-v)   Use a particular exported version ($VERSION)
  --tag (-r)       Build a release from a CVS tag ($TAG)
  --cvsroot (-d)   Specify the CVS root directory ($CVSROOT)
  --tmpdir         Use a different build directory ($TMPDIR)
  --antlr          Location of the ANTLR jar file ($ANTLRJAR)
  --precious (-k)  Keep the working directory
EOF
}

# Command-line options:
while test -n "$1"
do
  OPT="$1"; shift
  case $OPT in
    --version|-v) VERSION="$1"; shift;;
    --tag|-r) TAG="$1"; shift;;
    --tmpdir) TMPDIR="$1"; shift;;
    --cvsroot|-d) CVSROOT="$1"; export CVSROOT; CVSROOT2="-d $1" shift;;
    --precious|-k) PRECIOUS=yes;;
    *) usage; exit 1;;
  esac
done

# Temporary directory:
WORKING=$TMPDIR/streamit-$USER-$$
mkdir $WORKING
SRCDIR=$WORKING/streams
STREAMIT_HOME=$SRCDIR
SRCTAR=$WORKING/streamit-src-$VERSION.tar
BINDIR=$WORKING/streamit-$VERSION
BINTAR=$WORKING/streamit-$VERSION.tar
export STREAMIT_HOME

# Helper function to add a list of directories to $DIRS
builddirs() {
  PREFIX="$1"; shift
  while test -n "$1"; do DIRS="$DIRS $PREFIX/$1"; shift; done
}

# Get a checked-out copy of the source tree.
mkdir $WORKING/streams
DIRS="streams/strc streams/Makefile streams/README.source"
builddirs streams 3rdparty src library include misc configure.in
builddirs streams/apps benchmarks examples libraries sorts
builddirs streams/docs cookbook implementation-notes release syntax
builddirs streams/docs index.html

cvs $CVSROOT2 export -r $TAG -d $WORKING $DIRS

# Run autoconf to get a configure script.
autoconf $WORKING/streams/configure.in > $WORKING/streams/configure
chmod 0755 $WORKING/streams/configure

# Generate .in files.  (See also the tail of configure.in.)
INFILES="strc Makefile library/c/Makefile misc/Makefile.vars"
for f in $INFILES; do
  if test -f "$WORKING/streams/$f"; then
    $WORKING/streams/misc/make-dot-in.pl "$WORKING/streams/$f"
  fi
done

# Don't release CPLEX jar file or anything that depends on it
rm -rf $WORKING/streams/3rdparty/cplex/
rm -rf $WORKING/streams/src/at/dms/kjc/linprog/
rm -rf $WORKING/streams/src/at/dms/kjc/sir/lowering/partition/ILPPartitioner.java

# Remove .cvsignore files
rm -rf `find $WORKING -name ".cvsignore"`
# remove "calculations" from ASPLOS paper
rm -rf `find $WORKING -name "calculations"`

# remove PBS number gathering scripts
rm -rf $WORKING/streams/misc/scripts/number-gathering

# Some benchmarks we can't (or won't) export; trim those here.
# Streamit code not currently working.
rm -rf $WORKING/streams/apps/benchmarks/audiobeam
rm -rf $WORKING/streams/apps/benchmarks/beamformer/c
rm -rf $WORKING/streams/apps/benchmarks/cfar
rm -rf $WORKING/streams/apps/benchmarks/gsm/c
rm -rf $WORKING/streams/apps/benchmarks/gsm
rm -rf $WORKING/streams/apps/benchmarks/nokia
rm -rf $WORKING/streams/apps/benchmarks/perftest4
# no streamit code being built currently:
rm -rf $WORKING/streams/apps/benchmarks/serpent
rm -rf $WORKING/streams/apps/benchmarks/viram
#rm -rf $WORKING/streams/apps/benchmarks/vocoder
rm -rf $WORKING/streams/apps/benchmarks/micro04
#rm -rf $WORKING/streams/apps/benchmarks/pldi03
#rm -rf $WORKING/streams/apps/benchmarks/mpeg2
# do we want to trim down mpeg inputs, outputs?
rm -rf $WORKING/streams/apps/benchmarks/traces
rm -rf $WORKING/streams/apps/benchmarks/asplos06
# JPEGtoBMP was not working at time of release
rm -rf $WORKING/streams/apps/benchmarks/jpeg/streamit/JPEGtoBMP.str
# this is only relevant for spacedynamic backend, so don't release
rm -rf $WORKING/streams/apps/benchmarks/jpeg/streamit/Transcoder_Raw.str

# remove the 500MB of input and output for DCT
rm -rf $WORKING/streams/apps/benchmarks/dct_ieee/input
rm -rf $WORKING/streams/apps/benchmarks/dct_ieee/output

# complex FIR is fine but was a simple benchmarking exercise, 
# seems redundant with "fir"
rm -rf $WORKING/streams/apps/benchmarks/complex-fir
# FIR bank might be proprietary, and besides it has 5 MB
# of coefficients and we don't compile it well yet
rm -rf $WORKING/streams/apps/benchmarks/firbank

rm -rf $WORKING/streams/apps/examples/chol-para
rm -rf $WORKING/streams/apps/examples/median
rm -rf $WORKING/streams/apps/examples/phase
rm -rf $WORKING/streams/apps/examples/sample-trellis
rm -rf $WORKING/streams/apps/examples/toy-trellis
rm -rf $WORKING/streams/apps/examples/updown
rm -rf $WORKING/streams/apps/examples/vectadd/VectAdd1.*
rm -rf $WORKING/streams/apps/tests/portals
# autobatchersort gets the wrong answer
rm -rf $WORKING/streams/apps/sorts/BatcherSort/AutoBatcherSort.*
# don't release applications directory except GMTI
rm -rf $WORKING/streams/apps/applications/802.11a
rm -rf $WORKING/streams/apps/applications/crc
rm -rf $WORKING/streams/apps/applications/DCT
rm -rf $WORKING/streams/apps/applications/FAT
rm -rf $WORKING/streams/apps/applications/FAT-new
rm -rf $WORKING/streams/apps/applications/hdtv
rm -rf $WORKING/streams/apps/applications/nokia
rm -rf $WORKING/streams/apps/applications/nokia-fine
rm -rf $WORKING/streams/apps/applications/nokia-new
rm -rf $WORKING/streams/apps/applications/raytracer
rm -rf $WORKING/streams/apps/applications/raytracer-new
rm -rf $WORKING/streams/apps/applications/reed-solomon
rm -rf $WORKING/streams/apps/applications/video

# don't release some C++ software radio thing (?)
rm -rf $WORKING/streams/apps/libraries/SoftRadio

# Some parts of the compiler aren't useful to release; trim those here.
#rm -rf $WORKING/streams/src/at/dms/kjc/flatgraph2
rm -rf $WORKING/streams/src/at/dms/kjc/raw2
rm -rf $WORKING/streams/src/at/dms/kjc/spacetime
#rm -rf $WORKING/streams/src/at/dms/kjc/spacedynamic
rm -rf $WORKING/streams/src/com
rm -rf $WORKING/streams/src/org
rm -rf $WORKING/streams/src/streamit/eclipse
rm -rf $WORKING/streams/src/streamit/stair

# la la la
rm -rf $WORKING/streams/misc/release.sh

# remove PCA machine model
rm -rf $WORKING/streams/misc/raw/pca-mm

# Some parts of the language notes we don't want to be visible
rm -f $WORKING/streams/docs/syntax/02-04-24-additions
rm -f $WORKING/streams/docs/syntax/02-08-additions
rm -f $WORKING/streams/docs/syntax/messaging.tex
rm -f $WORKING/streams/docs/implementation-notes/assumptions
rm -f $WORKING/streams/docs/implementation-notes/immutable-ir.txt
rm -f $WORKING/streams/docs/implementation-notes/low-ir.txt
rm -f $WORKING/streams/docs/implementation-notes/messaging-implementation.txt
rm -f $WORKING/streams/docs/implementation-notes/portals.txt

# Release 2.1 version of language
mv $WORKING/streams/docs/syntax/streamit-lang-2.1.tex $WORKING/streams/docs/syntax/streamit-lang.tex 

# Build interesting bits of the documentation; they go in both releases.
for d in release cookbook syntax; do
  make -C $WORKING/streams/docs/$d
done
find $WORKING/streams/docs \( -name '*.aux' -o -name '*.log' \
  -o -name '*.toc' -o -name '*.[0-9]' \) -print0 | xargs -0 rm
for f in COPYING COPYING.GPL; do
  cp $WORKING/streams/docs/release/$f $WORKING/streams
done
for f in INSTALL NEWS OPTIONS README; do
  mv $WORKING/streams/docs/release/$f $WORKING/streams
done
$WORKING/streams/misc/build-bench-doc
rm $WORKING/streams/apps/benchall.xml

# Make stable copies for all of the trees.  Clean the binary tree a little
# in the process.
cp -R $WORKING/streams $BINDIR
rm -rf $BINDIR/src $BINDIR/README.source
rm -rf $BINDIR/include/dot-bashrc
rm -rf $BINDIR/include/dot-cshrc
rm -rf $BINDIR/misc/get-antlr
find $BINDIR/docs \( -name '*.hva' -o -name '*.tex' -o -name Makefile \
  -o -name '*.mp' \) -print0 | xargs -0 rm
rm -f $BINDIR/misc/htmlformat.pl
for f in $INFILES; do
  rm -f "$BINDIR/$f"
done

# Build the source tarball:
cp -R $WORKING/streams $WORKING/streamit-src-$VERSION
for f in $INFILES; do
  rm -f "$WORKING/streamit-src-$VERSION/$f"
done
tar cf $SRCTAR -C $WORKING streamit-src-$VERSION

# Use the build magic to get an ANTLR jar file.
$SRCDIR/misc/get-antlr $SRCDIR/3rdparty/antlr.jar
CLASSPATH=$SRCDIR/3rdparty/antlr.jar

# Now do a reference build.
export CLASSPATH
. $STREAMIT_HOME/include/dot-bashrc
make -C $SRCDIR/src jar CAG_BUILD=0

# Build binary jar file:
cp $SRCDIR/src/streamit.jar $BINDIR
tar cf $BINTAR -C $WORKING streamit-$VERSION

# gzip the tarball and move it here.
gzip $SRCTAR $BINTAR
mv $SRCTAR.gz $BINTAR.gz .

# Clean up.
if test -n "$PRECIOUS"
then
  echo Keeping working directory $WORKING
else
  rm -rf $WORKING
fi

