  
package com.carrotsearch.hppc;

import java.util.*;

import com.carrotsearch.hppc.cursors.*;
import com.carrotsearch.hppc.hash.MurmurHash3;
import com.carrotsearch.hppc.predicates.*;
import com.carrotsearch.hppc.procedures.*;

import static com.carrotsearch.hppc.Internals.*;
import static com.carrotsearch.hppc.HashContainerUtils.*;

/**
 * An identity hash map of <code>KType</code> to <code>char</code>, implemented using open
 * addressing with linear probing for collision resolution.
 * 
 * <p>
 * This class implements the {@link ObjectCharMap} interface using
 * reference-equality in place of object-equality when comparing keys (and values!). 
 * It is an equivalent of {@link IdentityHashMap}, but with primitive arrays for 
 * value storage.
 * </p>
 * 
 * <p>
 * The internal buffers of this implementation ({@link #keys}, {@link #values},
 * {@link #allocated}) are always allocated to the nearest size that is a power of two. When
 * the capacity exceeds the given load factor, the buffer size is doubled.
 * </p>
 * 
 * <p>This implementation supports <code>null</code> keys.</p>
 * 
 * <p><b>Important node.</b> The implementation uses power-of-two tables and linear
 * probing, which may cause poor performance (many collisions) if hash values are
 * not properly distributed. This implementation uses rehashing 
 * using {@link MurmurHash3}.</p>
 * 
 * @author This code is inspired by the collaboration and implementation in the <a
 *         href="http://fastutil.dsi.unimi.it/">fastutil</a> project.
 */
 @javax.annotation.Generated(date = "2014-09-08T10:42:30+0200", value = "HPPC generated from: ObjectCharOpenIdentityHashMap.java") 
