
package at.dms.kjc.sir.lowering.fusion;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;

// Finds variable definitions and accesses that have type "Int" or "Float" 
// and replaces them with numbered variables. When fusing multiple operators
// this allows operators to reuse variables.

// calling procedure:
//    FindVarDecls findVarDecls = new FindVarDecls();
//
//    for each operator to be fused
//         block = (JBlock)findVarDecls.findAndReplace(block);
// 
//    findVarDecls.addVariableDeclarations(newBlock); 

public class FindVarDecls extends SLIREmptyVisitor {

    private int max_int_count;
    private int max_float_count;

    private int int_count;
    private int float_count;

    private HashMap var_names; // JVariableDefinition -> Integer

    private HashMap ints; // Integer -> JVariableDefinition
    private HashMap floats; // Integer -> JVariableDefinition
    
    public FindVarDecls() { 
	max_int_count = 0;
	max_float_count = 0;
	ints = new HashMap();
	floats = new HashMap();
    }

    public void newOperator() {
	int_count = 0;
	float_count = 0;
	var_names = new HashMap();
    }
    
    // reset tells if this operator should be assigned new variables

    public JStatement findAndReplace(JStatement body) {
	
	//if (reset)
	    newOperator();

	//int_count = 0;
	//float_count = 0;
	//var_names = new HashMap();
	body.accept(this);
	//System.out.println("Found ints:"+int_count+" floats:"+float_count);
	if (int_count > max_int_count) { max_int_count = int_count; }
	if (float_count > max_float_count) { max_float_count = float_count; }
	ReplaceVarDecls replace = new ReplaceVarDecls(var_names, this);
	JBlock new_body = (JBlock)body.accept(replace);
	return new_body;
    }

    public int getMaxIntCount() { return max_int_count; }

    public int getMaxFloatCount() { return max_float_count; }
    
    public JVariableDefinition getIntVar(Integer index) { 
	if (!ints.containsKey(index)) {
	    JVariableDefinition var = new JVariableDefinition(null, 
		   0, CStdType.Integer, "__int_"+index.toString(), null);
	    ints.put(index, var);
	    return var;
	}
	return (JVariableDefinition)ints.get(index);
    }

    public JVariableDefinition getFloatVar(Integer index) { 
	if (!floats.containsKey(index)) {
	    JVariableDefinition var = new JVariableDefinition(null, 
		   0, CStdType.Float, "__float_"+index.toString(), null);
	    floats.put(index, var);
	    return var;
	}
	return (JVariableDefinition)floats.get(index);
    }

    public void visitVariableDeclarationStatement(JVariableDeclarationStatement self,
                                                  JVariableDefinition[] vars) {
        for (int i = 0; i < vars.length; i++) {
	    CType type = vars[i].getType();

	    if (type.isArrayType()) continue;

	    if (type.getTypeID() == CType.TID_INT) {
		var_names.put(vars[i],new Integer(int_count));
		int_count++;
	    }
	    if (type.getTypeID() == CType.TID_FLOAT) {
		var_names.put(vars[i],new Integer(float_count));
		float_count++;
	    }
        }
    }

    public void addVariableDeclarations(JBlock block) {

	for (int i = 0; i < getMaxIntCount(); i++) {
	    JVariableDefinition var = getIntVar(new Integer(i));
	    block.addStatementFirst(
                   new JVariableDeclarationStatement(null, var, null));
	}

	for (int i = 0; i < getMaxFloatCount(); i++) {
	    JVariableDefinition var = getFloatVar(new Integer(i));
	    block.addStatementFirst(
                   new JVariableDeclarationStatement(null, var, null));
	}
    }

}


public class ReplaceVarDecls extends SLIRReplacingVisitor {
    
    private HashMap var_names; // String (Ident) -> Integer
    private FindVarDecls find_obj;

    ReplaceVarDecls(HashMap var_names, FindVarDecls find_obj) {
	this.var_names = var_names;
	this.find_obj = find_obj;
    }

    public Object visitVariableDeclarationStatement(JVariableDeclarationStatement self,
						    JVariableDefinition[] vars) 
    {
	
	LinkedList new_vars = new LinkedList();
	LinkedList new_statements = new LinkedList();
	
	for (int i = 0; i < vars.length; i++) {

	    if (!var_names.containsKey(vars[i])) {
		// the variable has not been eliminated

		// if statement declares only one variable return the statement
		if (vars.length == 1) return self;

		// otherwise add the variable to the list
		new_vars.add(vars[i]);

	    } else {

		// the variable has been eliminated
		if (vars[i].getType().getTypeID() == CType.TID_INT) {
		    if (vars[i].getValue() != null) {		    
			Integer name = 
                            (Integer)var_names.get(vars[i]);
			JVariableDefinition var = find_obj.getIntVar(name); 
			JLocalVariableExpression var_expr = new JLocalVariableExpression(null, var); 
			JExpression expr = new JAssignmentExpression(null, var_expr, vars[i].getValue());
			new_statements.addLast(new JExpressionStatement(null, expr, null));
		    }
		}
		
		
		if (vars[i].getType().getTypeID() == CType.TID_FLOAT) {
		    if (vars[i].getValue() != null) {		    
			Integer name = 
                            (Integer)var_names.get(vars[i]);
			JVariableDefinition var = find_obj.getFloatVar(name);
			JLocalVariableExpression var_expr = new JLocalVariableExpression(null, var); 
			JExpression expr = new JAssignmentExpression(null, var_expr, vars[i].getValue());
			new_statements.addLast(new JExpressionStatement(null, expr, null));
		    }
		}
	    }
	    
	}

	// make sure that all variables are either renamed or none is renamed
	// this is because Unroller/VarDeclRaiser do not correctly handle
	// varaible declarations inside of a JCompoundStatement, so
	// we must return a JVariableDeclarationStatement or a JCompoundStatement
	// that does not contain declarations.
	
	assert (new_vars.size() == 0 || new_statements.size() == 0);

	if (new_vars.size() > 0) {

	    JVariableDefinition new_array[] = (JVariableDefinition[])new_vars.toArray(new JVariableDefinition[0]);
	    self.setVars(new_array);
	    return self;

	} else {

	    JExpressionStatement new_array[] = (JExpressionStatement[])new_statements.toArray(new JExpressionStatement[0]);
	    return new JCompoundStatement(null, new_array);

	}
    }

    
    public Object visitLocalVariableExpression(JLocalVariableExpression self,
					       String ident) {

	if (var_names.containsKey(self.getVariable())) {

	    // variable has been eliminated
	    if (self.getType().getTypeID() == CType.TID_INT) {
		Integer name = (Integer)var_names.get(self.getVariable());
		JVariableDefinition var = find_obj.getIntVar(name);
		return new JLocalVariableExpression(null, var);
	    }

	    if (self.getType().getTypeID() == CType.TID_FLOAT) {
		Integer name = (Integer)var_names.get(self.getVariable());
		JVariableDefinition var = find_obj.getFloatVar(name);
		return new JLocalVariableExpression(null, var);
	    }
	}

	// variable has not been eliminated
	return self;
    }

}
