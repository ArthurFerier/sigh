package norswap.sigh.types;

public final class ArrayType extends Type
{
    public final Type componentType;
    public final int depth; // TODO

    public ArrayType (Type componentType) {
        this.componentType = componentType;
        this.depth = 1;
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
}
