package com.carrotsearch.hppc.cursors;


/**
 * A cursor over entries of an associative container (float keys and double values).
 */
 @javax.annotation.Generated(date = "2014-09-08T10:42:29+0200", value = "HPPC generated from: FloatDoubleCursor.java") 
public final class FloatDoubleCursor
{
    /**
     * The current key and value's index in the container this cursor belongs to. The meaning of
     * this index is defined by the container (usually it will be an index in the underlying
     * storage buffer).
     */
    public int index;

    /**
     * The current key.
     */
    public float key;

    /**
     * The current value.
     */
    public double value;
    
    @Override
    public String toString()
    {
        return "[cursor, index: " + index + ", key: " + key + ", value: " + value + "]";
    }
}
