package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public class LaunchStateNode extends StatementNode {

    public Span span;
    public VarDeclarationNode varDeclaration;

    public LaunchStateNode (Span span, Object argument) {
        super(span);
        this.varDeclaration = Util.cast(argument, VarDeclarationNode.class);
    }

    @Override
    public String contents () {
        return varDeclaration.contents();
    }
}
