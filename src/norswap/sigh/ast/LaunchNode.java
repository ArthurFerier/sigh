package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class LaunchNode extends ExpressionNode
{

    Span span;
    public final FunCallNode funCall;

    public LaunchNode (Span span, Object argument) {
        super(span);
        this.span = span;
        this.funCall = Util.cast(argument, FunCallNode.class);
    }

    @Override
    public String contents ()
    {
        return funCall.contents();
    }
}
