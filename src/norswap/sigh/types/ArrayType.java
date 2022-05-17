package norswap.sigh.types;

import norswap.sigh.ast.ArrayTypeNode;

public final class ArrayType extends Type
{
    public final Type componentType;
    public final Type primitiveType;

    public ArrayType (Type componentType) {
        this.componentType = componentType;
        this.primitiveType = this.getType();
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

    public Type getType() {
        if (this.componentType instanceof ArrayType)
            return ((ArrayType) this.componentType).getType();
        else {
            return this.componentType;
        }
    }
}
