package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class LaunchNode extends ExpressionNode
{

    public Span span;
    public VarDeclarationNode varDeclaration;
    public FunCallNode funCall;

    public LaunchNode (Span span, Object argument) {
        super(span);
        this.span = span;
        if (argument instanceof FunCallNode) {
            this.funCall = Util.cast(argument, FunCallNode.class);
        } else if (argument instanceof VarDeclarationNode) {
            this.varDeclaration = Util.cast(argument, VarDeclarationNode.class);
        }
    }



    @Override
    public String contents ()
    {
        if (varDeclaration == null) {
            return funCall.contents();
        }
        return varDeclaration.contents();
    }
}
