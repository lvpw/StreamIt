
/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: AntlrOptions.java,v 1.1 2001-08-30 16:32:35 thies Exp $
 */

// Generated by optgen from AntlrOptions.opt
package at.dms.compiler.tools.antlr.compiler;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class AntlrOptions extends at.dms.util.Options {

  public AntlrOptions(String name) {
    super(name);
  }

  public AntlrOptions() {
    this("Antlr");
  }
  public String destination = ".";

  public boolean processOption(int code, Getopt g) {
    switch (code) {
    case 'd':
      destination = getString(g, ""); return true;
    default:
      return super.processOption(code, g);
    }
  }

  public String[] getOptions() {
    String[]	parent = super.getOptions();
    String[]	total = new String[parent.length + 1];
    System.arraycopy(parent, 0, total, 0, parent.length);
    total[parent.length + 0] = "  --destination, -d<String>: Sets the directory where all output is generated [.]";
    
    return total;
  }


  public String getShortOptions() {
    return "d:" + super.getShortOptions();
  }


  public void version() {
    System.out.println("Version 1.5A released 24 May 2001");
  }


  public void usage() {
    System.err.println("usage: at.dms.compiler.tools.antlr.compiler.Main [option]* [--help] <grammar-files>");
  }


  public void help() {
    System.err.println("usage: at.dms.compiler.tools.antlr.compiler.Main [option]* [--help] <grammar-files>");
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
    new LongOpt("destination", LongOpt.REQUIRED_ARGUMENT, null, 'd')
  };
}
