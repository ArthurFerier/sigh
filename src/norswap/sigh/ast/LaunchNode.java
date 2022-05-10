package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class LaunchNode extends ExpressionNode
{

    public Span span;
    public VarDeclarationNode varDeclaration;
    public FunCallNode funCallNode;

    public LaunchNode (Span span, Object argument) {
        super(span);
        this.span = span;
        if (argument instanceof FunCallNode) {
            this.funCallNode = Util.cast(argument, FunCallNode.class);
        } else if (argument instanceof VarDeclarationNode) {
            this.varDeclaration = Util.cast(argument, VarDeclarationNode.class);
        }
    }



    @Override
    public String contents ()
    {
        if (varDeclaration == null) {
            return funCallNode.contents();
        }
        return varDeclaration.contents();
    }
}
