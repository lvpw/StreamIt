/*
 * ExprBinary.java: A binary expression
 * David Maze <dmaze@cag.lcs.mit.edu>
 */

package streamit.frontend.nodes;

/**
 * An ExprBinary is a binary expression.  It has two child expressions,
 * which must be non-null, and an operator.  The child expressions are
 * ordered (because a-b is different from b-a).
 */
public class ExprBinary extends Expression
{
    // Operators:
    public static final int BINOP_ADD = 1;
    public static final int BINOP_SUB = 2;
    public static final int BINOP_MUL = 3;
    public static final int BINOP_DIV = 4;
    public static final int BINOP_MOD = 5;
    public static final int BINOP_AND = 6;
    public static final int BINOP_OR = 7;
    public static final int BINOP_EQ = 8;
    public static final int BINOP_NEQ = 9;
    public static final int BINOP_LT = 10;
    public static final int BINOP_LE = 11;
    public static final int BINOP_GT = 12;
    public static final int BINOP_GE = 13;
    // These are bitwise AND/OR/XOR:
    public static final int BINOP_BAND = 14;
    public static final int BINOP_BOR = 15;
    public static final int BINOP_BXOR = 16;
    
    private int op;
    private Expression left, right;
    
    /** Create a new binary expression given the operation and the
     * left and right child nodes.  Requires that op is a valid
     * operator code and that left and right are non-null. */
    public ExprBinary(FEContext context,
                      int op, Expression left, Expression right)
    {
        super(context);
        this.op = op;
        this.left = left;
        this.right = right;
    }

    /** Returns the operator of this. */
    public int getOp() { return op; }   

    /** Returns the left child expression of this. */
    public Expression getLeft() { return left; }

    /** Returns the right child expression of this. */
    public Expression getRight() { return right; }

    /** Accept a front-end visitor. */
    public Object accept(FEVisitor v)
    {
        return v.visitExprBinary(this);
    }
}
