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
 * $Id: JPerf.java,v 1.1 2001-08-30 16:32:41 thies Exp $
 */

package at.dms.compiler.tools.jperf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * This class represents the perfect hashing function generator
 */

public class JPerf {

  // --------------------------------------------------------------------
  // CONSTRUCTORS
  // --------------------------------------------------------------------

  /**
   * Constructs a JPerf object
   *
   * @param	keywords	the keywords to hash
   * @param	header		the verbatim part to output at the beginning of the file
   * @param	footer		the verbatim part to output at the end of the file
   * @param	load factor	the load factor
   */
  public JPerf(String[] keywords,
	       String[] header,
	       String[] footer,
	       double loadFactor) {
    this.keywords	= new Keywords(keywords);
    this.header		= header;
    this.footer		= footer;
    this.maxNodeCount	= (long)Math.ceil(loadFactor*keywords.length);
  }

  /**
   * Constructs a JPerf object with default load factor of 5
   *
   * @param	keywords	the keywords to hash
   * @param	header		the verbatim part to output at the beginning of the file
   * @param	footer		the verbatim part to output at the end of the file
   * @param	load factor	the load factor
   */
  public JPerf(String[] keywords, String[] header, String[] footer) {
    this(keywords, header, footer, 25./12.);
  }

  // --------------------------------------------------------------------
  // ACCESSORS & MUTATORS
  // --------------------------------------------------------------------

  /**
   * Builds tables and graph.
   *
   * Tasks: 1. generate the tables table1 and table2;
   *        2. generate the graph
   *        3. assure that the graph is acyclic
   */
  public void build() {
    table1 = new Table("T1",
		       keywords.getMaxWordLength(),
		       keywords.getMinCharValue(),
		       keywords.getMaxCharValue());
    table2 = new Table("T2",
		       keywords.getMaxWordLength(),
		       keywords.getMinCharValue(),
		       keywords.getMaxCharValue());
    graph = new Graph(maxNodeCount);

    // Adds keywords one by on as long as the graph is acyclic. If graph
    // becomes cyclic, repeat the whole process. If, after adding all
    // keywords, the graph is still acyclic, we are done.
    boolean	acyclic;

    do {
      table1.init();
      table2.init();
      graph.init();

      acyclic = true;
      for (int i = 0; acyclic && i < keywords.size(); i++) {
	acyclic = addKey(keywords.elementAt(i), i);
      }
    } while (! acyclic);

    // Assign recursively g-values to all nodes
    graph.assignGValues(keywords.size());
  }

  /**
   * Adds a `key -> value' pair.
   *
   * @return	true iff the graph is still acyclic
   */
  private boolean addKey(String key, long value) {
    long	sum1 = table1.insertKey(key, maxNodeCount);
    long	sum2 = table2.insertKey(key, maxNodeCount);

    // insert correspondent nodes into the graph
    return graph.addEdge(sum1, sum2, value);
  }

  // --------------------------------------------------------------------
  // CODE GENERATION
  // --------------------------------------------------------------------

  /**
   * Dumps result to class source.
   *
   * @param	fileName	the name of the output file.
   */
  public void genCode(String fileName) throws IOException {
    PrintWriter		out;

    out = new PrintWriter(new FileOutputStream(fileName, false), true);

    // output header
    printStringArray(out, header);

    out.println();
    out.println(" // --------------------------------------------------------------------");
    out.println(" // CODE GENERATED BY JPERF STARTS HERE");
    out.println();
    out.println("  private static final int MAX_GRAPH_NODE_VAL = " + maxNodeCount + ";");

    out.println();
    keywords.genCode(out);
    out.println();
    table1.genCode(out);
    table2.genCode(out);
    out.println();
    graph.genCode(out);
    out.println();

    out.println();
    printStringArray(out, EQUALS_FUNCTION);
    out.println();
    printStringArray(out, HASH_FUNCTION);
    out.println();
    printStringArray(out, FIND_FUNCTION);

    out.println();
    out.println(" // CODE GENERATED BY JPERF ENDS HERE");
    out.println(" // --------------------------------------------------------------------");
    out.println();

    // output footer
    printStringArray(out, footer);

    out.close();
  }

  /**
   * Prints a string array to the output file. Each element is written on a separate line.
   */
  private void printStringArray(PrintWriter out, String[] lines) {
    for (int i = 0; i < lines.length; i++) {
      out.println(lines[i]);
    }
  }

  // --------------------------------------------------------------------
  // DATA MEMBERS
  // --------------------------------------------------------------------

  private final String[]		EQUALS_FUNCTION = {
    "  private static final boolean equals(final char[] key, int offset, int length, final char[] word) {",
    "    if (word.length != length) {",
    "      return false;",
    "    } else {",
    "      for (int i = 0; i < length; i++) {",
    "	 if (word[i] != key[offset + i]) {",
    "	   return false;",
    "	 }",
    "      }",
    "      return true;",
    "    }",
    "  }"
  };

  private final String[]		HASH_FUNCTION = {
    "  private static final int hash(final char[] key, int offset, int length) {",
    "    int		f1 = 0;",
    "    int		f2 = 0;",
    "",
    "    for (int i = 0; i < length; i++) {",
    "      char	c = key[i + offset];",
    "",
    "      if (c < MIN_CHAR_VAL || c > MAX_CHAR_VAL) {",
    "	return -1;",
    "      }",
    "",
    "      int	t1 = T1[i][c - MIN_CHAR_VAL];",
    "      if (t1 == -1) {",
    "	return -1;",
    "      }",
    "",
    "      int	t2 = T2[i][c - MIN_CHAR_VAL];",
    "      if (t2 == -1) {",
    "	return -1;",
    "      }",
    "",
    "      f1 += t1;",
    "      f2 += t2;",
    "    }",
    "",
    "    f1 %= MAX_GRAPH_NODE_VAL;",
    "    f2 %= MAX_GRAPH_NODE_VAL;",
    "",
    "    return (gIndex(f1)+gIndex(f2)) % TOTAL_KEYWORDS;",
    "  }"
  };

  private final String[]		FIND_FUNCTION = {
    "  private static final int find(final char[] key, int offset, int length) {",
    "    if (length <= MAX_WORD_LENG && length >= MIN_WORD_LENG) {",
    "      int	ind = hash(key, offset, length);",
    "",
    "      if (ind < TOTAL_KEYWORDS && ind >= 0) {",
    "	if (equals(key, offset, length, keywords[ind])) {",
    "	  return ind;",
    "	}",
    "      }",
    "    }",
    "    return -1;",
    "  }"
  };

  // input
  private final Keywords		keywords;
  private final String[]		header;
  private final String[]		footer;

  private final long			maxNodeCount;

  // the table1 and table2, refer to document for their meanings
  private Table				table1;
  private Table				table2;

  // the vector for holding all nodes in the intermediate graph
  private Graph				graph;
}
