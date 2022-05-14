package norswap.sigh.types;

import norswap.sigh.ast.ArrayTypeNode;

public final class ArrayType extends Type
{
    public final Type componentType;
    public final int nDim; // TODO

    public ArrayType (Type componentType) {
        this.componentType = componentType;
        this.nDim = getDim();
    }

    @Override public String name() {
        return componentType.toString() + "[]";
    }

    @Override public boolean equals (Object o) {
        return this == o || o instanceof ArrayType && componentType.equals(o);
    }

    @Override public int hashCode () {
        return componentType.hashCode();
    }

    public int getDim() {
        if (this.componentType instanceof ArrayType)
            return 1 + ((ArrayType) this.componentType).getDim();
        else {
            return 1;
        }
    }
}
