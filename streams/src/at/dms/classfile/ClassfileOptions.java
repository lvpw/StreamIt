// Generated by optgen from ClassfileOptions.opt
package at.dms.classfile;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class ClassfileOptions extends at.dms.util.Options {

  public ClassfileOptions(String name) {
    super(name);
  }

  public ClassfileOptions() {
    this("Classfile");
  }
  public boolean inter = false;
  public String destination = null;
  public int repeat = 1;

  public boolean processOption(int code, Getopt g) {
    switch (code) {
    case 'i':
      inter = !false; return true;
    case 'd':
      destination = getString(g, ""); return true;
    case 'R':
      repeat = getInt(g, 0); return true;
    default:
      return super.processOption(code, g);
    }
  }

  public String[] getOptions() {
    String[]	parent = super.getOptions();
    String[]	total = new String[parent.length + 3];
    System.arraycopy(parent, 0, total, 0, parent.length);
    total[parent.length + 0] = "  --inter, -i:          Reads interface only [false]";
    total[parent.length + 1] = "  --destination, -d<String>: Selects the destination directory";
    total[parent.length + 2] = "  --repeat, -R<int>:    Repeats R times the read/write process [1]";
    
    return total;
  }


  public String getShortOptions() {
    return "id:R:" + super.getShortOptions();
  }


  public void version() {
    System.out.println("Version 1.5B released 9 August 2001");
  }


  public void usage() {
    System.err.println("usage: at.dms.classfile.Main [option]* [--help] <class-files, zip-file, directory>");
  }


  public void help() {
    System.err.println("usage: at.dms.classfile.Main [option]* [--help] <class-files, zip-file, directory>");
    printOptions();
    System.err.println();
    version();
    System.err.println();
    System.err.println("This program is part of the Kopi Suite.");
    System.err.println("For more info, please see: http://www.dms.at/kopi");
  }

  public LongOpt[] getLongOptions() {
    LongOpt[]	parent = super.getLongOptions();
    LongOpt[]	total = new LongOpt[parent.length + LONGOPTS.length];
    
    System.arraycopy(parent, 0, total, 0, parent.length);
    System.arraycopy(LONGOPTS, 0, total, parent.length, LONGOPTS.length);
    
    return total;
  }

  private static final LongOpt[] LONGOPTS = {
    new LongOpt("inter", LongOpt.NO_ARGUMENT, null, 'i'),
    new LongOpt("destination", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
    new LongOpt("repeat", LongOpt.REQUIRED_ARGUMENT, null, 'R')
  };
}
