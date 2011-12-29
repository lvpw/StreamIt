package at.dms.kjc.rstream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import at.dms.kjc.JForStatement;
import at.dms.kjc.JLocalVariable;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JPhylum;
import at.dms.kjc.SLIREmptyVisitor;

/**
 * This class will return a HashMap from local vars->hashset.  The hashset
 * contains all the uses of a local variable variable from the
 * starting point of the visitor. 
 *
 * @author Michael Gordon
 * 
 */

public class UseDefInfo extends SLIREmptyVisitor 
{
    private HashMap<JLocalVariable, HashSet> uses;

    /**
     * Given a method, return a hashmap from local vars -&gt; HashSet, 
     * where the hashset holds all the access of the local variable in 
     * the method.
     *
     * @param meth The method to search.
     *
     *
     * @return The hashmap as described above.
     * 
     */
    public static HashMap<JLocalVariable, HashSet> getUsesMap(JMethodDeclaration meth) 
    {
        UseDefInfo useInfo = new UseDefInfo();
        meth.accept(useInfo);
        return useInfo.uses;
    }

    /**
     * Given a for loop, return a hashmap from local vars -> HashSet, 
     * where the hashset holds all the accesses of the local variable in 
     * the for looping including body, init, cond, and increment.
     *
     * @param jfor the for statement.
     *
     * @return The hashmap as described above.
     * 
     */
    public static HashSet<Object> getForUses(JForStatement jfor) 
    {
        UseDefInfo useInfo = new UseDefInfo();
        jfor.getBody().accept(useInfo);
        jfor.getInit().accept(useInfo);
        jfor.getCondition().accept(useInfo);
        jfor.getIncrement().accept(useInfo);
    
        HashSet<Object> ret = new HashSet<Object>();
        Iterator<JLocalVariable> vars = useInfo.uses.keySet().iterator();
        while (vars.hasNext()) {
            StrToRStream.addAll(ret, useInfo.uses.get(vars.next()));
        }

        return ret;
    }
    
    /**
     * Given an IR tree, return a hashmap from local vars -> HashSet, 
     * where the hashset holds all the accesses of the local variable in 
     * the tree.
     *
     * @param jsomething the start of the visitor
     *
     * @return The hashmap as described above.
     * 
     */
    public static HashSet<Object> getUses(JPhylum jsomething)  
    {
        UseDefInfo useInfo = new UseDefInfo();
        jsomething.accept(useInfo);

        HashSet<Object> ret = new HashSet<Object>();
        Iterator<JLocalVariable> vars = useInfo.uses.keySet().iterator();
        while (vars.hasNext()) {
            StrToRStream.addAll(ret, useInfo.uses.get(vars.next()));
        }

        return ret;
    }
    
    private UseDefInfo() 
    {
        uses = new HashMap<JLocalVariable, HashSet>();
    }

    /*    
          private void addUse(JFieldAccessExpression exp)
          {

          if (!uses.containsKey(exp.getIdent()))
          uses.put(exp.getIdent(), new HashSet());

          ((HashSet)uses.get(exp.getIdent())).add(exp);
          }
    */
    private void addUse(JLocalVariableExpression exp) 
    {
        //if we didn't see this var before, add the 
        //hashset to hold uses
    
        if (exp.getVariable() == null) 
            System.out.println("Null variable");
    

        if (!uses.containsKey(exp.getVariable()))
            uses.put(exp.getVariable(), new HashSet());

        uses.get(exp.getVariable()).add(exp);
    }
    
    @Override
	public void visitLocalVariableExpression(JLocalVariableExpression self,
                                             String ident) {
        addUse(self);
    }
}