public class ObjectCharOpenIdentityHashMap<KType>
    implements ObjectCharMap<KType>, Cloneable
{
    /**
     * Minimum capacity for the map.
     */
    public final static int MIN_CAPACITY = HashContainerUtils.MIN_CAPACITY;

    /**
     * Default capacity.
     */
    public final static int DEFAULT_CAPACITY = HashContainerUtils.DEFAULT_CAPACITY;

    /**
     * Default load factor.
     */
    public final static float DEFAULT_LOAD_FACTOR = HashContainerUtils.DEFAULT_LOAD_FACTOR;

    /**
     * Hash-indexed array holding all keys.
     *
     * <p><strong>Important!</strong> 
     * The actual value in this field is always an instance of <code>Object[]</code>.
     * Be warned that <code>javac</code> emits additional casts when <code>keys</code> 
     * are directly accessed; <strong>these casts
     * may result in exceptions at runtime</strong>. A workaround is to cast directly to
     * <code>Object[]</code> before accessing the buffer's elements (although it is highly
     * recommended to use a {@link #iterator()} instead.
     * </pre>
     * 
     * @see #values
     */
    public KType [] keys;

    /**
     * Hash-indexed array holding all values associated to the keys
     * stored in {@link #keys}.
     * 
     * <p><strong>Important!</strong> 
     * The actual value in this field is always an instance of <code>Object[]</code>.
     * Be warned that <code>javac</code> emits additional casts when <code>values</code> 
     * are directly accessed; <strong>these casts
     * may result in exceptions at runtime</strong>. A workaround is to cast directly to
     * <code>Object[]</code> before accessing the buffer's elements (although it is highly
     * recommended to use a {@link #iterator()} instead.
     * </pre>
     * 
     * @see #keys
     */
    public char [] values;

    /**
     * Information if an entry (slot) in the {@link #values} table is allocated
     * or empty.
     * 
     * @see #assigned
     */
    public boolean [] allocated;

    /**
     * Cached number of assigned slots in {@link #allocated}.
     */
    public int assigned;

    /**
     * The load factor for this map (fraction of allocated slots
     * before the buffers must be rehashed or reallocated).
     */
    public final float loadFactor;

    /**
     * Resize buffers when {@link #allocated} hits this value. 
     */
    protected int resizeAt;

    /**
     * The most recent slot accessed in {@link #containsKey} (required for
     * {@link #lget}).
     * 
     * @see #containsKey
     * @see #lget
     */
    protected int lastSlot;
    
    /**
     * We perturb hashed values with the array size to avoid problems with
     * nearly-sorted-by-hash values on iterations.
     * 
     * @see "http://issues.carrot2.org/browse/HPPC-80"
     */
    protected int perturbation;

    /**
     * Creates a hash map with the default capacity of {@value #DEFAULT_CAPACITY},
     * load factor of {@value #DEFAULT_LOAD_FACTOR}.
     * 
     * <p>See class notes about hash distribution importance.</p>
     */
    public ObjectCharOpenIdentityHashMap()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a hash map with the given initial capacity, default load factor of
     * {@value #DEFAULT_LOAD_FACTOR}.
     * 
     * <p>See class notes about hash distribution importance.</p>
     * 
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     */
    public ObjectCharOpenIdentityHashMap(int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a hash map with the given initial capacity,
     * load factor.
     * 
     * <p>See class notes about hash distribution importance.</p>
     * 
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     *
     * @param loadFactor The load factor (greater than zero and smaller than 1).
     */
    public ObjectCharOpenIdentityHashMap(int initialCapacity, float loadFactor)
    {
        initialCapacity = Math.max(initialCapacity, MIN_CAPACITY);

        assert initialCapacity > 0
            : "Initial capacity must be between (0, " + Integer.MAX_VALUE + "].";
        assert loadFactor > 0 && loadFactor <= 1
            : "Load factor must be between (0, 1].";

        this.loadFactor = loadFactor;
        allocateBuffers(roundCapacity(initialCapacity));
    }
    
    /**
     * Create a hash map from all key-value pairs of another container.
     */
    public ObjectCharOpenIdentityHashMap(ObjectCharAssociativeContainer<KType> container)
    {
        this((int)(container.size() * (1 + DEFAULT_LOAD_FACTOR)));
        putAll(container);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char put(KType key, char value)
    {
        assert assigned < allocated.length;

        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask;
        while (allocated[slot])
        {
            if (key == keys[slot])
            {
                final char oldValue = values[slot];
                values[slot] = value;
                return oldValue;
            }

            slot = (slot + 1) & mask;
        }

        // Check if we need to grow. If so, reallocate new data, fill in the last element 
        // and rehash.
        if (assigned == resizeAt) {
            expandAndPut(key, value, slot);
        } else {
            assigned++;
            allocated[slot] = true;
            keys[slot] = key;                
            values[slot] = value;
        }
        return ((char) 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putAll(
        ObjectCharAssociativeContainer<? extends KType> container)
    {
        final int count = this.assigned;
        for (ObjectCharCursor<? extends KType> c : container)
        {
            put(c.key, c.value);
        }
        return this.assigned - count;
    }

    /**
     * Puts all key/value pairs from a given iterable into this map.
     */
    @Override
    public int putAll(
        Iterable<? extends ObjectCharCursor<? extends KType>> iterable)
    {
        final int count = this.assigned;
        for (ObjectCharCursor<? extends KType> c : iterable)
        {
            put(c.key, c.value);
        }
        return this.assigned - count;
    }

    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. An equivalent
     * of the following code:
     * <pre>
     * if (!map.containsKey(key)) map.put(value);
     * </pre>
     * 
     * <p>This method saves to {@link #lastSlot} as a side effect of each call.</p>
     * 
     * @param key The key of the value to check.
     * @param value The value to put if <code>key</code> does not exist.
     * @return <code>true</code> if <code>key</code> did not exist and <code>value</code>
     * was placed in the map.
     */
    public boolean putIfAbsent(KType key, char value)
    {
        if (!containsKey(key))
        {
            put(key, value);
            return true;
        }
        return false;
    }

       
    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. A logical 
     * equivalent of the following code (but does not update {@link #lastSlot}):
     * <pre>
     *  if (containsKey(key))
     *  {
     *      char v = (char) (lget() + additionValue);
     *      lset(v);
     *      return v;
     *  }
     *  else
     *  {
     *     put(key, putValue);
     *     return putValue;
     *  }
     * </pre>
     * 
     * @param key The key of the value to adjust.
     * @param putValue The value to put if <code>key</code> does not exist.
     * @param additionValue The value to add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
      
         public char putOrAdd(KType key, char putValue, char additionValue)
    {
        assert assigned < allocated.length;

        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask;
        while (allocated[slot])
        {
            if (key == keys[slot])
            {
                return values[slot] = (char) (values[slot] + additionValue);
            }

            slot = (slot + 1) & mask;
        }

        if (assigned == resizeAt) {
            expandAndPut(key, putValue, slot);
        } else {
            assigned++;
            allocated[slot] = true;
            keys[slot] = key;                
            values[slot] = putValue;
        }
        return putValue;
    }
     

       
    /**
     * An equivalent of calling
     * <pre>
     *  if (containsKey(key))
     *  {
     *      char v = (char) (lget() + additionValue);
     *      lset(v);
     *      return v;
     *  }
     *  else
     *  {
     *     put(key, additionValue);
     *     return additionValue;
     *  }
     * </pre>
     * 
     * @param key The key of the value to adjust.
     * @param additionValue The value to put or add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
      
         public char addTo(KType key, char additionValue)
    {
        return putOrAdd(key, additionValue, additionValue);
    }
     

    /**
     * Expand the internal storage buffers (capacity) and rehash.
     */
    private void expandAndPut(KType pendingKey, char pendingValue, int freeSlot)
    {
        assert assigned == resizeAt;
        assert !allocated[freeSlot];

        // Try to allocate new buffers first. If we OOM, it'll be now without
        // leaving the data structure in an inconsistent state.
        final KType   [] oldKeys      = this.keys;
        final char   [] oldValues    = this.values;
        final boolean [] oldAllocated = this.allocated;

        allocateBuffers(nextCapacity(keys.length));

        // We have succeeded at allocating new data so insert the pending key/value at
        // the free slot in the old arrays before rehashing.
        lastSlot = -1;
        assigned++;
        oldAllocated[freeSlot] = true;
        oldKeys[freeSlot] = pendingKey;
        oldValues[freeSlot] = pendingValue;
        
        // Rehash all stored keys into the new buffers.
        final KType []   keys = this.keys;
        final char []   values = this.values;
        final boolean [] allocated = this.allocated;
        final int mask = allocated.length - 1;
        for (int i = oldAllocated.length; --i >= 0;)
        {
            if (oldAllocated[i])
            {
                final KType k = oldKeys[i];
                final char v = oldValues[i];

                int slot = rehash(System.identityHashCode(k), perturbation) & mask;
                while (allocated[slot])
                {
                    slot = (slot + 1) & mask;
                }

                allocated[slot] = true;
                keys[slot] = k;                
                values[slot] = v;
            }
        }

        /*  */ Arrays.fill(oldKeys,   null); /*  */
        /*  */
    }

    /**
     * Allocate internal buffers for a given capacity.
     * 
     * @param capacity New capacity (must be a power of two).
     */
    private void allocateBuffers(int capacity)
    {
        KType [] keys = Internals.<KType[]>newArray(capacity);
        char [] values = new char [capacity];
        boolean [] allocated = new boolean [capacity];

        this.keys = keys;
        this.values = values;
        this.allocated = allocated;

        this.resizeAt = Math.max(2, (int) Math.ceil(capacity * loadFactor)) - 1;
        this.perturbation = computePerturbationValue(capacity);
    }

    /**
     * <p>Compute the key perturbation value applied before hashing. The returned value
     * should be non-zero and ideally different for each capacity. This matters because
     * keys are nearly-ordered by their hashed values so when adding one container's
     * values to the other, the number of collisions can skyrocket into the worst case
     * possible.
     * 
     * <p>If it is known that hash containers will not be added to each other 
     * (will be used for counting only, for example) then some speed can be gained by 
     * not perturbing keys before hashing and returning a value of zero for all possible
     * capacities. The speed gain is a result of faster rehash operation (keys are mostly
     * in order).   
     */
    protected int computePerturbationValue(int capacity)
    {
        return PERTURBATIONS[Integer.numberOfLeadingZeros(capacity)];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char remove(KType key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask; 
        while (allocated[slot])
        {
            if (key == keys[slot])
             {
                assigned--;
                char v = values[slot];
                shiftConflictingKeys(slot);
                return v;
             }
             slot = (slot + 1) & mask;
        }

        return ((char) 0);
    }

    /**
     * Shift all the slot-conflicting keys allocated to (and including) <code>slot</code>. 
     */
    protected void shiftConflictingKeys(int slotCurr)
    {
        // Copied nearly verbatim from fastutil's impl.
        final int mask = allocated.length - 1;
        int slotPrev, slotOther;
        while (true)
        {
            slotCurr = ((slotPrev = slotCurr) + 1) & mask;

            while (allocated[slotCurr])
            {
                slotOther = rehash(System.identityHashCode(keys[slotCurr]), perturbation) & mask;
                if (slotPrev <= slotCurr)
                {
                    // We are on the right of the original slot.
                    if (slotPrev >= slotOther || slotOther > slotCurr)
                        break;
                }
                else
                {
                    // We have wrapped around.
                    if (slotPrev >= slotOther && slotOther > slotCurr)
                        break;
                }
                slotCurr = (slotCurr + 1) & mask;
            }

            if (!allocated[slotCurr]) 
                break;

            // Shift key/value pair.
            keys[slotPrev] = keys[slotCurr];           
            values[slotPrev] = values[slotCurr];           
        }

        allocated[slotPrev] = false;
        
        /*  */ 
        keys[slotPrev] = null; 
        /*  */
        /*  */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int removeAll(ObjectContainer<? extends KType> container)
    {
        final int before = this.assigned;

        for (ObjectCursor<? extends KType> cursor : container)
        {
            remove(cursor.value);
        }

        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int removeAll(ObjectPredicate<? super KType> predicate)
    {
        final int before = this.assigned;

        final KType [] keys = this.keys;
        final boolean [] states = this.allocated;

        for (int i = 0; i < states.length;)
        {
            if (states[i])
            {
                if (predicate.apply(keys[i]))
                {
                    assigned--;
                    shiftConflictingKeys(i);
                    // Repeat the check for the same i.
                    continue;
                }
            }
            i++;
        }
        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Use the following snippet of code to check for key existence
     * first and then retrieve the value if it exists.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget(); 
     * </pre>
     * <p>The above code <strong>cannot</strong> be used by multiple concurrent
     * threads because a call to {@link #containsKey(Object)} stores
     * the temporary slot number in {@link #lastSlot}. An alternative to the above
     * conditional statement is to use {@link #getOrDefault} and
     * provide a custom default value sentinel (not present in the value set).</p>
     */
    @Override
    public char get(KType key)
    {
        // Same as:
        // getOrDefault(key, ((char) 0))
        // but let's keep it duplicated for VMs that don't have advanced inlining.
        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask;
        while (allocated[slot])
        {
            if (key == keys[slot])
            {
                return values[slot]; 
            }
            
            slot = (slot + 1) & mask;
        }
        return ((char) 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char getOrDefault(KType key, char defaultValue)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask;
        while (allocated[slot])
        {
            if (key == keys[slot])
            {
                return values[slot]; 
            }
            
            slot = (slot + 1) & mask;
        }
        return defaultValue;
    }

    /*  */
    /**
     * Returns the last key stored in this has map for the corresponding
     * most recent call to {@link #containsKey}.
     * 
     * <p>Use the following snippet of code to check for key existence
     * first and then retrieve the key value if it exists.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lkey(); 
     * </pre>
     * 
     * <p>This is equivalent to calling:</p>
     * <pre>
     * if (map.containsKey(key))
     *   key = map.keys[map.lslot()];
     * </pre>
     */
    public KType lkey()
    {
        return keys[lslot()];
    }
    /*  */

    /**
     * Returns the last value saved in a call to {@link #containsKey}.
     * 
     * @see #containsKey
     */
    public char lget()
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";
    
        return values[lastSlot];
    }

    /**
     * Sets the value corresponding to the key saved in the last
     * call to {@link #containsKey}, if and only if the key exists
     * in the map already.
     * 
     * @see #containsKey
     * @return Returns the previous value stored under the given key.
     */
    public char lset(char key)
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        final char previous = values[lastSlot];
        values[lastSlot] = key;
        return previous;
    }

    /**
     * @return Returns the slot of the last key looked up in a call to {@link #containsKey} if
     * it returned <code>true</code>.
     * 
     * @see #containsKey
     */
    public int lslot()
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        return lastSlot;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Saves the associated value for fast access using {@link #lget}
     * or {@link #lset}.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget();
     * </pre>
     * or, to modify the value at the given key without looking up
     * its slot twice:
     * <pre>
     * if (map.containsKey(key))
     *   map.lset(map.lget() + 1);
     * </pre>
     *  or, to retrieve the key-equivalent object from the map:
     * <pre>
     * if (map.containsKey(key))
     *   map.lkey();
     * </pre>     * 
     * <p><strong>Important:</strong> {@link #containsKey} and consecutive {@link #lget}, {@link #lset}
     * or {@link #lkey} must not be used by concurrent threads because {@link #lastSlot} is 
     * used to store state.</p>
     */
    @Override
    public boolean containsKey(KType key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(System.identityHashCode(key), perturbation) & mask;
        while (allocated[slot])
        {
            if (key == keys[slot])
            {
                lastSlot = slot;
                return true; 
            }
            slot = (slot + 1) & mask;
        }
        lastSlot = -1;
        return false;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Does not release internal buffers.</p>
     */
    @Override
    public void clear()
    {
        assigned = 0;

        // States are always cleared.
        Arrays.fill(allocated, false);

        /*  */
        Arrays.fill(keys, null); // Help the GC.
        /*  */

        /*  */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return assigned;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Note that an empty container may still contain many deleted keys (that occupy buffer
     * space). Adding even a single element to such a container may cause rehashing.</p>
     */
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    public int hashCode()
    {
        int h = 0;
        for (ObjectCharCursor<KType> c : this)
        {
                         h += rehash(System.identityHashCode(c.key)) + 
                 rehash(c.value);
             
            /*  */
        }
        return h;
    }

    /**
     * {@inheritDoc} 
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj != null)
        {
            if (obj == this) return true;

            if (obj instanceof ObjectCharMap)
            {
                /*  */
                @SuppressWarnings("unchecked")
                /*  */
                ObjectCharMap<KType> other = (ObjectCharMap<KType>) obj;
                if (other.size() == this.size())
                {
                    for (ObjectCharCursor<KType> c : other)
                    {
                        if (this.containsKey(c.key))
                        {
                            char v = this.get(c.key);
                                                         if (((c.value) == (v)))
                            {
                                continue;
                            }
                             
                            /*  */
                        }
                        return false;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An iterator implementation for {@link #iterator}.
     */
    private final class EntryIterator extends AbstractIterator<ObjectCharCursor<KType>>
    {
        private final ObjectCharCursor<KType> cursor;

        public EntryIterator()
        {
            cursor = new ObjectCharCursor<KType>();
            cursor.index = -1;
        }

        @Override
        protected ObjectCharCursor<KType> fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }
            
            if (i == max)
                return done();

            cursor.index = i;
            cursor.key = keys[i];
            cursor.value = values[i];

            return cursor;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ObjectCharCursor<KType>> iterator()
    {
        return new EntryIterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends ObjectCharProcedure<? super KType>> T forEach(T procedure)
    {
        final KType [] keys = this.keys;
        final char [] values = this.values;
        final boolean [] states = this.allocated;

        for (int i = 0; i < states.length; i++)
        {
            if (states[i])
                procedure.apply(keys[i], values[i]);
        }
        
        return procedure;
    }

    /**
     * Returns a specialized view of the keys of this associated container. 
     * The view additionally implements {@link ObjectLookupContainer}.
     */
    public KeysContainer keys()
    {
        return new KeysContainer();
    }

    /**
     * A view of the keys inside this hash map. The returned container will
     * still be based on reference-equality! 
     */
    public final class KeysContainer 
        extends AbstractObjectCollection<KType> implements ObjectLookupContainer<KType>
    {
        private final ObjectCharOpenIdentityHashMap<KType> owner = 
            ObjectCharOpenIdentityHashMap.this;
        
        @Override
        public boolean contains(KType e)
        {
            return containsKey(e);
        }
        
        @Override
        public <T extends ObjectProcedure<? super KType>> T forEach(T procedure)
        {
            final KType [] localKeys = owner.keys;
            final boolean [] localStates = owner.allocated;

            for (int i = 0; i < localStates.length; i++)
            {
                if (localStates[i])
                    procedure.apply(localKeys[i]);
            }

            return procedure;
        }

        @Override
        public <T extends ObjectPredicate<? super KType>> T forEach(T predicate)
        {
            final KType [] localKeys = owner.keys;
            final boolean [] localStates = owner.allocated;

            for (int i = 0; i < localStates.length; i++)
            {
                if (localStates[i])
                {
                    if (!predicate.apply(localKeys[i]))
                        break;
                }
            }

            return predicate;
        }

        @Override
        public boolean isEmpty()
        {
            return owner.isEmpty();
        }

        @Override
        public Iterator<ObjectCursor<KType>> iterator()
        {
            return new KeysIterator();
        }

        @Override
        public int size()
        {
            return owner.size();
        }

        @Override
        public void clear()
        {
            owner.clear();
        }

        @Override
        public int removeAll(ObjectPredicate<? super KType> predicate)
        {
            return owner.removeAll(predicate);
        }

        @Override
        public int removeAllOccurrences(final KType e)
        {
            final boolean hasKey = owner.containsKey(e);
            int result = 0;
            if (hasKey)
            {
                owner.remove(e);
                result = 1;
            }
            return result;
        }
    };
    
    /**
     * An iterator over the set of assigned keys.
     */
    private final class KeysIterator extends AbstractIterator<ObjectCursor<KType>>
    {
        private final ObjectCursor<KType> cursor;

        public KeysIterator()
        {
            cursor = new ObjectCursor<KType>();
            cursor.index = -1;
        }

        @Override
        protected ObjectCursor<KType> fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }
            
            if (i == max)
                return done();

            cursor.index = i;
            cursor.value = keys[i];

            return cursor;
        }
    }

    /**
     * @return Returns a container with all values stored in this map.
     */
    @Override
    public CharContainer values()                                                                                                    
    {                                                                                                                                         
        return new ValuesContainer();                                                                                                         
    }                                                                                                                                         

    /**                                                                                                                                       
     * A view over the set of values of this map.                                                                                                         
     */                                                                                                                                       
    private final class ValuesContainer extends AbstractCharCollection                                                               
    {                                                                                                                                         
        @Override                                                                                                                             
        public int size()                                                                                                                     
        {                                                                                                                                     
            return ObjectCharOpenIdentityHashMap.this.size();                                                                                       
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public boolean isEmpty()                                                                                                              
        {                                                                                                                                     
            return ObjectCharOpenIdentityHashMap.this.isEmpty();                                                                                    
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public boolean contains(char value)                                                                                                  
        {                                                                                                                                     
            // This is a linear scan over the values, but it's in the contract, so be it.                                                     
            final boolean [] allocated = ObjectCharOpenIdentityHashMap.this.allocated;                                                              
            final char [] values = ObjectCharOpenIdentityHashMap.this.values;                                                                      
                                                                                                                                              
            for (int slot = 0; slot < allocated.length; slot++)                                                                               
            {                                                                                                                                 
                if (allocated[slot])                                    
                {   
                    char v = values[slot];
                                         if (((value) == (v)))
                    {
                        return true;
                    }
                     
                    /*  */
                }                                                                                                                             
            }
            return false;                                                                                                                     
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public <T extends CharProcedure> T forEach(T procedure)                                                              
        {                                                                                                                                     
            final boolean [] allocated = ObjectCharOpenIdentityHashMap.this.allocated;                                                              
            final char [] values = ObjectCharOpenIdentityHashMap.this.values;                                                                      
                                                                                                                                              
            for (int i = 0; i < allocated.length; i++)                                                                                        
            {                                                                                                                                 
                if (allocated[i])                                                                                                             
                    procedure.apply(values[i]);                                                                                               
            }                                                                                                                                 
                                                                                                                                              
            return procedure;                                                                                                                 
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public <T extends CharPredicate> T forEach(T predicate)                                                              
        {                                                                                                                                     
            final boolean [] allocated = ObjectCharOpenIdentityHashMap.this.allocated;                                                              
            final char [] values = ObjectCharOpenIdentityHashMap.this.values;                                                                      
                                                                                                                                              
            for (int i = 0; i < allocated.length; i++)                                                                                        
            {                                                                                                                                 
                if (allocated[i])                                                                                                             
                {                                                                                                                             
                    if (!predicate.apply(values[i]))                                                                                          
                        break;                                                                                                                
                }                                                                                                                             
            }                                                                                                                                 
                                                                                                                                              
            return predicate;                                                                                                                 
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public Iterator<CharCursor> iterator()                                                                                       
        {                                                                                                                                     
            return new ValuesIterator();                                                                                                      
        }                                                                                                                                     

        @Override                                                                                                                             
        public int removeAllOccurrences(char e)                                                                                              
        {                                                                                                                                     
            throw new UnsupportedOperationException();                                                                                        
        }                                                                                                                                     
                                                                                                                                              
        @Override                                                                                                                             
        public int removeAll(CharPredicate predicate)                                                                        
        {                                                                                                                                     
            throw new UnsupportedOperationException();                                                                                        
        }                                                                                                                                     

        @Override                                                                                                                             
        public void clear()                                                                                                                   
        {                                                                                                                                     
            throw new UnsupportedOperationException();                                                                                        
        }                                                                                                                                     
    }                                                                                                                                         
                                                                                                                                              
    /**                                                                                                                                       
     * An iterator over the set of assigned values.                                                                                           
     */                                                                                                                                       
    private final class ValuesIterator extends AbstractIterator<CharCursor>                                                               
    {                                                                                                                                         
        private final CharCursor cursor;                                                                                             
                                                                                                                                              
        public ValuesIterator()                                                                                                               
        {                                                                                                                                     
            cursor = new CharCursor();                                                                                               
            cursor.index = -1;                                                                                                        
        }                                                                                                                                     
        
        @Override
        protected CharCursor fetch()
        {
            int i = cursor.index + 1;
            final int max = keys.length;
            while (i < max && !allocated[i])
            {
                i++;
            }
            
            if (i == max)
                return done();

            cursor.index = i;
            cursor.value = values[i];

            return cursor;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ObjectCharOpenIdentityHashMap<KType> clone()
    {
        try
        {
            /*  */
            @SuppressWarnings("unchecked")
            /*  */
            ObjectCharOpenIdentityHashMap<KType> cloned = 
                (ObjectCharOpenIdentityHashMap<KType>) super.clone();
            
            cloned.keys = keys.clone();
            cloned.values = values.clone();
            cloned.allocated = allocated.clone();

            return cloned;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Convert the contents of this map to a human-friendly string. 
     */
    @Override
    public String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (ObjectCharCursor<KType> cursor : this)
        {
            if (!first) buffer.append(", ");
            buffer.append(cursor.key);
            buffer.append("=>");
            buffer.append(cursor.value);
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * Creates a hash map from two index-aligned arrays of key-value pairs. 
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> from(KType [] keys, char [] values)
    {
        if (keys.length != values.length) 
            throw new IllegalArgumentException("Arrays of keys and values must have an identical length."); 

        ObjectCharOpenIdentityHashMap<KType> map = new ObjectCharOpenIdentityHashMap<KType>();
        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], values[i]);
        }
        return map;
    }

    /**
     * Create the map from another associative container.
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> from(ObjectCharAssociativeContainer<KType> container)
    {
        return new ObjectCharOpenIdentityHashMap<KType>(container);
    }
    
    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut). 
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> newInstance()
    {
        return new ObjectCharOpenIdentityHashMap<KType>();
    }

    /**
     * Returns a new object with no key perturbations (see
     * {@link #computePerturbationValue(int)}). Only use when sure the container will not
     * be used for direct copying of keys to another hash container.
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> newInstanceWithoutPerturbations()
    {
        return new ObjectCharOpenIdentityHashMap<KType>() {
            @Override
            protected int computePerturbationValue(int capacity) { return 0; }
        };
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut). 
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> newInstance(int initialCapacity, float loadFactor)
    {
        return new ObjectCharOpenIdentityHashMap<KType>(initialCapacity, loadFactor);
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut). The returned instance will have enough initial capacity to hold
     * <code>expectedSize</code> elements without having to resize.
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> newInstanceWithExpectedSize(int expectedSize)
    {
        return newInstanceWithExpectedSize(expectedSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut). The returned instance will have enough initial capacity to hold
     * <code>expectedSize</code> elements without having to resize.
     */
    public static <KType> ObjectCharOpenIdentityHashMap<KType> newInstanceWithExpectedSize(int expectedSize, float loadFactor)
    {
        return newInstance((int) (expectedSize / loadFactor) + 1, loadFactor);
    }
}
